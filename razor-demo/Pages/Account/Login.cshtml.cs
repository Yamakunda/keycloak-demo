using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.OpenIdConnect;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;

namespace RazorKeycloakDemo.Pages.Account;

public class LoginModel : PageModel
{
    // Challenge the OIDC scheme → browser is redirected to the Keycloak login page.
    public IActionResult OnGet(string? returnUrl = null)
    {
        var target = Url.IsLocalUrl(returnUrl) ? returnUrl : "/";
        return Challenge(
            new AuthenticationProperties { RedirectUri = target },
            OpenIdConnectDefaults.AuthenticationScheme);
    }
}
