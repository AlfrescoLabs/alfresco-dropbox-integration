{ "authenticated": ${authenticated?string}, "auth_url": "${auth_url!""}"<#if authenticated >, 
  "display_name": "${display_name}",
  "quota": ${quota?string.computer},
  "quota_normal": ${quota_normal?string.computer},
  "quota_shared": ${quota_shared?string.computer},
  "email": "${email}"
</#if> }
