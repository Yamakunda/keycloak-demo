<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        Đăng nhập bằng QR
    <#elseif section = "form">
        <div id="qr-login-container" style="text-align:center;">
            <p>Mở app trên điện thoại đã đăng nhập, chọn <b>Quét mã QR</b> để đăng nhập thiết bị này.</p>
            <div id="qr-canvas-wrapper" style="margin: 24px auto;">
                <canvas id="qr-canvas"></canvas>
            </div>
            <p id="qr-status" class="kc-feedback-text">Đang chờ quét mã…</p>

            <form id="kc-qr-form" action="${url.loginAction}" method="post" style="display:none;">
                <input type="hidden" id="qr-session-id-input" name="qr_session_id" value="${qrSessionId}"/>
            </form>
        </div>

        <script src="${url.resourcesPath}/js/qrcode.min.js"></script>
        <script>
            (function () {
                var sessionId = "${qrSessionId}";
                var expiresIn = ${qrExpiresIn?c};
                var pollIntervalMs = 2000;
                var loginActionUrl = "${url.loginAction}";
                var realmBaseUrl = loginActionUrl.substring(0, loginActionUrl.indexOf("/login-actions/"));
                var qrLoginBase = realmBaseUrl + "/qr-login";

                var canvas = document.getElementById("qr-canvas");
                var statusEl = document.getElementById("qr-status");
                var qrPayload = JSON.stringify({ apiUrl: realmBaseUrl, sessionId: sessionId });

                if (window.QRCode && canvas) {
                    QRCode.toCanvas(canvas, qrPayload, { width: 240, margin: 1 });
                } else if (canvas) {
                    statusEl.textContent = "Không tải được thư viện tạo mã QR.";
                }

                var elapsed = 0;
                var timer = setInterval(function () {
                    elapsed += pollIntervalMs;
                    if (elapsed >= expiresIn * 1000) {
                        clearInterval(timer);
                        statusEl.textContent = "Mã QR đã hết hạn — tải lại trang để lấy mã mới.";
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
                                clearInterval(timer);
                                statusEl.textContent = "Đã xác nhận! Đang đăng nhập…";
                                document.getElementById("kc-qr-form").submit();
                            } else if (data.status === "scanned") {
                                statusEl.textContent = "Đã quét — hãy xác nhận trên điện thoại";
                            } else {
                                statusEl.textContent = "Đang chờ quét mã…";
                            }
                        })
                        .catch(function () {});
                }, pollIntervalMs);
            })();
        </script>
    <#elseif section = "info">
        <div id="kc-registration-container">
            <div id="kc-registration">
                <span><a href="${url.loginRestartFlowUrl}">Quay lại đăng nhập bằng mật khẩu</a></span>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
