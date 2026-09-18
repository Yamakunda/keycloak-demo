package com.snp.keycloak.qrlogin;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class QrLoginCombinedAuthenticator extends UsernamePasswordForm {

    static final String SESSION_ID_PARAM = "qr_session_id";

    // login.ftl chỉ vẽ khối QR khi thấy attribute này, nên cùng một theme vẫn phục vụ được
    // cả flow có QR lẫn flow Username Password Form gốc.
    private static final String SHOW_QR_ATTRIBUTE = "showQrLogin";

    // UsernamePasswordForm.challenge() gọi thẳng createLoginUsernamePassword(), không đi qua
    // createLoginForm() — nên attribute phải được set ở đây mới tới được template.
    @Override
    protected Response challenge(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        LoginFormsProvider form = context.form().setAttribute(SHOW_QR_ATTRIBUTE, true);
        if (formData.size() > 0) {
            form.setFormData(formData);
        }
        return form.createLoginUsernamePassword();
    }

    @Override
    protected Response createLoginForm(LoginFormsProvider form) {
        return super.createLoginForm(form.setAttribute(SHOW_QR_ATTRIBUTE, true));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String qrSessionId = formData.getFirst(SESSION_ID_PARAM);

        if (qrSessionId != null && !qrSessionId.isBlank()) {
            handleQrAction(context, qrSessionId);
            return;
        }

        super.action(context);
    }

    private void handleQrAction(AuthenticationFlowContext context, String qrSessionId) {
        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(qrSessionId);
        if (qrSession == null || !"approved".equals(qrSession.status)) {
            context.failureChallenge(
                    org.keycloak.authentication.AuthenticationFlowError.INVALID_CREDENTIALS,
                    challenge(context, "QR session has not been confirmed or has expired", null));
            return;
        }

        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        UserModel user = session.users().getUserById(realm, qrSession.userId);
        store.remove(qrSessionId);

        if (user == null) {
            context.failureChallenge(
                    org.keycloak.authentication.AuthenticationFlowError.INVALID_CREDENTIALS,
                    challenge(context, "Could not find the user who confirmed the QR login", null));
            return;
        }

        context.setUser(user);
        context.success();
    }
}
