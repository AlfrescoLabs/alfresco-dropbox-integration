<#assign el=args.htmlid?html>
<script type="text/javascript">//<![CDATA[
   new Alfresco.UserDropbox("${el}");
//]]></script>
<div id="${el}-body" class="dropbox">
<div class="header-bar">Dropbox Account Details</div>
<#if authenticated >
   
      <div class="row">
	Account Name: ${display_name} <br />
	Account Email: ${email} <br />
	Quota: ${quota_string} <br />
	Quota Used: ${quota_normal_string} <br />
	Quota Shared: ${quota_shared_string} <br />
      </div>
      <hr/>
      <div class="row">
      <form id="${el}-form" action="${url.context}/service/components/profile/user-dropbox/delink" method="post">
	<div class="buttons">
	   <button id="${el}-dropbox-button" name="save">Delink Account</button>	
        </div>
      </form>
      </div>
<#else>
        <div class="row">
        <div class="buttons">
           <button id="${el}-dropbox-link" name="save">Link Account</button>
        </div>
        </div>
</#if>
</div>
