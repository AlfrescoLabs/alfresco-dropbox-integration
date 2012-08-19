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

package org.alfresco.dropbox.service.action;


import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.exceptions.FileExistsException;
import org.alfresco.dropbox.service.DropboxService;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionServiceException;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Status;
import org.springframework.social.dropbox.api.Metadata;
import org.springframework.web.client.HttpClientErrorException;


/**
 * 
 * 
 * @author Jared Ottley
 */
public class DropboxUpdateAction
    extends ActionExecuterAbstractBase
{
    private static final Log   logger             = LogFactory.getLog(DropboxUpdateAction.class);

    private DropboxService     dropboxService;
    private LockService        lockService;
    private NodeService        nodeService;
    private FileFolderService  fileFolderService;

    public static final String DROPBOX_USE_PARENT = "dropbox-use-parent";


    public void setDropboxService(DropboxService dropboxService)
    {
        this.dropboxService = dropboxService;
    }


    public void setLockService(LockService lockService)
    {
        this.lockService = lockService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }


    @Override
    protected void executeImpl(Action action, final NodeRef actionedUponNodeRef)
    {

        LockStatus lockStatus = lockService.getLockStatus(actionedUponNodeRef);

        if (lockStatus.equals(LockStatus.NO_LOCK) || lockStatus.equals(LockStatus.LOCK_EXPIRED))
        {
            // TODO need to find syncedUsers on Parent.

            Map<String, NodeRef> syncedUsers;

            if (useParent(action))
            {
                syncedUsers = filterMap(actionedUponNodeRef);
            }
            else
            {
                syncedUsers = dropboxService.getSyncedUsers(actionedUponNodeRef);
            }

            for (final Map.Entry<String, NodeRef> syncedUser : syncedUsers.entrySet())
            {
                AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
                {
                    public Object doWork()
                        throws Exception
                    {
                        boolean updateParent = false;
                        try
                        {
                            if (nodeService.getType(actionedUponNodeRef).equals(ContentModel.TYPE_CONTENT))
                            {
                                add(actionedUponNodeRef);

                                logger.debug("Dropbox: Add performed by " + syncedUser.getKey());

                                updateParent = true;
                            }
                            else if (nodeService.getType(actionedUponNodeRef).equals(ContentModel.TYPE_FOLDER))
                            {
                                addChildren(actionedUponNodeRef);

                                logger.debug("Dropbox: Add Children performed by " + syncedUser.getKey());

                                updateParent = true;
                            }
                        }
                        catch (FileExistsException fee)
                        {
                            logger.warn("Dropbox: " + fee.getMessage() + " Status OK.");

                            Metadata metadata = dropboxService.getMetadata(fee.getNodeRef());

                            dropboxService.persistMetadata(metadata, fee.getNodeRef());
                            updateParent = true;
                        }
                        finally
                        {
                            if (updateParent)
                            {
                                updateParent(actionedUponNodeRef);

                                logger.debug("Dropbox: Update Parent performed by " + syncedUser.getKey());
                            }
                        }

                        return null;
                    }
                }, syncedUser.getKey());

            }
        }
    }


    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
        paramList.add(new ParameterDefinitionImpl(DROPBOX_USE_PARENT, DataTypeDefinition.BOOLEAN, false, getParamDisplayLabel(DROPBOX_USE_PARENT)));
    }


    private void add(NodeRef nodeRef)
    {
        Metadata metadata = null;

        if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_CONTENT))
        {
            // TODO if this is marked for overwrite...and the file does not
            // exist...will it bomb?
            metadata = dropboxService.putFile(nodeRef, true);

            logger.debug("Dropbox: Add: putFile: " + nodeRef.toString());
        }
        else if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
        {
            // If this is a folder, we need to try and create the folder in
            // Dropbox for the user. If the folder already exists a 403
            // status error is returned, at which point, we get the metadata for
            // the folder and then update the node with the metadata.
            try
            {
                metadata = dropboxService.createFolder(nodeRef);

                logger.debug("Dropbox: Add: createFolder: " + nodeRef.toString());
            }
            catch (HttpClientErrorException hcee)
            {
                if (hcee.getStatusCode().value() == Status.STATUS_FORBIDDEN)
                {
                    metadata = dropboxService.getMetadata(nodeRef);
                }
                else
                {
                    throw new ActionServiceException(hcee.getMessage());
                }
            }
        }

        if (metadata != null)
        {
            dropboxService.persistMetadata(metadata, nodeRef);
        }

        if (nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
        {
            nodeService.removeAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS);
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

                    if (nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
                    {
                        nodeService.removeAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS);
                    }
                }
            }
        }
        else
        {
            add(nodeRef);
        }
    }


    private void updateParent(NodeRef nodeRef)
    {
        ChildAssociationRef childAssociationRef = nodeService.getPrimaryParent(nodeRef);
        NodeRef parent = childAssociationRef.getParentRef();

        if (nodeService.hasAspect(parent, DropboxConstants.Model.ASPECT_DROPBOX))
        {
            Metadata metadata = dropboxService.getMetadata(parent);
            dropboxService.persistMetadata(metadata, parent);
        }
    }


    private boolean useParent(Action action)
    {
        boolean useParent = false;

        if (action.getParameterValue(DROPBOX_USE_PARENT) != null)
        {
            useParent = Boolean.valueOf(action.getParameterValue(DROPBOX_USE_PARENT).toString());
        }
        else
        {
            useParent = false;
        }

        return useParent;
    }


    /**
     * Filter out users on the node being passed that are on the parent that don't need to be updated.
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
                Set<Map.Entry<String, NodeRef>> parentMapSet = parentMap.entrySet();
                Iterator<Map.Entry<String, NodeRef>> i = parentMapSet.iterator();
                while (i.hasNext())
                {
                    Map.Entry<String, NodeRef> entry = i.next();
                    if (!childMap.containsKey(entry.getKey()))
                    {
                        filteredMap.put(entry.getKey(), entry.getValue());
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
}
