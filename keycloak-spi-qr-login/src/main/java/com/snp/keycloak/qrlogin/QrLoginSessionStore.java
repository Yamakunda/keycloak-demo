package com.snp.keycloak.qrlogin;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu trạng thái phiên QR trong bộ nhớ tiến trình Keycloak — đủ cho demo 1 node.
 * Nhiều node Keycloak (cluster) sẽ cần chuyển sang Infinispan cache thay vì static Map này.
 */
final class QrLoginSessionStore {

    static final long TTL_SECONDS = 120;

    private static final QrLoginSessionStore INSTANCE = new QrLoginSessionStore();

    static QrLoginSessionStore getInstance() {
        return INSTANCE;
    }

    static final class Session {
        final String id;
        final long expiresAtMillis;
        volatile String status = "pending"; // pending | approved
        volatile String userId;
        volatile String username;

        Session(String id, long expiresAtMillis) {
            this.id = id;
            this.expiresAtMillis = expiresAtMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    Session createSession() {
        cleanupExpired();
        String id = randomId();
        Session session = new Session(id, System.currentTimeMillis() + TTL_SECONDS * 1000);
        sessions.put(id, session);
        return session;
    }

    Session get(String id) {
        Session session = sessions.get(id);
        if (session == null) return null;
        if (session.isExpired()) {
            sessions.remove(id);
            return null;
        }
        return session;
    }

    boolean approve(String id, String userId, String username, String ignoredAccessToken) {
        Session session = get(id);
        if (session == null || !"pending".equals(session.status)) {
            return false;
        }
        synchronized (session) {
            if (!"pending".equals(session.status)) return false;
            session.status = "approved";
            session.userId = userId;
            session.username = username;
        }
        return true;
    }

    void remove(String id) {
        sessions.remove(id);
    }

    private void cleanupExpired() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private String randomId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private QrLoginSessionStore() {
    }
}
