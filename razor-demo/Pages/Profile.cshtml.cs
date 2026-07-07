using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc.RazorPages;

namespace RazorKeycloakDemo.Pages;

[Authorize]
public class ProfileModel : PageModel
{
    public List<(string Name, string Value)> Tokens { get; } = new();

    public async Task OnGetAsync()
    {
        foreach (var name in new[] { "access_token", "id_token", "refresh_token", "expires_at" })
        {
            var value = await HttpContext.GetTokenAsync(name);
            if (!string.IsNullOrEmpty(value))
            {
                // Truncate raw JWTs so the page stays readable
                Tokens.Add((name, value.Length > 80 ? value[..80] + "…" : value));
            }
        }
    }
}
