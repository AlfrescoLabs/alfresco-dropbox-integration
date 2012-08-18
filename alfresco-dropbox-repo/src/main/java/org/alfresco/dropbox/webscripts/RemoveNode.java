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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.exceptions.FileNotFoundException;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;


/**
 * Recursive delete and removal off dropbox aspect.
 * 
 * TODO is it better to remove once from dropbox and then walk the folder 
 * structure to remove aspects? It costs on the dropbox side, each requests 
 * goes against limit Better choice would be to add transaction helper, 
 * call the single delete against dropbox and then if a failure occurs
 * during the removal of the aspects call the dropbox restore on the folder
 * Leave as is for demo/poc
 * 
 * @author Jared Ottley
 **/
public class RemoveNode
    extends Node
{
    private static Log logger = LogFactory.getLog(RemoveNode.class);


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {

        // our return map
        Map<String, Object> model = new HashMap<String, Object>();

        // Get the list of nodeRefs passed from Share
        List<NodeRef> nodeRefs = parseNodeRefs(req);

        cache.setNeverCache(true);

        // Loop through the list of nodeRefs passed from Share
        for (NodeRef nodeRef : nodeRefs)
        {
            if (nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_DROPBOX))
            {
                if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_CONTENT))
                {
                    remove(nodeRef);
                }
                else if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
                {
                    removeChildren(nodeRef);
                    remove(nodeRef);
                }
            }
        }

        return model;
    }


    private boolean deleteContent(NodeRef nodeRef)
    {
        boolean deleted = false;

        try
        {
            dropboxService.delete(nodeRef);
            deleted = true;
        }
        catch (FileNotFoundException fnfe)
        {
            logger.info(nodeRef.toString() + " not found. Status OK.");
            deleted = true;
        }

        logger.debug("Dropbox: " + nodeRef.toString() + " was deleted from Dropbox.");
        return deleted;
    }


    private void remove(NodeRef nodeRef)
    {
        boolean deleted = deleteContent(nodeRef);

        if (deleted)
        {
            dropboxService.deletePersistedMetadata(nodeRef);
        }
    }


    private void removeChildren(NodeRef nodeRef)
    {
        if (nodeService.exists(nodeRef))
        {
            List<FileInfo> list = fileFolderService.list(nodeRef);

            for (Iterator<FileInfo> iterator = list.iterator(); iterator.hasNext();)
            {
                FileInfo fileInfo = iterator.next();

                if (nodeService.exists(fileInfo.getNodeRef()))
                {
                    if (nodeService.getType(fileInfo.getNodeRef()).equals(ContentModel.TYPE_CONTENT))
                    {
                        remove(fileInfo.getNodeRef());
                    }
                    else if (nodeService.getType(fileInfo.getNodeRef()).equals(ContentModel.TYPE_FOLDER))
                    {
                        removeChildren(fileInfo.getNodeRef());
                        remove(fileInfo.getNodeRef());
                    }
                }
            }
        }
    }
}
