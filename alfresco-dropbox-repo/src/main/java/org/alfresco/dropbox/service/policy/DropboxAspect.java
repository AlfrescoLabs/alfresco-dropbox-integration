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

package org.alfresco.dropbox.service.policy;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.service.DropboxService;
import org.alfresco.dropbox.service.action.DropboxDeleteAction;
import org.alfresco.dropbox.service.action.DropboxMoveAction;
import org.alfresco.dropbox.service.action.DropboxUpdateAction;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentServicePolicies.OnContentUpdatePolicy;
import org.alfresco.repo.copy.CopyBehaviourCallback;
import org.alfresco.repo.copy.CopyDetails;
import org.alfresco.repo.copy.DoNothingCopyBehaviourCallback;
import org.alfresco.repo.copy.CopyServicePolicies.OnCopyNodePolicy;
import org.alfresco.repo.node.NodeServicePolicies.BeforeDeleteNodePolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnCreateChildAssociationPolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnMoveNodePolicy;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * 
 * 
 * @author Jared Ottley
 */
public class DropboxAspect
    implements OnContentUpdatePolicy, OnCreateChildAssociationPolicy, BeforeDeleteNodePolicy, OnCopyNodePolicy, OnMoveNodePolicy
{

    private static final Log    log                   = LogFactory.getLog(DropboxAspect.class);

    private PolicyComponent     policyComponent;
    private NodeService         nodeService;
    private ActionService       actionService;
    private DropboxService      dropboxService;

    private static final String DROPBOX_UPDATE_ACTION = "dropboxUpdateAction";
    private static final String DROPBOX_DELETE_ACTION = "dropboxDeleteAction";
    private static final String DROPBOX_MOVE_ACTION   = "dropboxMoveAction";


    public void setPolicyComponent(final PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
    }


    public void setNodeService(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setActionService(ActionService actionService)
    {
        this.actionService = actionService;
    }


    public void setDropboxService(DropboxService dropboxService)
    {
        this.dropboxService = dropboxService;
    }


    public void init()
    {
        policyComponent.bindClassBehaviour(OnContentUpdatePolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, new JavaBehaviour(this, "onContentUpdate", NotificationFrequency.TRANSACTION_COMMIT));
        policyComponent.bindAssociationBehaviour(OnCreateChildAssociationPolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, ContentModel.ASSOC_CONTAINS, new JavaBehaviour(this, "onCreateChildAssociation", NotificationFrequency.TRANSACTION_COMMIT));
        policyComponent.bindClassBehaviour(BeforeDeleteNodePolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, new JavaBehaviour(this, "beforeDeleteNode", NotificationFrequency.FIRST_EVENT));
        policyComponent.bindClassBehaviour(OnCopyNodePolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, new JavaBehaviour(this, "getCopyCallback"));
        policyComponent.bindClassBehaviour(OnMoveNodePolicy.QNAME, DropboxConstants.Model.ASPECT_DROPBOX, new JavaBehaviour(this, "onMoveNode", NotificationFrequency.FIRST_EVENT));
    }


    public void onContentUpdate(NodeRef nodeRef, boolean newContent)
    {
        if (!newContent)
        {
            if (!nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
            {
                nodeService.addAspect(nodeRef, DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS, null);
            }

            actionService.executeAction(actionService.createAction(DROPBOX_UPDATE_ACTION), nodeRef, false, true);

            log.debug("Dropbox: Updating " + nodeRef.toString() + "in Dropbox");
        }
    }


    public void onCreateChildAssociation(ChildAssociationRef childAssocRef, boolean isNewNode)
    {
        if (!nodeService.hasAspect(childAssocRef.getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
        {
            nodeService.addAspect(childAssocRef.getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS, null);
        }

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put(DropboxUpdateAction.DROPBOX_USE_PARENT, true);

        actionService.executeAction(actionService.createAction(DROPBOX_UPDATE_ACTION, params), childAssocRef.getChildRef(), false, true);

        log.debug("Dropbox: New child (" + childAssocRef.getChildRef().toString() + ") in Synced Folder"
                  + childAssocRef.getParentRef().toString() + "will be synced to Dropbox.");
    }


    public void beforeDeleteNode(NodeRef nodeRef)
    {
        if (nodeService.exists(nodeRef))
        {
            List<String> users = new ArrayList<String>();
            Map<String, Serializable> params = new HashMap<String, Serializable>();
            params.put(DropboxDeleteAction.DROPBOX_PATH, dropboxService.getDropboxPath(nodeRef) + "/"
                                                         + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME));
            Map<String, NodeRef> syncedUsers = dropboxService.getSyncedUsers(nodeRef);
            users.addAll(syncedUsers.keySet());
            params.put(DropboxDeleteAction.DROPBOX_USERS, (Serializable)users);

            actionService.executeAction(actionService.createAction(DROPBOX_DELETE_ACTION, params), nodeRef, false, true);

            log.debug("Dropbox: Deleting " + nodeRef.toString() + " from Dropbox.");
        }
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.alfresco.repo.copy.CopyServicePolicies.OnCopyNodePolicy#getCopyCallback (org.alfresco.service.namespace.QName,
     * org.alfresco.repo.copy.CopyDetails)
     */
    public CopyBehaviourCallback getCopyCallback(QName classRef, CopyDetails copyDetails)
    {
        log.info("Dropbox: Copying " + copyDetails.getSourceNodeRef().toString() + ".  Dropbox aspect will be  removed from copy.");

        return new DoNothingCopyBehaviourCallback();
    }


    public void onMoveNode(ChildAssociationRef oldChildAssocRef, ChildAssociationRef newChildAssocRef)
    {
        if (!nodeService.hasAspect(newChildAssocRef.getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS))
        {
            nodeService.addAspect(newChildAssocRef.getChildRef(), DropboxConstants.Model.ASPECT_SYNC_IN_PROGRESS, null);
        }

        Map<String, Serializable> params = new HashMap<String, Serializable>();

        params.put(DropboxMoveAction.DROPBOX_FROM_PATH, oldChildAssocRef);
        params.put(DropboxMoveAction.DROPBOX_TO_PATH, newChildAssocRef);

        actionService.executeAction(actionService.createAction(DROPBOX_MOVE_ACTION, params), null, false, true);

        log.debug("Dropbox: Moving" + newChildAssocRef.getChildRef().toString() + " and any children");
    }

}
