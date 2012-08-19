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

package org.alfresco.dropbox;


import org.alfresco.service.namespace.QName;


/**
 * @author Jared Ottley 
 * 
 * All Constants need for Dropbox: Application type, REST APIs
 * (GET, POST, PUT) and Content Model
 */
public interface DropboxConstants
{

    /**
     * Defines the dropbox client as using a specific folder structure within an
     * account, not giving it access to the entire dropbox account
     */

    public static final String COMPANY_HOME    = "/Company Home";
    public static final String DOCUMENTLIBRARY = "/documentLibrary";

    public static final String REMOTE_SYSTEM   = "dropbox";

    public static interface Model
    {

        /**
         * Dropbox namespace
         */
        public static final String ORG_DROPBOX_MODEL_1_0_URI = "http://www.alfresco.org/model/dropbox/1.0";

        /**
         * db:dropbox
         */
        public static final QName  ASPECT_DROPBOX            = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "dropbox");
        public static final QName  DROPBOX                   = ASPECT_DROPBOX;
        public static final QName  ASSOC_DROPBOX             = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "dropbox");
        public static final QName  TYPE_USERS                = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "users");
        public static final QName  ASSOC_USER_METADATA       = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "usermetadata");

        public static final QName  TYPE_METADATA             = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "metadata");
        public static final QName  PROP_HASH                 = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "hash");
        public static final QName  PROP_REV                  = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "rev");

        public static final QName  ASPECT_DROBOX_OAUTH       = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "oauth");
        public static final QName  PROP_ACCESS_TOKEN         = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "access_token");
        public static final QName  PROP_TOKEN_SECRET         = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "token_secret");
        public static final QName  PROP_OAUTH_COMPLETE       = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "oauth_complete");


        /**
         * db:syncable
         */
        public static final QName  ASPECT_SYNCABLE           = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "syncable");
        public static final QName  ASSOC_SYNC_DETAILS        = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "syncdetails");
        /**
         * db:status
         */
        public static final QName  TYPE_STATUS               = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "status");
        public static final QName  PROP_SYNCING              = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "syncing");
        public static final QName  PROP_LAST_SYNC            = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "lastsync");

        public static final QName  ASPECT_SYNC_IN_PROGRESS   = QName.createQName(Model.ORG_DROPBOX_MODEL_1_0_URI, "syncinprogress");

    }

}
