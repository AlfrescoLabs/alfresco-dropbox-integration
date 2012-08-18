/**
 * User Profile Component - User Dropbox GET method
 */

function main()
{
   // Call the repo to retrieve dropbox properties
   
   var result = remote.call("/dropbox/account");
   if (result.status == 200)
   {
      var dropbox = eval('(' + result + ')');
      
      if (dropbox.authenticated){
    	  model.display_name = dropbox.display_name;
    	  model.quota = dropbox.quota;
    	  model.quota_string =  formatSize(dropbox.quota);
    	  model.quota_normal = dropbox.quota_normal;
    	  model.quota_normal_string = formatSize(dropbox.quota_normal);
    	  model.quota_shared = dropbox.quota_shared;
    	  model.quota_shared_string = formatSize(dropbox.quota_shared);
    	  model.email = dropbox.email;
      }
      model.authenticated = dropbox.authenticated;      
   }
}

function formatSize(q)
{
   var size;
   var MEGABYTE = 1024 * 1024;
   var GIGABYTE = 1024 * 1024 * 1024;

   if (q >= GIGABYTE) {
      if (q%GIGABYTE != 0) {
         size = Math.round(q / GIGABYTE * 100)/100;
      } else {
         size = q / GIGABYTE;
      }
          size = size + " GB";
   } else {
      if (q%MEGABYTE != 0) {
         size = Math.round(q / MEGABYTE * 100)/100;
      } else {
        size = q / MEGABYTE;
      }
      size = size + " MB";
   }

   return size;
}


main();
