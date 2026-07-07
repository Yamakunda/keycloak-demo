using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authentication.OpenIdConnect;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;

namespace RazorKeycloakDemo.Pages.Account;

public class LogoutModel : PageModel
{
    // Single logout: clear the local cookie AND hit Keycloak's end-session
    // endpoint (the OIDC handler sends id_token_hint + post_logout_redirect_uri).
    public IActionResult OnPost()
    {
        return SignOut(
            new AuthenticationProperties { RedirectUri = "/" },
            CookieAuthenticationDefaults.AuthenticationScheme,
            OpenIdConnectDefaults.AuthenticationScheme);
    }

    public IActionResult OnGet() => RedirectToPage("/Index");
}
