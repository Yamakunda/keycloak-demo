<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<#import "social-providers.ftl" as identityProviders>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>

    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <#assign showQr = showQrLogin?? && showQrLogin>
        <div id="qr-combined-layout" class="<#if !showQr>qr-single-col</#if>">
          <div id="qr-combined-password-col">
            <div id="kc-form">
              <div id="kc-form-wrapper">
                <#if realm.password>
                    <form id="kc-form-login" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" novalidate="novalidate">
                        <#if !usernameHidden??>
                            <#assign label>
                                <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                            </#assign>
                            <@field.input name="username" label=label error=kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc autofocus=true autocomplete="username" value=login.username!'' />
                            <@field.password name="password" label=msg("password") error="" forgotPassword=realm.resetPasswordAllowed autofocus=usernameHidden?? autocomplete="current-password" />
                        <#else>
                            <@field.password name="password" label=msg("password") forgotPassword=realm.resetPasswordAllowed autofocus=usernameHidden?? autocomplete="current-password" />
                        </#if>

                        <div class="${properties.kcFormGroupClass!}">
                            <#if realm.rememberMe && !usernameHidden??>
                                <@field.checkbox name="rememberMe" label=msg("rememberMe") value=login.rememberMe?? />
                            </#if>
                        </div>

                        <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                        <@buttons.loginButton />
                    </form>
                </#if>
              </div>
            </div>
          </div>

          <#if showQr>
          <div id="qr-combined-divider"></div>

          <div id="qr-combined-qr-col">
            <div id="qr-canvas-wrapper">
                <canvas id="qr-canvas"></canvas>
            </div>
            <p id="qr-status">${msg("qrCombinedScanHint")}</p>
            <p id="qr-countdown">&nbsp;</p>

            <form id="kc-qr-form" action="${url.loginAction}" method="post" style="display:none;">
                <input type="hidden" id="qr-session-id-input" name="qr_session_id" value=""/>
            </form>
          </div>
          </#if>
        </div>

        <#if showQr>
        <script src="${url.resourcesPath}/js/qrcode.min.js"></script>
        <script>
            (function () {
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
                var countdownTimer = null;
                // Tăng mỗi lần sinh QR mới — long-poll của phiên cũ đang treo sẽ tự bỏ kết quả
                // khi thấy generation đã đổi, tránh ghi đè trạng thái của QR mới.
                var generation = 0;

                function formatRemaining(seconds) {
                    var m = Math.floor(seconds / 60);
                    var s = seconds % 60;
                    return m + ":" + (s < 10 ? "0" : "") + s;
                }

                function drawQr(payloadSessionId) {
                    var qrPayload = JSON.stringify({ apiUrl: realmBaseUrl, sessionId: payloadSessionId });
                    if (window.QRCode && canvas) {
                        QRCode.toCanvas(canvas, qrPayload, { width: 200, margin: 1 });
                    } else if (canvas) {
                        statusEl.textContent = "${msg("qrCombinedLibraryError")}";
                    }
                }

                function updateCountdownText() {
                    countdownEl.textContent = "${msg("qrCombinedExpiresIn")}" + " " + formatRemaining(remainingSeconds);
                }

                function startCountdown() {
                    if (countdownTimer) clearInterval(countdownTimer);
                    remainingSeconds = expiresIn;
                    updateCountdownText();
                    countdownTimer = setInterval(function () {
                        remainingSeconds -= 1;
                        if (remainingSeconds <= 0) {
                            clearInterval(countdownTimer);
                            refreshSession();
                            return;
                        }
                        updateCountdownText();
                    }, 1000);
                }

                function refreshSession() {
                    if (countdownTimer) clearInterval(countdownTimer);
                    generation += 1;
                    var myGeneration = generation;

                    statusEl.textContent = "${msg("qrCombinedRegenerating")}";
                    fetch(qrLoginBase + "/start", { method: "POST" })
                        .then(function (r) { return r.json(); })
                        .then(function (data) {
                            if (myGeneration !== generation) return;
                            sessionId = data.session_id;
                            expiresIn = data.expires_in;
                            sessionIdInput.value = sessionId;
                            drawQr(sessionId);
                            statusEl.textContent = "${msg("qrCombinedScanHint")}";
                            startCountdown();
                            waitForStatusChange(myGeneration, "pending");
                        })
                        .catch(function () {
                            if (myGeneration !== generation) return;
                            statusEl.textContent = "${msg("qrCombinedStartError")}";
                        });
                }

                // Long polling: server giữ request treo tới khi status khác knownStatus hoặc
                // hết timeout, nên không cần gọi lặp mỗi vài giây.
                function waitForStatusChange(myGeneration, knownStatus) {
                    if (myGeneration !== generation) return;

                    fetch(qrLoginBase + "/check", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ session_id: sessionId, known_status: knownStatus })
                    })
                        .then(function (r) { return r.json(); })
                        .then(function (data) {
                            if (myGeneration !== generation) return;

                            if (data.status === "approved") {
                                if (countdownTimer) clearInterval(countdownTimer);
                                generation += 1;
                                statusEl.textContent = "${msg("qrCombinedApproved")}";
                                document.getElementById("kc-qr-form").submit();
                                return;
                            }

                            if (data.status === "expired") {
                                refreshSession();
                                return;
                            }

                            if (data.status === "scanned") {
                                statusEl.textContent = "${msg("qrCombinedScanned")}";
                            } else {
                                statusEl.textContent = "${msg("qrCombinedScanHint")}";
                            }
                            waitForStatusChange(myGeneration, data.status);
                        })
                        .catch(function () {
                            if (myGeneration !== generation) return;
                            setTimeout(function () {
                                waitForStatusChange(myGeneration, knownStatus);
                            }, 3000);
                        });
                }

                refreshSession();
            })();
        </script>
        </#if>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container" class="${properties.kcLoginFooterBand!}">
                <div id="kc-registration" class="${properties.kcLoginFooterBandItem!}">
                    <span>${msg("noAccount")} <a href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    <#elseif section = "socialProviders" >
        <#if realm.password && social.providers?? && social.providers?has_content>
            <@identityProviders.show social=social/>
        </#if>
    </#if>

</@layout.registrationLayout>
