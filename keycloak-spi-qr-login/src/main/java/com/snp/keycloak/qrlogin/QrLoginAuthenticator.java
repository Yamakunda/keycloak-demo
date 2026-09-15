package com.snp.keycloak.qrlogin;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Bước xác thực "Đăng nhập bằng QR" — đặt ALTERNATIVE song song với Username Password Form
 * trong Browser Flow. Khi được chọn, render trang riêng (qr-login.ftl) hiện mã QR; trang đó
 * tự poll trạng thái qua REST API /realms/{realm}/qr-login/* (QrLoginResourceProvider) và khi
 * thấy "approved" thì tự submit lại action URL của chính authenticator này (kèm session_id)
 * để hoàn tất bước xác thực ngay trong flow chuẩn — Keycloak sẽ tự tạo session/issue token
 * như mọi authenticator khác, không cần TokenIssuer thủ công.
 */
public class QrLoginAuthenticator implements Authenticator {

    static final String SESSION_ID_PARAM = "qr_session_id";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        QrLoginSessionStore.Session qrSession = QrLoginSessionStore.getInstance().createSession();
        var form = context.form()
                .setAttribute("qrSessionId", qrSession.id)
                .setAttribute("qrExpiresIn", QrLoginSessionStore.TTL_SECONDS);
        context.challenge(form.createForm("qr-login.ftl"));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        String sessionId = context.getHttpRequest().getDecodedFormParameters().getFirst(SESSION_ID_PARAM);
        if (sessionId == null || sessionId.isBlank()) {
            context.challenge(context.form()
                    .setError("Thiếu session_id")
                    .createForm("qr-login.ftl"));
            return;
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null || !"approved".equals(qrSession.status)) {
            context.challenge(context.form()
                    .setAttribute("qrSessionId", sessionId)
                    .setAttribute("qrExpiresIn", QrLoginSessionStore.TTL_SECONDS)
                    .setError("Phiên QR chưa được xác nhận hoặc đã hết hạn")
                    .createForm("qr-login.ftl"));
            return;
        }

        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        UserModel user = session.users().getUserById(realm, qrSession.userId);
        store.remove(sessionId);

        if (user == null) {
            context.challenge(context.form()
                    .setError("Không tìm thấy người dùng đã xác nhận QR")
                    .createForm("qr-login.ftl"));
            return;
        }

        context.setUser(user);
        context.success();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
