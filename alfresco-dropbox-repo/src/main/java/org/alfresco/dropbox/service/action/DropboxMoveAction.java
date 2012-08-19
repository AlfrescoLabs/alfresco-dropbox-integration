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


import java.util.List;
import java.util.Map;

import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.service.DropboxService;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.social.dropbox.api.Metadata;


/**
 * 
 * 
 * @author Jared Ottley
 */
public class DropboxMoveAction
    extends ActionExecuterAbstractBase
{
    private static final Log   logger            = LogFactory.getLog(DropboxMoveAction.class);

    private DropboxService     dropboxService;
    private NodeService        nodeService;

    public static final String DROPBOX_FROM_PATH = "dropbox-from-path";
    public static final String DROPBOX_TO_PATH   = "dropbox-to-path";


    public void setDropboxService(DropboxService dropboxService)
    {
        this.dropboxService = dropboxService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    @Override
    protected void executeImpl(final Action action, final NodeRef actionedUponNodeRef)
    {

        Map<String, NodeRef> syncedUsers = dropboxService.getSyncedUsers(((ChildAssociationRef)action.getParameterValue(DROPBOX_TO_PATH)).getChildRef());

        for (final Map.Entry<String, NodeRef> syncedUser : syncedUsers.entrySet())
        {
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Metadata>()
            {
                public Metadata doWork()
                    throws Exception
                {
                    Metadata metadata = dropboxService.move((ChildAssociationRef)action.getParameterValue(DROPBOX_FROM_PATH), (ChildAssociationRef)action.getParameterValue(DROPBOX_TO_PATH));
                    dropboxService.persistMetadata(metadata, ((ChildAssociationRef)action.getParameterValue(DROPBOX_TO_PATH)).getChildRef());

                    logger.debug("Dropbox: Moved from "
                                 + ((ChildAssociationRef)action.getParameterValue(DROPBOX_FROM_PATH)).toString() + " to "
                                 + ((ChildAssociationRef)action.getParameterValue(DROPBOX_TO_PATH)).toString());

                    if (nodeService.hasAspect(((ChildAssociationRef)action.getParameterValue(DROPBOX_TO_PATH)).getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
                    {
                        nodeService.removeAspect(((ChildAssociationRef)action.getParameterValue(DROPBOX_TO_PATH)).getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS);
                    }

                    return metadata;
                }
            }, syncedUser.getKey());
        }
    }


    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
        paramList.add(new ParameterDefinitionImpl(DROPBOX_FROM_PATH, DataTypeDefinition.CHILD_ASSOC_REF, true, getParamDisplayLabel(DROPBOX_FROM_PATH)));
        paramList.add(new ParameterDefinitionImpl(DROPBOX_TO_PATH, DataTypeDefinition.CHILD_ASSOC_REF, true, getParamDisplayLabel(DROPBOX_TO_PATH)));

    }

}
