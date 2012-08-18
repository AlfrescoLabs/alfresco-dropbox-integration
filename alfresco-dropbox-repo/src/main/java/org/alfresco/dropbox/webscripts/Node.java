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


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.alfresco.dropbox.service.DropboxService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.httpclient.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;


/**
 * @author Jared Ottley
 * @author Peter Monks
 */
public class Node
    extends DeclarativeWebScript
{

    // JSON object sent from share contains a single array of nodeRefs
    private final static String JSON_KEY_NODE_REFS = "nodeRefs";

    protected NodeService       nodeService;
    protected DropboxService    dropboxService;
    protected FileFolderService fileFolderService;


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setDropboxService(DropboxService dropboxService)
    {
        this.dropboxService = dropboxService;
    }


    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }


    /*
     * Parse the the JSON object of nodeRefs passed from Share. Lifted from
     * alfresco-jive toolkit
     */
    protected List<NodeRef> parseNodeRefs(final WebScriptRequest req)
    {
        final List<NodeRef> result = new ArrayList<NodeRef>();
        Content content = req.getContent();
        String jsonStr = null;
        JSONObject json = null;

        try
        {
            if (content == null || content.getSize() == 0)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "No content sent with request.");
            }

            jsonStr = content.getContent();

            if (jsonStr == null || jsonStr.trim().length() == 0)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "No content sent with request.");
            }

            json = new JSONObject(jsonStr);

            if (!json.has(JSON_KEY_NODE_REFS))
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Key " + JSON_KEY_NODE_REFS + " is missing from JSON: "
                                                                        + jsonStr);
            }

            JSONArray nodeRefs = json.getJSONArray(JSON_KEY_NODE_REFS);

            for (int i = 0; i < nodeRefs.length(); i++)
            {
                NodeRef nodeRef = new NodeRef(nodeRefs.getString(i));
                result.add(nodeRef);
            }
        }
        catch (final IOException ioe)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage(), ioe);
        }
        catch (final JSONException je)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Unable to parse JSON: " + jsonStr);
        }
        catch (final WebScriptException wse)
        {
            throw wse; // Ensure WebScriptExceptions get rethrown verbatim
        }
        catch (final Exception e)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Unable to retrieve nodeRefs from JSON '" + jsonStr + "'.", e);
        }

        return (result);
    }

}
