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
import java.util.Map;

import org.alfresco.dropbox.DropboxConstants;
import org.alfresco.service.cmr.dictionary.InvalidAspectException;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.NoSuchPersonException;
import org.alfresco.service.cmr.security.PersonService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * 
 *
 * @author Jared Ottley
 */
public class DelinkDropboxUser
    extends DeclarativeWebScript
{
    private static Log          logger  = LogFactory.getLog(DelinkDropboxUser.class);

    private PersonService       personService;
    private NodeService         nodeService;

    private static final String SUCCESS = "success";


    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
        this.personService.setCreateMissingPeople(false);
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        Map<String, String> templateArgs = req.getServiceMatch().getTemplateVars();
        String user = templateArgs.get("user");
        try
        {
            NodeRef nodeRef = personService.getPerson(user);

            if (nodeService.hasAspect(nodeRef, DropboxConstants.Model.ASPECT_DROBOX_OAUTH))
            {
                try
                {
                    nodeService.removeAspect(nodeRef, DropboxConstants.Model.ASPECT_DROBOX_OAUTH);
                    // TODO This may need to be expanded out to remove much more
                    // ie, removing all synched content. this should be done as
                    // an action...async
                }
                catch (InvalidNodeRefException ine)
                {
                    throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, ine.getMessage());
                }
                catch (InvalidAspectException iae)
                {
                    throw new WebScriptException(Status.STATUS_INTERNAL_SERVER_ERROR, iae.getMessage());
                }

                model.put(SUCCESS, true);
            }
            else
            {
                model.put(SUCCESS, false);
            }
        }
        catch (NoSuchPersonException nspe)
        {
            logger.debug(nspe.getMessage());
            throw new WebScriptException(Status.STATUS_NOT_FOUND, "User Not Found");
        }


        return model;
    }
}
