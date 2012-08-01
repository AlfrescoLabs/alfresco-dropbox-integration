/**
 * User Notifications Update method
 * 
 * @method POST
 */
 
function main()
{
  // make remote call to update user dropbox settings on person object
   
  if(url.templateArgs.action == "delink"){
   var conn = remote.connect("alfresco");
   var result = conn.post("/dropbox/account/delink/" + user.id, "{}");
   if (result.status == 200)
   {
      model.success = true;
   }
   else
   {
      model.success = false;
      status.code = result.status;
   }
  } else if (url.templateArgs.action == "link"){
	  model.success = true;
  }
}

main();
