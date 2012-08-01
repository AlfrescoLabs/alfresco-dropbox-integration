/*
 * Copyright 2011-2012 Alfresco Software Limited.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This file is part of an unsupported extension to Alfresco.
 * 
 */

package org.alfresco.dropbox.webscripts;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.alfresco.dropbox.DropboxConstants;
import org.springframework.social.dropbox.api.Metadata;
import org.springframework.web.client.HttpClientErrorException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.service.cmr.model.FileExistsException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionType;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;


/**
 * @author Jared Ottley
 */
public class GetNode
    extends Node
{
    private static Log      logger = LogFactory.getLog(GetNode.class);

    private BehaviourFilter behaviourFilter;
    private VersionService  versionService;


    public void setBehaviourFilter(BehaviourFilter behaviourFilter)
    {
        this.behaviourFilter = behaviourFilter;
    }


    public void setVersionService(VersionService versionService)
    {
        this.versionService = versionService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        // our return map
        Map<String, Object> model = new HashMap<String, Object>();

        // Get the list of nodeRefs passed from Share
        List<NodeRef> nodeRefs = parseNodeRefs(req);

        // We don't want to cache the returned response
        cache.setNeverCache(true);

        // Turn off Dropbox policies for this node.
        behaviourFilter.disableBehaviour(DropboxConstants.Model.ASPECT_DROPBOX);

        // Loop through the list of nodeRefs passed from Share
        for (NodeRef nodeRef : nodeRefs)
        {
            logger.debug("Retrieving Node: " + nodeRef);

            // Need to match the children in both paths (dropbox and alfresco)
            if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
            {
                updateChildren(nodeRef);
            }
            else if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_CONTENT))
            {
                update(nodeRef);
            }
        }

        return model;
    }


    /*
     * Override parseNodeRefs. When a GET is passing a JSON object, the JSON
     * object is changed to and passed as URL parameters (non-Javadoc)
     * @see
     * org.alfresco.dropbox.webscripts.Node#parseNodeRefs(org.springframework
     * .extensions.webscripts.WebScriptRequest)
     */
    @Override
    protected final List<NodeRef> parseNodeRefs(final WebScriptRequest req)
    {
        final List<NodeRef> result = new ArrayList<NodeRef>();
        String nodeRefs = req.getParameter("nodeRefs");

        try
        {
            if (nodeRefs == null || nodeRefs.length() == 0)
            {
                throw new WebScriptException(Status.STATUS_BAD_REQUEST, "No content sent with request.");
            }

            NodeRef nodeRef = new NodeRef(nodeRefs);
            result.add(nodeRef);
        }
        catch (final WebScriptException wse)
        {
            throw wse; // Ensure WebScriptExceptions get re-thrown verbatim
        }
        catch (final Exception e)
        {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Unable to retrieve nodeRefs from Parameter '" + req.getURL()
                                                                    + "'.", e);
        }

        return result;
    }


    private void update(NodeRef nodeRef)
    {
        Metadata metadata = dropboxService.getMetadata(nodeRef);

        Map<QName, Serializable> persistedMetadata = dropboxService.getPersistedMetadata(nodeRef);

        if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_CONTENT))
        {
            if (!metadata.getRev().equals(persistedMetadata.get(DropboxConstants.Model.PROP_REV).toString()))
            {
                // We need to make the node Versionable if it isn't already
                makeVersionable(nodeRef);

                // TODO Add modified date test?
                metadata = dropboxService.getFile(nodeRef);

                dropboxService.persistMetdata(metadata, nodeRef);

                syncUpdate(nodeRef, false);
            }
        }
    }


    private void updateChildren(NodeRef nodeRef)
    {
        Metadata parentMetadata = dropboxService.getMetadata(nodeRef);

        // Get the list of the content returned.
        List<Metadata> list = parentMetadata.getContents();

        for (Metadata child : list)
        {
            String name = child.getPath().replaceAll(Matcher.quoteReplacement(parentMetadata.getPath() + "/"), "");

            NodeRef childNodeRef = fileFolderService.searchSimple(nodeRef, name);

            if (childNodeRef == null)
            {
                NodeRef newChildNodeRef = null;

                try
                {
                    if (child.isDir())
                    {
                        newChildNodeRef = fileFolderService.create(nodeRef, name, ContentModel.TYPE_FOLDER).getNodeRef();
                        updateChildren(newChildNodeRef);
                        dropboxService.persistMetdata(child, newChildNodeRef);
                    }
                    else
                    {
                        newChildNodeRef = fileFolderService.create(nodeRef, name, ContentModel.TYPE_CONTENT).getNodeRef();
                        Metadata metadata = dropboxService.getFile(newChildNodeRef);
                        dropboxService.persistMetdata(metadata, newChildNodeRef);
                    }
                    syncUpdate(newChildNodeRef, true);
                }
                catch (FileExistsException fee)
                {
                    // TODO What to do here?
                    fee.printStackTrace();
                }
            }
            else
            {
                if (nodeService.getType(childNodeRef).equals(ContentModel.TYPE_CONTENT))
                {
                    update(childNodeRef);
                }
                else if (nodeService.getType(childNodeRef).equals(ContentModel.TYPE_FOLDER))
                {
                    updateChildren(childNodeRef);
                }
            }
        }

        update(nodeRef);
    }


    private void syncUpdate(final NodeRef nodeRef, boolean useParent)
    {
        String currentUser = AuthenticationUtil.getRunAsUser();

        Map<String, NodeRef> syncedUsers = dropboxService.getSyncedUsers(nodeRef);

        if (useParent)
        {
            syncedUsers = filterMap(nodeRef);
        }
        else
        {
            syncedUsers = dropboxService.getSyncedUsers(nodeRef);
        }

        syncedUsers.remove(currentUser);

        for (String key : syncedUsers.keySet())
        {
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork()
                    throws Exception
                {
                    Metadata metadata = null;
                    if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_CONTENT))
                    {
                        metadata = dropboxService.putFile(nodeRef, true);
                        dropboxService.persistMetdata(metadata, nodeRef);
                    }
                    else if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
                    {
                        // If this is a folder, we need to try and create the
                        // folder in Dropbox for the user. If the folder already
                        // exists a 403 status error is returned, at which
                        // point, we get the metadata for the folder and then
                        // update the node with the metadata.
                        try
                        {
                            metadata = dropboxService.createFolder(nodeRef);

                            logger.debug("Dropbox: Add: createFolder: " + nodeRef.toString());
                        }
                        catch (HttpClientErrorException hcee)
                        {
                            if (hcee.getStatusCode().ordinal() == Status.STATUS_FORBIDDEN)
                            {
                                metadata = dropboxService.getMetadata(nodeRef);
                            }
                            else
                            {
                                throw new WebScriptException(hcee.getMessage());
                            }
                        }
                    }

                    if (metadata != null)
                    {
                        dropboxService.persistMetdata(metadata, nodeRef);
                    }
                    else
                    {
                        throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Dropbox metadata maybe out of sync for " + nodeRef);
                    }

                    return null;
                }
            }, key);
        }
    }


    /**
     * Filter out users on the node being passed that are on the parent that
     * don't need to be updated.
     * 
     * @param nodeRef Node being added to the folder
     * @return synced users that need to be updated
     */
    private Map<String, NodeRef> filterMap(NodeRef nodeRef)
    {
        Map<String, NodeRef> filteredMap = new HashMap<String, NodeRef>();

        Map<String, NodeRef> parentMap = dropboxService.getSyncedUsers(nodeService.getPrimaryParent(nodeRef).getParentRef());
        Map<String, NodeRef> childMap = dropboxService.getSyncedUsers(nodeRef);

        if (parentMap.size() > 0)
        {
            if (childMap.size() > 0)
            {
                for (String key : parentMap.keySet())
                {
                    if (!childMap.containsKey(key))
                    {
                        filteredMap.put(key, parentMap.get(key));
                    }
                }
            }
            else
            {
                filteredMap = parentMap;
            }
        }

        return filteredMap;
    }


    private void makeVersionable(NodeRef nodeRef)
    {
        if (!nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE))
        {
            Map<String, Serializable> versionProperties = new HashMap<String, Serializable>();
            versionProperties.put(Version2Model.PROP_VERSION_TYPE, VersionType.MAJOR);

            nodeService.setProperty(nodeRef, ContentModel.PROP_AUTO_VERSION, true);
            nodeService.setProperty(nodeRef, ContentModel.PROP_AUTO_VERSION_PROPS, true);

            versionService.createVersion(nodeRef, versionProperties);
        }
    }

}
