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

package org.alfresco.dropbox.service;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.dropbox.exceptions.DropboxAuthenticationException;
import org.alfresco.dropbox.exceptions.DropboxClientException;
import org.alfresco.dropbox.exceptions.FileNotFoundException;
import org.alfresco.dropbox.exceptions.FileSizeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.oauth1.OAuth1CredentialsStoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth1CredentialsInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Status;
import org.springframework.social.connect.Connection;
import org.springframework.social.dropbox.api.Dropbox;
import org.springframework.social.dropbox.api.DropboxFile;
import org.springframework.social.dropbox.api.DropboxUserProfile;
import org.springframework.social.dropbox.api.Metadata;
import org.springframework.social.dropbox.connect.DropboxConnectionFactory;
import org.springframework.social.oauth1.AuthorizedRequestToken;
import org.springframework.social.oauth1.OAuth1Operations;
import org.springframework.social.oauth1.OAuth1Parameters;
import org.springframework.social.oauth1.OAuthToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;


/**
 * 
 * 
 * @author Jared Ottley
 */
public class DropboxServiceImpl
    implements DropboxService
{

    private static Log                    logger    = LogFactory.getLog(DropboxServiceImpl.class);

    private PersonService                 personService;
    private NodeService                   nodeService;
    private PermissionService             permissionService;
    private ContentService                contentService;
    private SysAdminParams                sysAdminParams;
    private AuthorityService              authorityService;
    private SiteService                   siteService;
    private BehaviourFilter               behaviourFilter;
    private OAuth1CredentialsStoreService oauth1CredentialsStoreService;

    private DropboxConnectionFactory      connectionFactory;

    private static final String           NOT_FOUND = "404 Not Found";


    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setContentService(ContentService contentService)
    {
        this.contentService = contentService;
    }


    public void setPermissionService(PermissionService permissionService)
    {
        this.permissionService = permissionService;
    }


    public void setSysAdminParams(SysAdminParams sysAdminParams)
    {
        this.sysAdminParams = sysAdminParams;
    }


    public void setAuthorityService(AuthorityService authorityService)
    {
        this.authorityService = authorityService;
    }


    public void setSiteService(SiteService siteService)
    {
        this.siteService = siteService;
    }


    public void setBehaviourFilter(BehaviourFilter behaviourFilter)
    {
        this.behaviourFilter = behaviourFilter;
    }


    public void setConnectionFactory(DropboxConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }


    public void setOauth1CredentialsStoreService(OAuth1CredentialsStoreService oauth1CredentialsStoreService)
    {
        this.oauth1CredentialsStoreService = oauth1CredentialsStoreService;
    }


    // Dropbox Connection

    private Connection<Dropbox> getConnection()
        throws DropboxAuthenticationException
    {
        Connection<Dropbox> connection = null;

        OAuth1CredentialsInfo credentialsInfo = oauth1CredentialsStoreService.getPersonalOAuth1Credentials(DropboxConstants.REMOTE_SYSTEM);

        if (credentialsInfo != null)
        {
            OAuthToken accessToken = new OAuthToken(credentialsInfo.getOAuthToken(), credentialsInfo.getOAuthSecret());

            try
            {
                connection = connectionFactory.createConnection(accessToken);
            }
            catch (HttpClientErrorException hcee)
            {
                if (hcee.getStatusCode().ordinal() == Status.STATUS_FORBIDDEN)
                {
                    throw new DropboxAuthenticationException();
                }

            }
        }

        logger.debug("Dropbox Connection made for " + AuthenticationUtil.getRunAsUser());

        return connection;
    }


    // Dropbox Authentication

    public String getAuthorizeUrl(String callbackUrl)
    {

        String authorizeUrl = null;

        if (callbackUrl != null)
        {
            OAuth1Parameters parameters = new OAuth1Parameters(callbackUrl);

            OAuth1Operations operations = connectionFactory.getOAuthOperations();

            OAuthToken requestToken = operations.fetchRequestToken(parameters.getCallbackUrl(), null);

            persistTokens(requestToken, false);

            nodeService.setProperty(personService.getPerson(AuthenticationUtil.getRunAsUser()), DropboxConstants.Model.PROP_OAUTH_COMPLETE, false);

            authorizeUrl = operations.buildAuthorizeUrl(requestToken.getValue(), parameters);

        }

        return authorizeUrl;
    }


    public boolean completeAuthentication(String verifier)
    {
        boolean authenticationComplete = false;

        if (verifier != null)
        {
            NodeRef person = personService.getPerson(AuthenticationUtil.getRunAsUser());

            if (nodeService.hasAspect(person, DropboxConstants.Model.ASPECT_DROBOX_OAUTH))
            {

                OAuth1Operations operations = connectionFactory.getOAuthOperations();

                OAuth1CredentialsInfo credintialsInfo = getTokenFromUser();

                OAuthToken accessToken = operations.exchangeForAccessToken(new AuthorizedRequestToken(new OAuthToken(credintialsInfo.getOAuthToken(), credintialsInfo.getOAuthSecret()), verifier), null);

                persistTokens(accessToken, true);

                nodeService.setProperty(person, DropboxConstants.Model.PROP_OAUTH_COMPLETE, true);

                authenticationComplete = true;
            }
        }

        return authenticationComplete;
    }


    private OAuth1CredentialsInfo getTokenFromUser()
    {
        return oauth1CredentialsStoreService.getPersonalOAuth1Credentials(DropboxConstants.REMOTE_SYSTEM);

    }


    private void persistTokens(OAuthToken token, boolean complete)
    {
        OAuth1CredentialsInfo credentialsInfo = oauth1CredentialsStoreService.getPersonalOAuth1Credentials(DropboxConstants.REMOTE_SYSTEM);

        oauth1CredentialsStoreService.storePersonalOAuth1Credentials(DropboxConstants.REMOTE_SYSTEM, token.getValue(), token.getSecret());

        if (credentialsInfo != null)
        {
            HashMap<QName, Serializable> properties = new HashMap<QName, Serializable>();
            properties.put(DropboxConstants.Model.PROP_OAUTH_COMPLETE, complete);

            NodeRef person = personService.getPerson(AuthenticationUtil.getRunAsUser());
            nodeService.addAspect(person, DropboxConstants.Model.ASPECT_DROBOX_OAUTH, properties);
        }
    }


    public DropboxUserProfile getUserProfile()
    {
        DropboxUserProfile profile;

        Connection<Dropbox> connection = this.getConnection();
        profile = connection.getApi().getUserProfile();

        logger.debug("Get Dropbox User Profile for " + AuthenticationUtil.getRunAsUser());

        return profile;
    }


    // Dropbox Actions

    public Metadata getMetadata(NodeRef nodeRef)
    {
        String hash = null;

        // Get the hash if it exists
        if (nodeService.getProperty(nodeRef, DropboxConstants.Model.PROP_HASH) != null)
        {
            hash = nodeService.getProperty(nodeRef, DropboxConstants.Model.PROP_HASH).toString();
        }

        String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

        Metadata metadata;
        Connection<Dropbox> connection = this.getConnection();
        metadata = connection.getApi().getItemMetadata(path, hash);

        logger.debug("Get Metadata for " + path + ": " + this.metadataAsJSON(metadata));

        return metadata;
    }


    public Metadata copy(NodeRef originalNodeRef, NodeRef newNodeRef)
    {
        Metadata metadata;
        Connection<Dropbox> connection = this.getConnection();

        String from_path = getDropboxPath(originalNodeRef) + "/" + nodeService.getProperty(originalNodeRef, ContentModel.PROP_NAME);
        String to_path = getDropboxPath(newNodeRef) + "/" + nodeService.getProperty(newNodeRef, ContentModel.PROP_NAME);

        metadata = connection.getApi().copy(from_path, to_path);

        logger.debug("Copy " + from_path + " to " + to_path + ". New Metadata: " + this.metadataAsJSON(metadata));

        return metadata;
    }


    public Metadata createFolder(NodeRef nodeRef)
    {
        Metadata metadata;
        Connection<Dropbox> connection = this.getConnection();

        String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

        metadata = connection.getApi().createFolder(path);

        logger.debug("Create Folder at " + path + ". New Metadata: " + this.metadataAsJSON(metadata));

        return metadata;
    }


    public Metadata delete(NodeRef nodeRef)
    {
        Metadata metadata;
        Connection<Dropbox> connection = this.getConnection();

        String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

        try
        {
            metadata = connection.getApi().delete(path);

            logger.debug("Delete " + path + ". Deleted Metadata: " + this.metadataAsJSON(metadata));
        }
        catch (RestClientException rce)
        {
            if (rce.getMessage().equals(NOT_FOUND))
            {
                throw new FileNotFoundException(nodeRef);
            }
            else
            {
                throw new DropboxClientException(rce.getMessage());
            }
        }

        return metadata;
    }


    public Metadata delete(String path)
    {
        Metadata metadata;
        Connection<Dropbox> connection = this.getConnection();

        try
        {
            metadata = connection.getApi().delete(path);

            logger.debug("Delete " + path + ". Deleted Metadata: " + this.metadataAsJSON(metadata));
        }
        catch (RestClientException rce)
        {
            if (rce.getMessage().equals(NOT_FOUND))
            {
                throw new FileNotFoundException();
            }
            else
            {
                throw new DropboxClientException(rce.getMessage());
            }
        }

        return metadata;
    }


    public Metadata move(ChildAssociationRef oldChildAssocRef, ChildAssociationRef newChildAssocRef)
    {
        Metadata metadata;
        Connection<Dropbox> connection = this.getConnection();

        String from_path = getDropboxPath(oldChildAssocRef.getParentRef()) + "/"
                           + nodeService.getProperty(oldChildAssocRef.getChildRef(), ContentModel.PROP_NAME);
        String to_path = getDropboxPath(newChildAssocRef.getChildRef()) + "/"
                         + nodeService.getProperty(newChildAssocRef.getChildRef(), ContentModel.PROP_NAME);

        metadata = connection.getApi().move(from_path, to_path);

        logger.debug("Move " + from_path + " to " + to_path + ". New Metadata: " + this.metadataAsJSON(metadata));

        return metadata;
    }


    public Metadata getFile(NodeRef nodeRef)
    {
        Metadata metadata;
        Connection<Dropbox> connection = this.getConnection();

        String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);

        DropboxFile dropboxFile = connection.getApi().getFile(path);

        try
        {
            ContentWriter writer = contentService.getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
            writer.guessEncoding();
            writer.setMimetype(dropboxFile.getContentType().split(";")[0]);
            writer.putContent(dropboxFile.getInputStream());
        }
        catch (ContentIOException cio)
        {
            cio.printStackTrace();
        }

        metadata = this.getMetadata(nodeRef);

        logger.debug("Get File " + path + ". File Metadata: " + this.metadataAsJSON(metadata));

        return metadata;
    }


    public Metadata putFile(NodeRef nodeRef, boolean overwrite)
    {
        // 150 MB
        final long MAX_FILE_SIZE = 157286400L;

        Metadata metadata = null;
        Connection<Dropbox> connection = this.getConnection();

        ContentReader contentReader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);

        try
        {
            if (contentReader.getSize() < MAX_FILE_SIZE)
            {
                String path = getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
                metadata = connection.getApi().putFile(path, getBytes(contentReader), null, overwrite, null);
            }
            else
            {
                throw new FileSizeException();
            }
        }
        catch (IOException ioe)
        {
            throw new DropboxClientException(ioe.getMessage());
        }

        logger.debug("Put File " + getDropboxPath(nodeRef) + "/" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)
                     + ". File Metadata " + this.metadataAsJSON(metadata));

        return metadata;
    }


    public void persistMetadata(Metadata metadata, NodeRef nodeRef)
    {

        List<ChildAssociationRef> dropboxAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

        ChildAssociationRef usersAssocRef;
        if (dropboxAssoc.size() == 0)
        {
            usersAssocRef = nodeService.createNode(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.TYPE_USERS);
        }
        else
        {
            usersAssocRef = dropboxAssoc.get(0);
        }

        List<ChildAssociationRef> usersAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()));

        ChildAssociationRef userAssocRef;
        if (usersAssoc.size() == 0)
        {
            userAssocRef = nodeService.createNode(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()), DropboxConstants.Model.TYPE_METADATA);
        }
        else
        {
            userAssocRef = usersAssoc.get(0);
        }

        Map<QName, Serializable> properties = new HashMap<QName, Serializable>();
        properties.put(DropboxConstants.Model.PROP_REV, metadata.getRev());
        // properties.put(DropboxConstants.Model.PROP_REVISION, metadata.get)
        properties.put(ContentModel.PROP_MODIFIED, metadata.getModified());

        // If a hash is returned we might need to update it
        if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER) && metadata.getHash() != null)
        {
            // If the hash is not the same update it
            if (nodeService.getProperty(userAssocRef.getChildRef(), DropboxConstants.Model.PROP_HASH) != null)
            {
                if (!nodeService.getProperty(userAssocRef.getChildRef(), DropboxConstants.Model.PROP_HASH).equals(metadata.getHash()))
                {
                    properties.put(DropboxConstants.Model.PROP_HASH, metadata.getHash());
                }
            }
            else
            {
                properties.put(DropboxConstants.Model.PROP_HASH, metadata.getHash());
            }
        }

        behaviourFilter.disableBehaviour(userAssocRef.getChildRef(), ContentModel.ASPECT_AUDITABLE);
        nodeService.addProperties(userAssocRef.getChildRef(), properties);

    }


    public Map<QName, Serializable> getPersistedMetadata(NodeRef nodeRef)
    {
        Map<QName, Serializable> properties = null;
        List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

        ChildAssociationRef usersAssocRef;
        if (childAssoc.size() > 0)
        {
            usersAssocRef = childAssoc.get(0);

            List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()));

            if (userAssoc.size() == 0)
            {
                throw new DropboxClientException("No Metadata for this User");
            }
            else
            {
                properties = new HashMap<QName, Serializable>();
                properties.put(DropboxConstants.Model.PROP_REV, nodeService.getProperty(userAssoc.get(0).getChildRef(), DropboxConstants.Model.PROP_REV));
                properties.put(ContentModel.PROP_MODIFIED, nodeService.getProperty(userAssoc.get(0).getChildRef(), ContentModel.PROP_MODIFIED));

                if (nodeService.getType(nodeRef).equals(ContentModel.TYPE_FOLDER))
                {
                    properties.put(DropboxConstants.Model.PROP_HASH, nodeService.getProperty(userAssoc.get(0).getChildRef(), DropboxConstants.Model.PROP_HASH));
                }
            }
        }

        return properties;
    }


    public boolean deletePersistedMetadata(NodeRef nodeRef)
    {
        boolean deleted = false;
        List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

        if (childAssoc.size() > 0)
        {
            ChildAssociationRef usersAssocRef = childAssoc.get(0);

            List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()));

            if (userAssoc.size() > 0)
            {
                deleted = nodeService.removeChildAssociation(userAssoc.get(0));
            }

            if (getSyncCount(nodeRef) == 0)
            {
                nodeService.removeChildAssociation(childAssoc.get(0));
                nodeService.removeAspect(nodeRef, DropboxConstants.Model.ASPECT_DROPBOX);
            }
        }

        return deleted;
    }


    public boolean deletePersistedMetadata(final NodeRef nodeRef, String userAuthority)
    {
        boolean deleted = false;

        SiteInfo siteInfo = siteService.getSite(nodeRef);
        if (siteInfo != null)
        {
            if (authorityService.isAdminAuthority(AuthenticationUtil.getRunAsUser())
                || siteService.getMembersRole(siteInfo.getShortName(), AuthenticationUtil.getRunAsUser()).equals("SiteManager"))
            {
                deleted = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Boolean>()
                {
                    public Boolean doWork()
                        throws Exception
                    {
                        return deletePersistedMetadata(nodeRef);
                    }
                }, userAuthority);
            }
        }

        return deleted;
    }


    /**
     * All the user metadata for the synched users. User must be Repository Admin or Site Manager.
     * 
     * @param nodeRef
     * @return
     */
    public Map<String, NodeRef> getSyncedUsers(NodeRef nodeRef)
    {
        Map<String, NodeRef> syncedUsers = new HashMap<String, NodeRef>();

        if (nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_DROPBOX))
        {
            List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

            if (childAssoc.size() > 0)
            {
                ChildAssociationRef usersAssocRef = childAssoc.get(0);

                Set<QName> childNodeTypeQNames = new HashSet<QName>();
                childNodeTypeQNames.add(DropboxConstants.Model.TYPE_METADATA);
                List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), childNodeTypeQNames);

                if (userAssoc.size() > 0)
                {
                    for (Iterator<ChildAssociationRef> iterator = userAssoc.iterator(); iterator.hasNext();)
                    {
                        ChildAssociationRef childAssociationRef = iterator.next();
                        syncedUsers.put(childAssociationRef.getQName().getLocalName(), childAssociationRef.getChildRef());
                    }
                }
            }
        }

        return syncedUsers;
    }


    /**
     * Is the node synced to Dropbox for the currently authenticated User
     * 
     * @param nodeRef
     * @return
     */
    public boolean isSynced(NodeRef nodeRef)
    {
        boolean synced = false;

        List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

        ChildAssociationRef usersAssocRef;
        if (childAssoc.size() == 0)
        {
            usersAssocRef = nodeService.createNode(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.TYPE_USERS);
        }
        else
        {
            usersAssocRef = childAssoc.get(0);
        }

        List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), DropboxConstants.Model.ASSOC_USER_METADATA, QName.createQName(DropboxConstants.Model.ORG_DROPBOX_MODEL_1_0_URI, AuthenticationUtil.getRunAsUser()));

        if (userAssoc.size() > 0)
        {
            synced = true;
        }

        return synced;
    }


    /**
     * Is the node synced to Dropbox for the user. Requires Admin or SiteManager role to run
     * 
     * @param nodeRef
     * @param userAuthority
     * @return
     */
    public boolean isSynced(final NodeRef nodeRef, String userAuthority)
    {
        boolean synced = false;

        SiteInfo siteInfo = siteService.getSite(nodeRef);
        if (siteInfo != null)
        {
            if (authorityService.isAdminAuthority(AuthenticationUtil.getRunAsUser())
                || siteService.getMembersRole(siteInfo.getShortName(), AuthenticationUtil.getRunAsUser()).equals("SiteManager"))
            {
                synced = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Boolean>()
                {
                    public Boolean doWork()
                        throws Exception
                    {
                        return isSynced(nodeRef);
                    }
                }, userAuthority);
            }
        }

        return synced;
    }


    /**
     * Total number of users who have the node synced to their Dropbox account. If -1 is returned then the total could not be
     * determined.
     * 
     * @param nodeRef
     * @return
     */
    private int getSyncCount(final NodeRef nodeRef)
    {
        int count = -1;

        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Integer>()
        {
            public Integer doWork()
                throws Exception
            {
                int count = -1;

                List<ChildAssociationRef> childAssoc = nodeService.getChildAssocs(nodeRef, DropboxConstants.Model.ASSOC_DROPBOX, DropboxConstants.Model.ASSOC_DROPBOX);

                if (childAssoc.size() > 0)
                {
                    ChildAssociationRef usersAssocRef = childAssoc.get(0);

                    Set<QName> childNodeTypeQNames = new HashSet<QName>();
                    childNodeTypeQNames.add(DropboxConstants.Model.ASSOC_USER_METADATA);
                    List<ChildAssociationRef> userAssoc = nodeService.getChildAssocs(usersAssocRef.getChildRef(), childNodeTypeQNames);

                    if (userAssoc.size() >= 0)
                    {
                        count = userAssoc.size();
                    }
                }

                return count;
            }
        }, AuthenticationUtil.getAdminUserName());

        return count;
    }


    public String getDropboxPath(NodeRef nodeRef)
    {
        String path = nodeService.getPath(nodeRef).toDisplayPath(nodeService, permissionService);
        path = path.replaceFirst(DropboxConstants.COMPANY_HOME, sysAdminParams.getShareHost());
        path = path.replaceFirst(DropboxConstants.DOCUMENTLIBRARY, "");

        logger.debug("Path: " + path);

        return path;
    }


    private byte[] getBytes(ContentReader reader)
        throws IOException
    {
        InputStream originalInputStream = reader.getContentInputStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final int BUF_SIZE = 1 << 8; // 1KiB buffer
        byte[] buffer = new byte[BUF_SIZE];
        int bytesRead = -1;
        while ((bytesRead = originalInputStream.read(buffer)) > -1)
        {
            outputStream.write(buffer, 0, bytesRead);
        }
        originalInputStream.close();
        return outputStream.toByteArray();
    }


    private String metadataAsJSON(Metadata metadata)
    {

        String json;

        json = "{ \"size\": \"" + metadata.getSize() + "\",\"{\"bytes\": " + metadata.getBytes() + ", \"is_dir\": "
               + metadata.isDir() + ", \"is_deleted\": " + metadata.isDeleted() + ", \"rev\": \"" + metadata.getRev()
               + "\", \"hash\": \"" + metadata.getHash() + "\", \"thumb_exists\": " + metadata.isThumbExists() + ", \"icon\": \""
               + metadata.getIcon() + "\", \"modified\": \"" + metadata.getModified() + "\", \"root\": \"" + metadata.getRoot()
               + "\", \"path\": \"" + metadata.getPath() + "\", \"mime_type\": \"" + metadata.getMimeType()
               + "\", \"contents\": \"" + metadata.getContents() + "\"}";

        return json;
    }

}
