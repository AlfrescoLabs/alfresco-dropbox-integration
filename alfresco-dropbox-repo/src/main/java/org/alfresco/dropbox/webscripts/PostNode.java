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


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.dropbox.DropboxConstants;
import org.springframework.social.dropbox.api.Metadata;
import org.springframework.web.client.HttpClientErrorException;
import org.alfresco.dropbox.exceptions.DropboxClientException;
import org.alfresco.dropbox.exceptions.FileExistsException;
import org.alfresco.dropbox.exceptions.FileSizeException;
import org.alfresco.dropbox.exceptions.NotModifiedException;
import org.alfresco.dropbox.exceptions.TooManyFilesException;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.InvalidTypeException;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;


/**
 * @author Jared Ottley
 */
public class PostNode
    extends Node
{

    private static final Log          logger        = LogFactory.getLog(PostNode.class);

    private SiteService         siteService;

    private static final String FOLDER_EXISTS = "403 Forbidden";


    public void setSiteService(SiteService siteService)
    {
        this.siteService = siteService;
    }


    /*
     * (non-Javadoc)
     * @see
     * org.springframework.extensions.webscripts.DeclarativeWebScript#executeImpl
     * (org.springframework.extensions.webscripts.WebScriptRequest,
     * org.springframework.extensions.webscripts.Status,
     * org.springframework.extensions.webscripts.Cache)
     */
    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        // our return map
        Map<String, Object> model = new HashMap<String, Object>();

        // Get the list of nodeRefs passed from Share
        List<NodeRef> nodeRefs = parseNodeRefs(req);

        cache.setNeverCache(true);

        // Loop through the list of nodeRefs passed from Share
        try
        {
            for (NodeRef nodeRef : nodeRefs)
            {
                if (nodeService.exists(nodeRef))
                {
                    try
                    {
                        if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_CONTENT))
                        {
                            add(nodeRef);
                        }
                        else if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
                        {
                            addChildren(nodeRef);
                            // Adding a child will automatically add the parent
                            // folder
                            // add(nodeRef);
                            // We need to persist the folders metadata
                            Metadata metadata = dropboxService.getMetadata(nodeRef);
                            dropboxService.persistMetadata(metadata, nodeRef);
                        }
                    }
                    catch (FileExistsException fee)
                    {
                        logger.warn(fee.getMessage());

                        Metadata metadata = dropboxService.getMetadata(fee.getNodeRef());

                        dropboxService.persistMetadata(metadata, fee.getNodeRef());
                    }
                }

                makeSyncable(nodeRef);

            }
        }
        catch (InvalidNodeRefException inre)
        {
            throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, inre.getMessage());
        }
        catch (InvalidTypeException ite)
        {
            throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, ite.getMessage());
        }
        catch (FileSizeException fse)
        {
            throw new WebScriptException(Status.STATUS_NOT_ACCEPTABLE, fse.getMessage());
        }
        catch (NotModifiedException nme)
        {
            throw new WebScriptException(Status.STATUS_NOT_MODIFIED, nme.getMessage());
        }
        catch (TooManyFilesException tmfe)
        {
            throw new WebScriptException(Status.STATUS_NOT_ACCEPTABLE, tmfe.getMessage());
        }
        catch (DropboxClientException dce)
        {
            throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, dce.getMessage());
        }

        return model;
    }


    private void add(NodeRef nodeRef)
    {
        Metadata metadata = null;

        if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_CONTENT))
        {
            metadata = dropboxService.putFile(nodeRef, false);
        }
        else if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
        {
            try
            {
                metadata = dropboxService.createFolder(nodeRef);

                logger.debug("Dropbox: Add: createFolder: " + nodeRef.toString());
            }
            catch (HttpClientErrorException hcee)
            {
                if (hcee.getMessage().equals(FOLDER_EXISTS))
                {
                    metadata = dropboxService.getMetadata(nodeRef);
                }
                else
                {
                    throw new WebScriptException(hcee.getMessage());
                }
            }
        }

        // update/add the Dropbox aspect and set its properties;
        if (metadata != null)
        {
            dropboxService.persistMetadata(metadata, nodeRef);
        }
        else
        {
            throw new WebScriptException(Status.STATUS_BAD_REQUEST, "Dropbox metadata maybe out of sync for " + nodeRef);
        }
    }


    private void addChildren(NodeRef nodeRef)
    {
        // Get all of the Children on this Folder
        List<FileInfo> children = fileFolderService.list(nodeRef);

        // If there are children, then we want to send them to Dropbox as well
        if (children.size() > 0)
        {

            for (FileInfo fileInfo : children)
            {
                if (nodeService.exists(fileInfo.getNodeRef()))
                {
                    try
                    {
                        if (nodeService.getType(fileInfo.getNodeRef()).equals(ContentModel.TYPE_CONTENT))
                        {
                            add(fileInfo.getNodeRef());
                        }
                        else if (nodeService.getType(fileInfo.getNodeRef()).equals(ContentModel.TYPE_FOLDER))
                        {
                            addChildren(fileInfo.getNodeRef());

                            Metadata metadata = dropboxService.getMetadata(fileInfo.getNodeRef());
                            dropboxService.persistMetadata(metadata, fileInfo.getNodeRef());
                        }
                    }
                    catch (FileExistsException fee)
                    {
                        logger.warn(fee.getMessage());

                        Metadata metadata = dropboxService.getMetadata(fee.getNodeRef());
                        dropboxService.persistMetadata(metadata, fee.getNodeRef());
                    }
                }
            }
        }
        else
        {
            // If the folder has no children just add it.
            add(nodeRef);
        }
    }


    private void makeSyncable(NodeRef nodeRef)
    {
        SiteInfo siteInfo = siteService.getSite(nodeRef);

        if (siteInfo != null)
        {
            if (!nodeService.hasAspect(siteInfo.getNodeRef(), DropboxConstants.Model.ASPECT_SYNCABLE))
            {
                ChildAssociationRef childAssocRef = nodeService.createNode(siteInfo.getNodeRef(), DropboxConstants.Model.ASSOC_SYNC_DETAILS, DropboxConstants.Model.DROPBOX, DropboxConstants.Model.TYPE_STATUS);
                nodeService.setProperty(childAssocRef.getChildRef(), DropboxConstants.Model.PROP_SYNCING, false);

            }
            else
            {
                List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(siteInfo.getNodeRef(), DropboxConstants.Model.ASSOC_SYNC_DETAILS, DropboxConstants.Model.DROPBOX);

                if (childAssoc.size() == 0)
                {
                    ChildAssociationRef childAssocRef = nodeService.createNode(siteInfo.getNodeRef(), DropboxConstants.Model.ASSOC_SYNC_DETAILS, DropboxConstants.Model.DROPBOX, DropboxConstants.Model.TYPE_STATUS);
                    nodeService.setProperty(childAssocRef.getChildRef(), DropboxConstants.Model.PROP_SYNCING, false);
                }
            }
        }
        else
        {
            throw new WebScriptException(nodeRef + "is not contained in a site.");
        }
    }

}
