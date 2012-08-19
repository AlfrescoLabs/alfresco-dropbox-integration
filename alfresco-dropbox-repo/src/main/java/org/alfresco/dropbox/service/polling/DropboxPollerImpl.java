/*
 * Copyright 2011-2012 Alfresco Software Limited.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 * 
 * This file is part of an unsupported extension to Alfresco.
 */

package org.alfresco.dropbox.service.polling;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.exceptions.NotModifiedException;
import org.alfresco.dropbox.service.DropboxService;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.search.impl.lucene.LuceneQueryParserException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.social.dropbox.api.Metadata;


/**
 * 
 * 
 * @author Jared Ottley
 */
public class DropboxPollerImpl
    implements DropboxPoller
{
    private static final Log     log                          = LogFactory.getLog(DropboxPollerImpl.class);

    private SearchService        searchService;
    private NodeService          nodeService;
    private FileFolderService    fileFolderService;
    private TransactionService   transactionService;
    private DropboxService       dropboxService;

    private static final String  CMIS_DROPBOX_SITES_QUERY     = "SELECT * FROM st:site AS S JOIN db:syncable AS O ON S.cmis:objectId = O.cmis:objectId";
    private static final String  CMIS_DROPBOX_DOCUMENTS_QUERY = "SELECT D.* FROM cmis:document AS D JOIN db:dropbox AS O ON D.cmis:objectId = O.cmis:objectId";
    private static final String  CMIS_DROPBOX_FOLDERS_QUERY   = "SELECT F.* FROM cmis:folder AS F JOIN db:dropbox AS O ON F.cmis:objectId = O.cmis:objectId";

    private static final NodeRef MISSING_NODE                 = new NodeRef("missing://missing/missing");


    public void setSearchService(SearchService searchService)
    {
        this.searchService = searchService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }


    public void setTransactionService(TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }


    public void setDropboxService(DropboxService dropboxService)
    {
        this.dropboxService = dropboxService;
    }


    public void execute()
    {
        log.debug("Dropbox poller initiated.");

        // TODO where should the authentication and transactions go?
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
        {

            public Object doWork()
                throws Exception
            {
                RetryingTransactionCallback<Object> txnWork = new RetryingTransactionCallback<Object>()
                {
                    public Object execute()
                        throws Exception
                    {
                        List<NodeRef> sites = getSites();

                        List<NodeRef> folders = null;
                        List<NodeRef> documents = null;

                        if (sites != null)
                        {
                            for (NodeRef site : sites)
                            {
                                if (!isSyncing(site))
                                {
                                    log.debug("Processing Content in " + nodeService.getProperty(site, ContentModel.PROP_NAME));
                                    try
                                    {
                                        syncOn(site);

                                        folders = getFolders(site);
                                        documents = getDocuments(site);


                                        if (documents != null)
                                        {
                                            // If the document is the child of a synced folder...we want to work on the folder as a
                                            // full collection and not the document as an independent element
                                            Iterator<NodeRef> i = documents.iterator();

                                            while (i.hasNext())
                                            {
                                                NodeRef document = i.next();
                                                if (folders.contains(nodeService.getPrimaryParent(document).getParentRef()))
                                                {
                                                    i.remove();
                                                }
                                            }
                                            if (documents.size() > 0)
                                            {
                                                for (NodeRef document : documents)
                                                {
                                                    updateNode(document);
                                                }
                                            }
                                        }

                                        if (folders.size() > 0)
                                        {
                                            for (NodeRef folder : folders)
                                            {
                                                log.debug("Looking for updates/new content in "
                                                          + nodeService.getProperty(folder, ContentModel.PROP_NAME));

                                                try
                                                {
                                                    Metadata metadata = dropboxService.getMetadata(folder);

                                                    // Get the list of the content returned.
                                                    List<Metadata> list = metadata.getContents();

                                                    for (Metadata child : list)
                                                    {
                                                        String name = child.getPath().replaceAll(Matcher.quoteReplacement(metadata.getPath()
                                                                                                                          + "/"), "");

                                                        NodeRef childNodeRef = fileFolderService.searchSimple(folder, name);

                                                        if (childNodeRef == null)
                                                        {
                                                            addNode(folder, child, name);
                                                        }
                                                        else
                                                        {
                                                            updateNode(childNodeRef, child);
                                                        }
                                                    }

                                                    metadata = dropboxService.getMetadata(folder);

                                                    dropboxService.persistMetadata(metadata, folder);
                                                }
                                                catch (NotModifiedException nme)
                                                {
                                                    // TODO
                                                }
                                            }
                                        }
                                    }
                                    finally
                                    {
                                        syncOff(site);
                                        log.debug("End processing " + nodeService.getProperty(site, ContentModel.PROP_NAME));

                                        documents = null;
                                        folders = null;
                                    }
                                }

                            }
                        }

                        return null;
                    }
                };

                transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);

                return null;
            }
        }, AuthenticationUtil.getAdminUserName());

    }


    private List<NodeRef> getSites()
    {
        List<NodeRef> sites = new ArrayList<NodeRef>();

        ResultSet resultSet = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<ResultSet>()
        {

            public ResultSet doWork()
                throws Exception
            {
                ResultSet resultSet = null;
                try
                {
                    resultSet = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_CMIS_ALFRESCO, CMIS_DROPBOX_SITES_QUERY);

                }
                catch (LuceneQueryParserException lqpe)
                {
                    // This is primarily to handle the case where the dropbox model has not been added to solr yet. Incidentally it
                    // catches other failures too ;)
                    log.info("Unable to perform site query: " + lqpe.getMessage());
                }

                return resultSet;
            }

        }, AuthenticationUtil.getAdminUserName());

        // TODO Hopefully one day this will go away --Open Bug??

        try
        {
            if (resultSet.length() > 0)
            {
                if (!resultSet.getNodeRef(0).equals(MISSING_NODE))
                {
                    sites = resultSet.getNodeRefs();
                    log.debug("Sites with Dropbox content: " + sites);
                }
            }
        }
        finally
        {
            resultSet.close();
        }

        return sites;
    }


    private List<NodeRef> getDocuments(final NodeRef nodeRef)
    {
        List<NodeRef> documents = Collections.synchronizedList(new ArrayList<NodeRef>());


        ResultSet resultSet = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<ResultSet>()
        {

            public ResultSet doWork()
                throws Exception
            {
                ResultSet resultSet = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_CMIS_ALFRESCO, CMIS_DROPBOX_DOCUMENTS_QUERY
                                                                                                                                          + " WHERE IN_TREE(D, '"
                                                                                                                                          + nodeRef
                                                                                                                                          + "')");

                return resultSet;
            }

        }, AuthenticationUtil.getAdminUserName());

        try
        {
            // TODO Hopefully one day this will go away --Open Bug??
            if (resultSet.length() > 0)
            {
                if (!resultSet.getNodeRef(0).equals(MISSING_NODE))
                {
                    documents = resultSet.getNodeRefs();
                    log.debug("Documents synced to Dropbox: " + documents);
                }
            }
        }
        finally
        {
            resultSet.close();
        }

        return documents;
    }


    private List<NodeRef> getFolders(final NodeRef nodeRef)
    {
        List<NodeRef> folders = Collections.synchronizedList(new ArrayList<NodeRef>());

        ResultSet resultSet = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<ResultSet>()
        {

            public ResultSet doWork()
                throws Exception
            {

                ResultSet resultSet = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_CMIS_ALFRESCO, CMIS_DROPBOX_FOLDERS_QUERY
                                                                                                                                          + " WHERE IN_TREE(F, '"
                                                                                                                                          + nodeRef
                                                                                                                                          + "')");

                return resultSet;
            }

        }, AuthenticationUtil.getAdminUserName());

        try
        {
            // TODO Hopefully one day this will go away --Open Bug??
            if (resultSet.length() > 0)
            {
                if (!resultSet.getNodeRef(0).equals(MISSING_NODE))
                {
                    folders = resultSet.getNodeRefs();
                    log.debug("Folders synced to Dropbox: " + folders);
                }
            }
        }
        finally
        {
            resultSet.close();
        }

        return folders;
    }


    private void updateNode(final NodeRef nodeRef)
    {
        Metadata metadata = dropboxService.getMetadata(nodeRef);

        updateNode(nodeRef, metadata);
    }


    private void updateNode(final NodeRef nodeRef, final Metadata metadata)
    {
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
        {
            public Object doWork()
                throws Exception
            {

                RetryingTransactionCallback<Object> txnWork = new RetryingTransactionCallback<Object>()
                {
                    public Object execute()
                        throws Exception
                    {

                        if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
                        {
                            try
                            {
                                Metadata metadata = dropboxService.getMetadata(nodeRef);

                                // Get the list of the content returned.
                                List<Metadata> list = metadata.getContents();

                                for (Metadata child : list)
                                {
                                    String name = child.getPath().replaceAll(Matcher.quoteReplacement(metadata.getPath() + "/"), "");

                                    NodeRef childNodeRef = fileFolderService.searchSimple(nodeRef, name);

                                    if (childNodeRef == null)
                                    {
                                        addNode(nodeRef, child, name);
                                    }
                                    else
                                    {
                                        updateNode(childNodeRef, child);
                                    }
                                }

                                metadata = dropboxService.getMetadata(nodeRef);

                                dropboxService.persistMetadata(metadata, nodeRef);
                            }
                            catch (NotModifiedException nme)
                            {

                            }

                        }
                        else
                        {
                            Serializable rev = nodeService.getProperty(nodeRef, DropboxConstants.Model.PROP_REV);

                            if (!metadata.getRev().equals(rev))
                            {
                                Metadata metadata = null;
                                try
                                {
                                    metadata = dropboxService.getFile(nodeRef);
                                }
                                catch (ContentIOException cio)
                                {
                                    cio.printStackTrace();
                                }

                                if (metadata != null)
                                {
                                    dropboxService.persistMetadata(metadata, nodeRef);
                                }
                                else
                                {
                                    throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Dropbox metadata maybe out of sync for "
                                                                                            + nodeRef);
                                }
                            }


                        }
                        return null;
                    }
                };

                transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);

                return null;

            }
        }, AuthenticationUtil.getAdminUserName());
    }


    private void addNode(final NodeRef parentNodeRef, final Metadata metadata, final String name)
    {
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
        {

            public Object doWork()
                throws Exception
            {
                NodeRef nodeRef = null;

                if (metadata.isDir())
                {
                    RetryingTransactionCallback<NodeRef> txnWork = new RetryingTransactionCallback<NodeRef>()
                    {
                        public NodeRef execute()
                            throws Exception
                        {
                            NodeRef nodeRef = null;
                            nodeRef = fileFolderService.create(parentNodeRef, name, ContentModel.TYPE_FOLDER).getNodeRef();

                            Metadata metadata = dropboxService.getMetadata(nodeRef);

                            List<Metadata> list = metadata.getContents();

                            for (Metadata child : list)
                            {
                                String name = child.getPath().replaceAll(Matcher.quoteReplacement(metadata.getPath() + "/"), "");

                                addNode(nodeRef, child, name);
                            }

                            return nodeRef;
                        }
                    };

                    nodeRef = transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);
                }
                else
                {
                    log.debug("Adding " + metadata.getPath() + " to Alfresco");

                    RetryingTransactionCallback<NodeRef> txnWork = new RetryingTransactionCallback<NodeRef>()
                    {
                        public NodeRef execute()
                            throws Exception
                        {
                            NodeRef nodeRef = null;

                            try
                            {
                                nodeRef = fileFolderService.create(parentNodeRef, name, ContentModel.TYPE_CONTENT).getNodeRef();
                                Metadata metadata = dropboxService.getFile(nodeRef);

                                dropboxService.persistMetadata(metadata, parentNodeRef);
                            }
                            catch (ContentIOException cio)
                            {
                                cio.printStackTrace();
                            }

                            return nodeRef;
                        }
                    };

                    nodeRef = transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);
                }
                dropboxService.persistMetadata(metadata, nodeRef);
                return null;

            }
        }, AuthenticationUtil.getAdminUserName());
    }


    private boolean isSyncing(final NodeRef nodeRef)
    {
        Boolean syncing = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Boolean>()
        {
            public Boolean doWork()
                throws Exception
            {
                RetryingTransactionCallback<Boolean> txnWork = new RetryingTransactionCallback<Boolean>()
                {
                    public Boolean execute()
                        throws Exception
                    {
                        boolean syncing = false;

                        if (nodeRef != null)
                        {
                            List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_SYNC_DETAILS, DropboxConstants.Model.DROPBOX);

                            if (childAssoc.size() == 1)
                            {
                                syncing = new Boolean(nodeService.getProperty(childAssoc.get(0).getChildRef(), DropboxConstants.Model.PROP_SYNCING).toString());
                            }
                        }

                        return syncing;
                    }
                };

                boolean syncing = transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);

                return syncing;
            }
        }, AuthenticationUtil.getAdminUserName());

        return syncing;
    }


    private void syncOn(NodeRef site)
    {
        sync(site, true);
    }


    private void syncOff(NodeRef site)
    {
        sync(site, false);
    }


    private void sync(final NodeRef site, final boolean sync)
    {
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
        {
            public Object doWork()
                throws Exception
            {
                RetryingTransactionCallback<Object> txnWork = new RetryingTransactionCallback<Object>()
                {
                    public Object execute()
                        throws Exception
                    {

                        if (nodeService.hasAspect(site, DropboxConstants.Model.ASPECT_SYNCABLE))
                        {
                            List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(site, DropboxConstants.Model.ASSOC_SYNC_DETAILS, DropboxConstants.Model.DROPBOX);

                            if (childAssoc.size() == 1)
                            {
                                nodeService.setProperty(childAssoc.get(0).getChildRef(), DropboxConstants.Model.PROP_SYNCING, sync);
                            }
                        }

                        return null;
                    }
                };

                transactionService.getRetryingTransactionHelper().doInTransaction(txnWork, false);

                return null;
            }
        }, AuthenticationUtil.getAdminUserName());
    }


}
