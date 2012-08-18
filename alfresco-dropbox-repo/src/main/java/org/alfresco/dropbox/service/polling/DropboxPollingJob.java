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

package org.alfresco.dropbox.service.polling;

import org.alfresco.error.AlfrescoRuntimeException;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * 
 *
 * @author Jared Ottley
 */
public class DropboxPollingJob
    implements Job
{

    public void execute(JobExecutionContext context)
        throws JobExecutionException
    {
        JobDataMap jobData = context.getJobDetail().getJobDataMap();
        // extract the content cleaner to use
        Object dropboxPollerObj = jobData.get("dropboxPoller");
        if (dropboxPollerObj == null || !(dropboxPollerObj instanceof DropboxPoller))
        {
            throw new AlfrescoRuntimeException(
                    "DropboxPollerJob data must contain valid 'dropboxPoller' reference");
        }
        DropboxPoller dropboxPoller = (DropboxPoller) dropboxPollerObj;
        dropboxPoller.execute();
        
    }

}
