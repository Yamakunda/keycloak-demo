using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authentication.OpenIdConnect;
using Microsoft.IdentityModel.Protocols.OpenIdConnect;

// Nạp .env vào process env trước khi builder đọc config (env vars → section Keycloak).
// Trong Docker file .env không được copy vào image; biến do compose env_file cấp.
DotNetEnv.Env.TraversePath().Load();

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddRazorPages();

var keycloak = builder.Configuration.GetSection("Keycloak");
string Required(string key) => keycloak[key]
    ?? throw new InvalidOperationException(
        $"Keycloak:{key} is not set — copy .env.example thành .env rồi điền giá trị (key Keycloak__{key}).");
var authority = $"{Required("ServerUrl").TrimEnd('/')}/realms/{Required("Realm")}";
var clientId = Required("ClientId");
var clientSecret = Required("ClientSecret");

builder.Services
    .AddAuthentication(options =>
    {
        options.DefaultScheme = CookieAuthenticationDefaults.AuthenticationScheme;
        options.DefaultChallengeScheme = OpenIdConnectDefaults.AuthenticationScheme;
    })
    .AddCookie(options =>
    {
        options.Cookie.Name = "razor-demo-auth";
        options.Cookie.HttpOnly = true;
        options.Cookie.SameSite = SameSiteMode.Lax;
        options.Cookie.SecurePolicy = CookieSecurePolicy.SameAsRequest;
        options.ExpireTimeSpan = TimeSpan.FromMinutes(30);
        options.SlidingExpiration = true;
    })
    .AddOpenIdConnect(options =>
    {
        options.Authority = authority;
        options.ClientId = clientId;
        options.ClientSecret = clientSecret;

        options.ResponseType = OpenIdConnectResponseType.Code; // Authorization Code Flow
        options.UsePkce = true;

        options.Scope.Clear();
        options.Scope.Add("openid");
        options.Scope.Add("profile");
        options.Scope.Add("email");

        options.SaveTokens = true; // keep id/access/refresh token in the auth session
        options.GetClaimsFromUserInfoEndpoint = true;
        options.MapInboundClaims = false; // keep raw OIDC claim names (sub, email, name…)

        options.TokenValidationParameters.NameClaimType = "preferred_username";
        options.TokenValidationParameters.RoleClaimType = "roles";

        // Dev only: Keycloak runs on plain http://localhost:8080
        options.RequireHttpsMetadata = false;

        options.Events = new OpenIdConnectEvents
        {
            OnTokenResponseReceived = ctx =>
            {
                var log = ctx.HttpContext.RequestServices
                    .GetRequiredService<ILoggerFactory>().CreateLogger("Keycloak.TokenFlow");
                var tr = ctx.TokenEndpointResponse;
                log.LogInformation(
                    "TOKEN ISSUED  type={TokenType} expires_in={ExpiresIn}s refresh_token={HasRefresh} scope=[{Scope}]",
                    tr.TokenType, tr.ExpiresIn, string.IsNullOrEmpty(tr.RefreshToken) ? "no" : "yes", tr.Scope);
                return Task.CompletedTask;
            },
            OnTokenValidated = ctx =>
            {
                var log = ctx.HttpContext.RequestServices
                    .GetRequiredService<ILoggerFactory>().CreateLogger("Keycloak.TokenFlow");
                log.LogInformation(
                    "TOKEN VALIDATED  sub={Sub} user={User} email={Email}",
                    ctx.Principal?.FindFirst("sub")?.Value,
                    ctx.Principal?.Identity?.Name,
                    ctx.Principal?.FindFirst("email")?.Value);
                return Task.CompletedTask;
            },
            OnRedirectToIdentityProviderForSignOut = ctx =>
            {
                var log = ctx.HttpContext.RequestServices
                    .GetRequiredService<ILoggerFactory>().CreateLogger("Keycloak.TokenFlow");
                log.LogInformation("SINGLE LOGOUT  redirecting to Keycloak end-session endpoint");
                return Task.CompletedTask;
            }
        };
    });

builder.Services.AddAuthorization();

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error");
}

app.UseStaticFiles();
app.UseRouting();
app.UseAuthentication();
app.UseAuthorization();
app.MapRazorPages();

app.Run();
