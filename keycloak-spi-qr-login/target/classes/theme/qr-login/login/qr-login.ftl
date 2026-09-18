<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        QR Login
    <#elseif section = "form">
        <div id="qr-login-container" style="text-align:center;">
            <p>Open the app on your already logged-in phone and choose <b>Scan QR Code</b> to sign in on this device.</p>
            <div id="qr-canvas-wrapper" style="margin: 24px auto;">
                <canvas id="qr-canvas"></canvas>
            </div>
            <p id="qr-status" class="kc-feedback-text">Waiting for scan…</p>
            <p id="qr-countdown" class="kc-feedback-text"></p>

            <form id="kc-qr-form" action="${url.loginAction}" method="post" style="display:none;">
                <input type="hidden" id="qr-session-id-input" name="qr_session_id" value="${qrSessionId}"/>
            </form>
        </div>

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

                var sessionId = "${qrSessionId}";
                var expiresIn = ${qrExpiresIn?c};
                var remainingSeconds = expiresIn;
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
                        QRCode.toCanvas(canvas, qrPayload, { width: 240, margin: 1 });
                    } else if (canvas) {
                        statusEl.textContent = "Failed to load the QR code library.";
                    }
                }

                function startCountdown() {
                    if (countdownTimer) clearInterval(countdownTimer);
                    remainingSeconds = expiresIn;
                    countdownEl.textContent = "Expires in " + formatRemaining(remainingSeconds);
                    countdownTimer = setInterval(function () {
                        remainingSeconds -= 1;
                        if (remainingSeconds <= 0) {
                            clearInterval(countdownTimer);
                            refreshSession();
                            return;
                        }
                        countdownEl.textContent = "Expires in " + formatRemaining(remainingSeconds);
                    }, 1000);
                }

                function refreshSession() {
                    if (countdownTimer) clearInterval(countdownTimer);
                    generation += 1;
                    var myGeneration = generation;

                    statusEl.textContent = "QR code expired — generating a new one…";
                    fetch(qrLoginBase + "/start", { method: "POST" })
                        .then(function (r) { return r.json(); })
                        .then(function (data) {
                            if (myGeneration !== generation) return;
                            sessionId = data.session_id;
                            expiresIn = data.expires_in;
                            sessionIdInput.value = sessionId;
                            drawQr(sessionId);
                            statusEl.textContent = "Waiting for scan…";
                            startCountdown();
                            waitForStatusChange(myGeneration, "pending");
                        })
                        .catch(function () {
                            if (myGeneration !== generation) return;
                            statusEl.textContent = "Failed to generate a new QR code — please reload the page.";
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
                                statusEl.textContent = "Confirmed! Signing in…";
                                document.getElementById("kc-qr-form").submit();
                                return;
                            }

                            if (data.status === "expired") {
                                refreshSession();
                                return;
                            }

                            if (data.status === "scanned") {
                                statusEl.textContent = "Scanned — confirm on your phone";
                            } else {
                                statusEl.textContent = "Waiting for scan…";
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

                generation = 1;
                drawQr(sessionId);
                startCountdown();
                waitForStatusChange(generation, "pending");
            })();
        </script>
    <#elseif section = "info">
        <div id="kc-registration-container">
            <div id="kc-registration">
                <span><a href="${url.loginRestartFlowUrl}">Back to password login</a></span>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
