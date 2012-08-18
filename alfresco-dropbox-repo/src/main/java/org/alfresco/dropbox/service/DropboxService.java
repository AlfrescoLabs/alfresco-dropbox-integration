/*
 * Copyright 2012 Alfresco Software Limited.
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

package org.alfresco.dropbox.service;


import java.io.Serializable;
import java.util.Map;

import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.springframework.social.dropbox.api.DropboxUserProfile;
import org.springframework.social.dropbox.api.Metadata;

/**
 * 
 *
 * @author Jared Ottley
 */
public interface DropboxService
{
    /**
     * Get the oAuth1 Authorization Url for the current user
     * 
     * @param callbackUrl
     * @return
     */
    public String getAuthorizeUrl(String callbackUrl);
    
    /**
     * Complete the oAuth1 Flow. Persists the returned tokens for the current User
     * 
     * @param verifier
     * @return
     */
    public boolean completeAuthentication(String verifier);
    
    /**
     * Get the current users Dropbox user profile
     * 
     * @return
     */
    public DropboxUserProfile getUserProfile();
    
    /**
     * Return map of all users who currently have the node synced to their Dropbox accounts with nodeRef of persisted Metadata
     * 
     * @param nodeRef
     * @return
     */
    public Map<String, NodeRef> getSyncedUsers(NodeRef nodeRef);
    
    /**
     * Get the current users Dropbox metadata for the node from Dropbox
     * 
     * @param nodeRef
     * @return
     */
    public Metadata getMetadata(NodeRef nodeRef);
    
    /**
     * Get the Dropbox metadata for the node from Alfresco for the current user.
     * Lookup by nodes path.
     * 
     * @param nodeRef
     * @return
     */
    public Map<QName, Serializable> getPersistedMetadata(NodeRef nodeRef);
    
    /**
     * Persist the Dropbox metadata to the node for the current user.
     * If no other users have the node synced to dropbox the Dropbox aspect
     * is added to the node.
     * 
     * @param metadata
     * @param nodeRef
     */
    public void persistMetadata(Metadata metadata, NodeRef nodeRef);
    
    /**
     * Delete the persisted Dropbox Metadata from the Node for the current user.
     * If no other users have the node synced, the Dropbox Aspect is also removed.
     * 
     * @param nodeRef
     * @return
     */
    public boolean deletePersistedMetadata(NodeRef nodeRef);
    
    /**
     * Delete the persisted Dropbox Metadata from the Node for the named user.
     * Can only be performed by an admin user or site manager.
     * If no other users have the node synced, the Dropbox Aspect is also removed.
     * 
     * @param nodeRef
     * @param userAuthority
     * @return
     */
    public boolean deletePersistedMetadata(final NodeRef nodeRef, String userAuthority);
    
    /**
     * Retrieve the file from Drobox from the current users account.
     * Lookup by path of the node. Writes file to the node.
     * 
     * @param nodeRef
     * @return
     */
    public Metadata getFile(NodeRef nodeRef);
    
    /**
     * Send the node to the current users Dropbox.
     * Location is set by path of current node.
     * 
     * @param nodeRef
     * @param overwrite Creates a new file. The new files name is name-<number>.extension
     * @return
     */
    public Metadata putFile(NodeRef nodeRef, boolean overwrite);
    
    /**
     * Create folder in the current users Dropbox account.
     * Location is set by path of current node. 
     * 
     * @param nodeRef
     * @return
     */
    public Metadata createFolder(NodeRef nodeRef);
    
    /**
     * Get the Dropbox qualified path to the file for the node
     * 
     * @param nodeRef
     * @return
     */
    public String getDropboxPath(NodeRef nodeRef);
    
    /**
     * Is the node synced to Dropbox for the current user.
     * 
     * @param nodeRef
     * @return
     */
    public boolean isSynced(NodeRef nodeRef);
    
    /**
     * Move the file in the current users Dropbox.
     * 
     * @param oldChildAssocRef from Child association reference for node
     * @param newChildAssocRef to Child association reference for node
     * @return
     */
    public Metadata move(ChildAssociationRef oldChildAssocRef, ChildAssociationRef newChildAssocRef);
    
    /**
     * Copy the file in the current users Dropbox
     * 
     * @param originalNodeRef   from the source node location
     * @param newNodeRef        to the target node location
     * @return
     */
    public Metadata copy(NodeRef originalNodeRef, NodeRef newNodeRef);
    
    /**
     * Delete the node from the current users Dropbox
     * 
     * @param nodeRef
     * @return
     */
    public Metadata delete(NodeRef nodeRef);
    
    /**
     * Delete the node from the current users Dropbox
     * 
     * @param path
     * @return
     */
    public Metadata delete(String path);
}
