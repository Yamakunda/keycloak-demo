package com.snp.keycloak.qrlogin;

import org.keycloak.TokenVerifier;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.util.DefaultClientSessionContext;

final class TokenIntrospection {

    record Result(String userId, String username) {
    }

    static Result verify(KeycloakSession session, String accessToken) throws Exception {
        RealmModel realm = session.getContext().getRealm();

        AccessToken token = TokenVerifier.create(accessToken, AccessToken.class)
                .withChecks(TokenVerifier.IS_ACTIVE, new TokenVerifier.RealmUrlCheck(
                        org.keycloak.services.Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName())
                ))
                .publicKey(session.keys().getActiveRsaKey(realm).getPublicKey())
                .verify()
                .getToken();

        if (token.getSubject() == null) {
            throw new IllegalArgumentException("Token is missing subject");
        }

        return new Result(token.getSubject(), token.getPreferredUsername());
    }

    private TokenIntrospection() {
    }
}
