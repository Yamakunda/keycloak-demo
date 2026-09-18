package com.snp.keycloak.qrlogin;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

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
                    .setError("Missing session_id")
                    .createForm("qr-login.ftl"));
            return;
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null || !"approved".equals(qrSession.status)) {
            context.challenge(context.form()
                    .setAttribute("qrSessionId", sessionId)
                    .setAttribute("qrExpiresIn", QrLoginSessionStore.TTL_SECONDS)
                    .setError("QR session has not been confirmed or has expired")
                    .createForm("qr-login.ftl"));
            return;
        }

        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        UserModel user = session.users().getUserById(realm, qrSession.userId);
        store.remove(sessionId);

        if (user == null) {
            context.challenge(context.form()
                    .setError("Could not find the user who confirmed the QR login")
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
