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
 
/**
 * User Notifications component.
 * 
 * @author Jared Ottley
 * 
 * @namespace Alfresco
 * @class Alfresco.UserDropbox
 */
(function()
{
   /**
    * YUI Library aliases
    */
   var Dom = YAHOO.util.Dom,
      Event = YAHOO.util.Event;
      
   /**
    * UserNotifications constructor.
    * 
    * @param {String} htmlId The HTML id of the parent element
    * @return {Alfresco.UserDropbox} The new UserDropbox instance
    * @constructor
    */
   Alfresco.UserDropbox = function(htmlId)
   {
      Alfresco.UserDropbox.superclass.constructor.call(this, "Alfresco.UserDropbox", htmlId, ["button"]);
      return this;
   }
   
   YAHOO.extend(Alfresco.UserDropbox, Alfresco.component.Base,
   {
      /**
       * Fired by YUI when parent element is available for scripting.
       * Component initialisation, including instantiation of YUI widgets and event listener binding.
       *
       * @method onReady
       */
      onReady: function UP_onReady()
      {
         // Reference to self used by inline functions
         var me = this;
         
         // Buttons
         this.widgets.ok = Alfresco.util.createYUIButton(this, "dropbox-button", null,
            {
               type: "submit"
            });
         
         this.widgets.link = Alfresco.util.createYUIButton(this, "dropbox-link", this.onClick, {type: "link" });
        
         if (this.widgets.ok != null){
	         // Form definition
	         var form = new Alfresco.forms.Form(this.id + "-form");
	         form.setSubmitElements(this.widgets.ok);
	         form.setSubmitAsJSON(true);
	         form.setAJAXSubmit(true,
	         {
	            successCallback:
	            {
	               fn: this.onSuccess,
	               scope: this
	            }
	         });
	         
	         // Initialise the form
	         form.init();
         }
         
         // Finally show the main component body here to prevent UI artifacts on YUI button decoration
         Dom.setStyle(this.id + "-body", "display", "block");
      },
      
      onClick: function redState() {
    	 Alfresco.util.Ajax.jsonGet(
    	{
    		            url: Alfresco.constants.PROXY_URI_RELATIVE + "/dropbox/account/authurl?callback=" + window.location.protocol + "//" + window.location.host,
    		            successCallback: 
    		            {
    					    fn: function(response)
    					    {
    						    window.location=response.json.authURL;
    						},
    						scope: this
    		            },
    		            failureCallback:
    		            {
    		            	fn: function(response)
    		            	{
    		            		Alfresco.util.PopupManager.displayPrompt(
    		                    {
    		                    	text: this.msg("Need Failure Message", this.name)
    		                    });
    		                },
    		                scope: this
    		            }
    	 });
    		            
      },

      /**
       * YUI WIDGET EVENT HANDLERS
       * Handlers for standard events fired from YUI widgets, e.g. "click"
       */
      
      /**
       * Save Changes form submit success handler
       *
       * @method onSuccess
       * @param response {object} Server response object
       */
      onSuccess: function UP_onSuccess(response)
      {
         if (response && response.json)
         {
            if (response.json.success)
            {
               // succesfully updated details - refresh back to the user profile main page
               Alfresco.util.PopupManager.displayMessage(
               {
                  text: Alfresco.util.message("message.success", this.name)
               });
               this.navigateToProfile();
            }
            else if (response.json.message)
            {
               Alfresco.util.PopupManager.displayPrompt(
               {
                  text: response.json.message
               });
            }
         }
         else
         {
            Alfresco.util.PopupManager.displayPrompt(
            {
               text: Alfresco.util.message("message.failure", this.name)
            });
         }
      },
      
      /**
       * Perform URL navigation back to user profile main page
       * 
       * @method navigateToProfile
       */
      navigateToProfile: function UP_navigateToProfile()
      {
         var pageIndex = document.location.href.lastIndexOf('/');
         document.location.href = document.location.href.substring(0, pageIndex + 1) + "user-dropbox";
      }
   });
})();
