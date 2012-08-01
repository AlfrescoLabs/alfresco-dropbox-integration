<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
   <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
   <style type="text/css">
body
{
   font: 13px/1.231 arial,helvetica,clean,sans-serif;
   color: #000000;
}

body,div,p
{
   margin: 0;
   padding: 0;
}

div
{
	text-align: center;
}

ul
{
   text-align: left;
}

li
{
   padding: 0.2em;
}

div.panel
{
   display: inline-block;
}
   </style>
   <title>Alfresco Share &raquo; Link Dropbox Account</title>
</head>
<body>
   <div>
      <br/>
      <img src="/share/themes/default/images/app-logo.png">
      <br/>
      <br/>
      <#if success>
       <script type="text/javascript">
		   self.close();
	   </script>
      <p style="font-size:150%">Link to Dropbox Account Complete.</p>
      <br/>
      <p>If the page does not close, clink the link below</p>
      <#else>
      <p style="font-size:150%">Failed to link Dropbox Account.  Please Try again at a later time.  If the issue continues, please contact your System Administrator.</p>
      </#if>
      <br/>
      <a href="self.close();">Close page</a>
      <br/>
      <br/>
      <br/>
      <a href="http://www.alfresco.com">Alfresco Software</a> Inc. &copy; 2005-2012 All rights reserved.
   </div>
</div>
</body>
</html>