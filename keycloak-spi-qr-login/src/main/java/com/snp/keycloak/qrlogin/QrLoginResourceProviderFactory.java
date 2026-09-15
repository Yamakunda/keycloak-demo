package com.snp.keycloak.qrlogin;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Đăng ký endpoint REST tùy biến tại /realms/{realm}/qr-login/* — factory id "qr-login"
 * phải khớp với tên file trong META-INF/services.
 */
public class QrLoginResourceProviderFactory implements RealmResourceProviderFactory {

    public static final String ID = "qr-login";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new QrLoginResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
