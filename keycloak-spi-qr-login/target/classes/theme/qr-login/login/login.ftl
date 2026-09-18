<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div id="qr-combined-layout">
          <div id="qr-combined-password-col">
            <#if realm.password>
                <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                    <#if !usernameHidden??>
                        <div class="${properties.kcFormGroupClass!}">
                            <label for="username" class="${properties.kcLabelClass!}"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>

                            <input tabindex="2" id="username" class="${properties.kcInputClass!}" name="username" value="${(login.username!'')}"  type="text" autofocus autocomplete="username"
                                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                                   dir="ltr"
                            />

                            <#if messagesPerField.existsError('username','password')>
                                <span id="input-error" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                        ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                                </span>
                            </#if>

                        </div>
                    </#if>

                    <div class="${properties.kcFormGroupClass!}">
                        <label for="password" class="${properties.kcLabelClass!}">${msg("password")}</label>

                        <div class="${properties.kcInputGroup!}" dir="ltr">
                            <input tabindex="3" id="password" class="${properties.kcInputClass!}" name="password" type="password" autocomplete="current-password"
                                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                            />
                            <button class="${properties.kcFormPasswordVisibilityButtonClass!}" type="button" aria-label="${msg("showPassword")}"
                                    aria-controls="password" data-password-toggle tabindex="4"
                                    data-icon-show="${properties.kcFormPasswordVisibilityIconShow!}" data-icon-hide="${properties.kcFormPasswordVisibilityIconHide!}"
                                    data-label-show="${msg('showPassword')}" data-label-hide="${msg('hidePassword')}">
                                <i class="${properties.kcFormPasswordVisibilityIconShow!}" aria-hidden="true"></i>
                            </button>
                        </div>

                        <#if usernameHidden?? && messagesPerField.existsError('username','password')>
                            <span id="input-error" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                            </span>
                        </#if>

                    </div>

                    <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                        <div id="kc-form-options">
                            <#if realm.rememberMe && !usernameHidden??>
                                <div class="checkbox">
                                    <label>
                                        <#if login.rememberMe??>
                                            <input tabindex="5" id="rememberMe" name="rememberMe" type="checkbox" checked> ${msg("rememberMe")}
                                        <#else>
                                            <input tabindex="5" id="rememberMe" name="rememberMe" type="checkbox"> ${msg("rememberMe")}
                                        </#if>
                                    </label>
                                </div>
                            </#if>
                            </div>
                            <div class="${properties.kcFormOptionsWrapperClass!}">
                                <#if realm.resetPasswordAllowed>
                                    <span><a tabindex="6" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a></span>
                                </#if>
                            </div>

                      </div>

                      <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                          <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                          <input tabindex="7" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
                      </div>
                </form>
            </#if>
          </div>

          <div id="qr-combined-divider"></div>

          <div id="qr-combined-qr-col">
            <div id="qr-canvas-wrapper">
                <canvas id="qr-canvas"></canvas>
            </div>
            <p id="qr-status" class="kc-feedback-text">${msg("qrCombinedScanHint")}</p>
            <p id="qr-countdown" class="kc-feedback-text"></p>

            <form id="kc-qr-form" action="${url.loginAction}" method="post" style="display:none;">
                <input type="hidden" id="qr-session-id-input" name="qr_session_id" value=""/>
                <input type="hidden" name="qr_combined_login" value="true"/>
            </form>
          </div>
        </div>

        <script src="${url.resourcesPath}/js/qrcode.min.js"></script>
        <script>
            (function () {
                var pollIntervalMs = 2000;
                var loginActionUrl = "${url.loginAction}";
                var realmBaseUrl = loginActionUrl.substring(0, loginActionUrl.indexOf("/login-actions/"));
                var qrLoginBase = realmBaseUrl + "/qr-login";

                var canvas = document.getElementById("qr-canvas");
                var statusEl = document.getElementById("qr-status");
                var countdownEl = document.getElementById("qr-countdown");
                var sessionIdInput = document.getElementById("qr-session-id-input");

                var sessionId = null;
                var expiresIn = 0;
                var remainingSeconds = 0;
                var pollTimer = null;
                var countdownTimer = null;

                function formatRemaining(seconds) {
                    var m = Math.floor(seconds / 60);
                    var s = seconds % 60;
                    return m + ":" + (s < 10 ? "0" : "") + s;
                }

                function drawQr(payloadSessionId) {
                    var qrPayload = JSON.stringify({ apiUrl: realmBaseUrl, sessionId: payloadSessionId });
                    if (window.QRCode && canvas) {
                        QRCode.toCanvas(canvas, qrPayload, { width: 220, margin: 1 });
                    } else if (canvas) {
                        statusEl.textContent = "${msg("qrCombinedLibraryError")}";
                    }
                }

                function stopTimers() {
                    if (pollTimer) clearInterval(pollTimer);
                    if (countdownTimer) clearInterval(countdownTimer);
                }

                function startCountdown() {
                    if (countdownTimer) clearInterval(countdownTimer);
                    remainingSeconds = expiresIn;
                    countdownEl.textContent = "${msg("qrCombinedExpiresIn")}" + formatRemaining(remainingSeconds);
                    countdownTimer = setInterval(function () {
                        remainingSeconds -= 1;
                        if (remainingSeconds <= 0) {
                            clearInterval(countdownTimer);
                            return;
                        }
                        countdownEl.textContent = "${msg("qrCombinedExpiresIn")}" + formatRemaining(remainingSeconds);
                    }, 1000);
                }

                function refreshSession() {
                    stopTimers();
                    statusEl.textContent = "${msg("qrCombinedRegenerating")}";
                    fetch(qrLoginBase + "/start", { method: "POST" })
                        .then(function (r) { return r.json(); })
                        .then(function (data) {
                            sessionId = data.session_id;
                            expiresIn = data.expires_in;
                            sessionIdInput.value = sessionId;
                            drawQr(sessionId);
                            statusEl.textContent = "${msg("qrCombinedScanHint")}";
                            startCountdown();
                            startPolling();
                        })
                        .catch(function () {
                            statusEl.textContent = "${msg("qrCombinedStartError")}";
                        });
                }

                function startPolling() {
                    if (pollTimer) clearInterval(pollTimer);
                    var elapsed = 0;
                    pollTimer = setInterval(function () {
                        elapsed += pollIntervalMs;
                        if (elapsed >= expiresIn * 1000) {
                            clearInterval(pollTimer);
                            refreshSession();
                            return;
                        }

                        fetch(qrLoginBase + "/check", {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({ session_id: sessionId })
                        })
                            .then(function (r) { return r.json(); })
                            .then(function (data) {
                                if (data.status === "approved") {
                                    stopTimers();
                                    statusEl.textContent = "${msg("qrCombinedApproved")}";
                                    document.getElementById("kc-qr-form").submit();
                                } else if (data.status === "scanned") {
                                    statusEl.textContent = "${msg("qrCombinedScanned")}";
                                } else if (data.status === "expired") {
                                    clearInterval(pollTimer);
                                    refreshSession();
                                } else {
                                    statusEl.textContent = "${msg("qrCombinedScanHint")}";
                                }
                            })
                            .catch(function () {});
                    }, pollIntervalMs);
                }

                refreshSession();
            })();
        </script>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container">
                <div id="kc-registration">
                    <span>${msg("noAccount")} <a tabindex="8"
                                                 href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    <#elseif section = "socialProviders" >
        <#if realm.password && social?? && social.providers?has_content>
            <div id="kc-social-providers" class="${properties.kcFormSocialAccountSectionClass!}">
                <hr/>
                <h2>${msg("identity-provider-login-label")}</h2>

                <ul class="${properties.kcFormSocialAccountListClass!} <#if social.providers?size gt 3>${properties.kcFormSocialAccountListGridClass!}</#if>">
                    <#list social.providers as p>
                        <li>
                            <a id="social-${p.alias}" class="${properties.kcFormSocialAccountListButtonClass!} <#if social.providers?size gt 3>${properties.kcFormSocialAccountGridItem!}</#if>"
                                    type="button" href="${p.loginUrl}">
                                <#if p.iconClasses?has_content>
                                    <i class="${properties.kcCommonLogoIdP!} ${p.iconClasses!}" aria-hidden="true"></i>
                                    <span class="${properties.kcFormSocialAccountNameClass!} kc-social-icon-text">${p.displayName!}</span>
                                <#else>
                                    <span class="${properties.kcFormSocialAccountNameClass!}">${p.displayName!}</span>
                                </#if>
                            </a>
                        </li>
                    </#list>
                </ul>
            </div>
        </#if>
    </#if>

</@layout.registrationLayout>
