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

package org.alfresco.dropbox.service.action;


import java.util.List;

import org.alfresco.dropbox.exceptions.FileNotFoundException;
import org.alfresco.dropbox.service.DropboxService;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 *
 * @author Jared Ottley
 */
public class DropboxDeleteAction
    extends ActionExecuterAbstractBase
{
    private static final Log   logger        = LogFactory.getLog(DropboxDeleteAction.class);

    DropboxService             dropboxService;

    public static final String DROPBOX_PATH  = "dropbox-path";
    public static final String DROPBOX_USERS = "dropbox-users";


    public void setDropboxService(DropboxService dropboxService)
    {
        this.dropboxService = dropboxService;
    }


    @SuppressWarnings("unchecked")
    @Override
    protected void executeImpl(Action action, final NodeRef actionedUponNodeRef)
    {

        final String path = (String)action.getParameterValue(DROPBOX_PATH);
        List<String> users = (List<String>)action.getParameterValue(DROPBOX_USERS);

        for (final String user : users)
        {

            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork()
                    throws Exception
                {
                    try
                    {
                        dropboxService.delete(path);

                        logger.debug("Dropbox: Deleting " + actionedUponNodeRef.toString() + " and any children from Dropbox.");
                    }
                    catch (FileNotFoundException fnfe)
                    {
                        logger.info("Dropbox: " + path + " not found in " + user + "'s account. Status OK.");
                    }

                    return null;
                }
            }, user);
        }
    }


    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
        paramList.add(new ParameterDefinitionImpl(DROPBOX_PATH, DataTypeDefinition.TEXT, true, getParamDisplayLabel(DROPBOX_PATH)));
        paramList.add(new ParameterDefinitionImpl(DROPBOX_USERS, DataTypeDefinition.TEXT, true, getParamDisplayLabel(DROPBOX_USERS)));
    }

}
