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
 * 
 * @author Jared Ottley
 */
(function() {

	var $html = Alfresco.util.encodeHTML;

			/**
			 * Send to Dropbox.
			 * 
			 * @method onDropboxActionSendto
			 * @param asset
			 *            {object} Object literal representing the file or
			 *            folder to be sent to Dropbox
			 */
			YAHOO.Bubbling
					.fire(
							"registerAction",
							{
								actionName : "onDropboxActionSendTo",
								fn : function dlA_onDropboxActionSendto(record) {

									var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;

									var fnShowLoadingMessage = function Dropbox_fnShowLoadingMessage() {
										// Check the timer still exists. This is
										// to prevent IE firing the event after
										// we cancelled it. Which is "useful".
										if (timerShowLoadingMessage) {
											loadingMessage = Alfresco.util.PopupManager
													.displayMessage( {
														displayTime : 0,
														text : '<span class="wait">' + $html(this
																.msg("dropbox.actions.document.message.sendto")) + '</span>',
														noEscape : true
													});

											if (YAHOO.env.ua.ie > 0) {
												this.loadingMessageShowing = true;
											} else {
												loadingMessage.showEvent
														.subscribe(
																function() {
																	this.loadingMessageShowing = true;
																}, this, true);
											}
										}
									};

									var destroyLoaderMessage = function Dropbox_destroyLoaderMessage() {
										if (timerShowLoadingMessage) {
											// Stop the "slow loading" timed
											// function
											timerShowLoadingMessage.cancel();
											timerShowLoadingMessage = null;
										}

										if (loadingMessage) {
											if (loadingMessageShowing) {
												// Safe to destroy
												loadingMessage.destroy();
												loadingMessage = null;
											} else {
												// Wait and try again later.
												// Scope doesn't get set
												// correctly with "this"
												YAHOO.lang.later(100, me,
														destroyLoaderMessage);
											}
										}
									};

									destroyLoaderMessage();
									timerShowLoadingMessage = YAHOO.lang.later(
											0, this, fnShowLoadingMessage);

									var success = {
										fn : function(response) {

											if (response.json.authenticated) {

												var successHandler = {
													fn : function(response) {
														destroyLoaderMessage();
														Alfresco.util.PopupManager
																.displayMessage( {
																	text : this
																			.msg("dropbox.actions.document.sendto.success")
																});
														// Updating the Doclist
														this._updateDocList
																.call(this);
													},
													scope : this
												}

												var failureHandler = {
													fn : function() {
														destroyLoaderMessage();
														Alfresco.util.PopupManager
																.displayMessage( {
																	text : this
																			.msg("dropbox.actions.document.sendto.failure")
																});
														this._updateDocList
																.call(this);
													},
													scope : this
												}

												var nodeRefs = [];

												if (YAHOO.lang.isArray(record)) {
													for ( var i = 0, il = record.length; i < il; i++) {
														nodeRefs
																.push(record[i].nodeRef);
													}
												} else {
													nodeRefs
															.push(record.nodeRef);
												}

												// Call repository to send the
												// document to dropbox
												Alfresco.util.Ajax
														.jsonPost( {
															url : Alfresco.constants.PROXY_URI + 'dropbox/node',
															dataObj : {
																nodeRefs : nodeRefs
															},
															successCallback : successHandler,
															failureCallback : failureHandler
														});
											} else {
												loadingMessageShowing = true;
												destroyLoaderMessage();
												
												//basic and ugly
												DBOAuthwindow = window.open(response.json.auth_url, "DBOAuthwindow", "location=1,status=1,scrollbars=1,width=960,height=900");
												DBOAuthwindow.moveTo(0,0);

											}

										},
										scope : this
									}

									var failure = {
										fn : function(response) {

											destroyLoaderMessage();
											Alfresco.util.PopupManager
													.displayMessage( {
														text : this
																.msg("dropbox.actions.document.sendto.authentication.failure")
													});

										},
										scope : this
									}

									Alfresco.util.Ajax
											.jsonGet( {
												url : Alfresco.constants.PROXY_URI + 'dropbox/account?callback=' + Alfresco.constants.PROXY_URI,
												dataObj : {},
												successCallback : success,
												failureCallback : failure
											});

								}
							}),

			/**
			 * Get from Dropbox.
			 * 
			 * @method onDropboxActionGetFrom
			 * @param asset
			 *            {object} Object literal representing the file or
			 *            folder to be retrieved from Dropbox
			 */
			YAHOO.Bubbling
					.fire(
							"registerAction",
							{
								actionName : "onDropboxActionGetFrom",
								fn : function dlA_onDropboxActionGetFrom(record) {

									var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;

									var fnShowLoadingMessage = function Dropbox_fnShowLoadingMessage() {
										// Check the timer still exists. This is
										// to prevent IE firing the event after
										// we cancelled it. Which is "useful".
										if (timerShowLoadingMessage) {
											loadingMessage = Alfresco.util.PopupManager
													.displayMessage( {
														displayTime : 0,
														text : '<span class="wait">' + $html(this
																.msg("dropbox.actions.document.message.getfrom")) + '</span>',
														noEscape : true
													});

											if (YAHOO.env.ua.ie > 0) {
												this.loadingMessageShowing = true;
											} else {
												loadingMessage.showEvent
														.subscribe(
																function() {
																	this.loadingMessageShowing = true;
																}, this, true);
											}
										}
									};

									var destroyLoaderMessage = function Dropbox_destroyLoaderMessage() {
										if (timerShowLoadingMessage) {
											// Stop the "slow loading" timed
											// function
											timerShowLoadingMessage.cancel();
											timerShowLoadingMessage = null;
										}

										if (loadingMessage) {
											if (loadingMessageShowing) {
												// Safe to destroy
												loadingMessage.destroy();
												loadingMessage = null;
											} else {
												// Wait and try again later.
												// Scope doesn't get set
												// correctly with "this"
												YAHOO.lang.later(100, me,
														destroyLoaderMessage);
											}
										}
									};

									destroyLoaderMessage();
									timerShowLoadingMessage = YAHOO.lang.later(
											0, this, fnShowLoadingMessage);

									var successHandler = {
										fn : function(response) {
											destroyLoaderMessage();
											Alfresco.util.PopupManager
													.displayMessage( {
														text : this
																.msg("dropbox.actions.document.getfrom.success")
													});
											// Updating the Doclist
											this._updateDocList.call(this);
										},
										scope : this
									}

									var failureHandler = {
										fn : function() {
											destroyLoaderMessage();
											Alfresco.util.PopupManager
													.displayMessage( {
														text : this
																.msg("dropbox.actions.document.getfrom.failure")
													});
											this._updateDocList.call(this);
										},
										scope : this
									}
									var nodeRefs = [];

									if (YAHOO.lang.isArray(record)) {
										for ( var i = 0, il = record.length; i < il; i++) {
											nodeRefs.push(record[i].nodeRef);
										}
									} else {
										nodeRefs.push(record.nodeRef);
									}

									// Call repository to send the document to
									// dropbox
									Alfresco.util.Ajax
											.jsonGet( {
												url : Alfresco.constants.PROXY_URI + 'dropbox/node',
												dataObj : {
													nodeRefs : nodeRefs
												},
												successCallback : successHandler,
												failureCallback : failureHandler
											});
								}
							}),

			/**
			 * Remove from Dropbox.
			 * 
			 * @method onDropboxActionRemove
			 * @param asset
			 *            {object} Object literal representing the file or
			 *            folder to be removed from Dropbox
			 */
			YAHOO.Bubbling
					.fire(
							"registerAction",
							{
								actionName : "onDropboxActionRemove",
								fn : function dlA_onDropboxActionRemove(record) {

									var loadingMessage = null, timerShowLoadingMessage = null, loadingMessageShowing = false, me = this;

									var fnShowLoadingMessage = function Dropbox_fnShowLoadingMessage() {
										// Check the timer still exists. This is
										// to prevent IE firing the event after
										// we cancelled it. Which is "useful".
										if (timerShowLoadingMessage) {
											loadingMessage = Alfresco.util.PopupManager
													.displayMessage( {
														displayTime : 0,
														text : '<span class="wait">' + $html(this
																.msg("dropbox.actions.document.message.remove")) + '</span>',
														noEscape : true
													});

											if (YAHOO.env.ua.ie > 0) {
												this.loadingMessageShowing = true;
											} else {
												loadingMessage.showEvent
														.subscribe(
																function() {
																	this.loadingMessageShowing = true;
																}, this, true);
											}
										}
									};

									var destroyLoaderMessage = function Dropbox_destroyLoaderMessage() {
										if (timerShowLoadingMessage) {
											// Stop the "slow loading" timed
											// function
											timerShowLoadingMessage.cancel();
											timerShowLoadingMessage = null;
										}

										if (loadingMessage) {
											if (loadingMessageShowing) {
												// Safe to destroy
												loadingMessage.destroy();
												loadingMessage = null;
											} else {
												// Wait and try again later.
												// Scope doesn't get set
												// correctly with "this"
												YAHOO.lang.later(100, me,
														destroyLoaderMessage);
											}
										}
									};

									destroyLoaderMessage();
									timerShowLoadingMessage = YAHOO.lang.later(
											0, this, fnShowLoadingMessage);

									var successHandler = {
										fn : function(response) {
											destroyLoaderMessage();
											Alfresco.util.PopupManager
													.displayMessage( {
														text : this
																.msg("dropbox.actions.document.remove.success")
													});
											// Updating the Doclist
											this._updateDocList.call(this);
										},
										scope : this
									}

									var failureHandler = {
										fn : function() {
											destroyLoaderMessage();
											Alfresco.util.PopupManager
													.displayMessage( {
														text : this
																.msg("dropbox.actions.document.remove.failure")
													});
											this._updateDocList.call(this);
										},
										scope : this
									}

									var nodeRefs = [];

									if (YAHOO.lang.isArray(record)) {
										for ( var i = 0, il = record.length; i < il; i++) {
											nodeRefs.push(record[i].nodeRef);
										}
									} else {
										nodeRefs.push(record.nodeRef);
									}

									// Call repository to send the document to
									// dropbox
									Alfresco.util.Ajax
											.jsonPost( {
												url : Alfresco.constants.PROXY_URI + 'dropbox/removenode',
												dataObj : {
													nodeRefs : nodeRefs
												},
												successCallback : successHandler,
												failureCallback : failureHandler
											});
								}
							})

})();
