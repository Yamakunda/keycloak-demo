package com.snp.keycloak.qrlogin;

import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.services.util.DefaultClientSessionContext;

/**
 * Sau khi phiên QR được app approve, web cần 1 access_token THẬT do Keycloak issue (không
 * phải access_token app chuyển tiếp) — dùng chính TokenManager nội bộ của Keycloak để tạo
 * user session + client session mới cho client "qr-login-confidential" rồi issue token,
 * giống hệt Keycloak tự làm khi 1 client hoàn tất Authorization Code flow bình thường.
 *
 * Lưu ý: đây là internal API (org.keycloak.protocol.oidc.*, org.keycloak.services.util.*),
 * không phải public API ổn định — có thể đổi giữa các minor version của Keycloak.
 */
final class TokenIssuer {

    private static final String QR_CLIENT_ID = "qr-login-confidential";

    static AccessTokenResponse issueFor(KeycloakSession session, String userId) {
        RealmModel realm = session.getContext().getRealm();
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            throw new IllegalStateException("Không tìm thấy user " + userId);
        }

        ClientModel client = realm.getClientByClientId(QR_CLIENT_ID);
        if (client == null) {
            throw new IllegalStateException("Client " + QR_CLIENT_ID + " không tồn tại trong realm");
        }
        // TokenManager/protocol mappers đọc session.getContext().getClient() (vd để quyết định
        // lightweight token) — request tới custom REST resource này không tự set nó như các
        // endpoint chuẩn (token endpoint, ...) đã làm, nên phải set thủ công trước khi issue token.
        session.getContext().setClient(client);

        UserSessionModel userSession = session.sessions().createUserSession(
                realm,
                user,
                user.getUsername(),
                session.getContext().getConnection() != null ? session.getContext().getConnection().getRemoteAddr() : "0.0.0.0",
                "qr-login-spi",
                false,
                null,
                null
        );

        AuthenticatedClientSessionModel clientSession = session.sessions().createClientSession(realm, client, userSession);
        clientSession.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        clientSession.setNote(OIDCLoginProtocol.ISSUER, org.keycloak.services.Urls.realmIssuer(
                session.getContext().getUri().getBaseUri(), realm.getName()));

        var clientSessionCtx = DefaultClientSessionContext.fromClientSessionAndScopeParameter(
                clientSession, "openid profile email", session);

        EventBuilder event = new EventBuilder(realm, session, session.getContext().getConnection());
        event.event(EventType.LOGIN).client(client).user(user);

        TokenManager tokenManager = new TokenManager();
        TokenManager.AccessTokenResponseBuilder responseBuilder = tokenManager.responseBuilder(
                realm, client, event, session, userSession, clientSessionCtx
        )
                .generateAccessToken()
                .generateRefreshToken()
                .generateIDToken();

        return responseBuilder.build();
    }

    private TokenIssuer() {
    }
}
