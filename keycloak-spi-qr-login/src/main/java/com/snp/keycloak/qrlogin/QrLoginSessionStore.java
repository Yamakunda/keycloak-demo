package com.snp.keycloak.qrlogin;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class QrLoginSessionStore {

    static final long TTL_SECONDS = 180;

    private static final QrLoginSessionStore INSTANCE = new QrLoginSessionStore();

    static QrLoginSessionStore getInstance() {
        return INSTANCE;
    }

    static final class Session {
        final String id;
        final long expiresAtMillis;
        volatile String status = "pending";
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

    /**
     * Chờ tới khi status khác {@code knownStatus} hoặc hết {@code timeoutMillis} — để trang
     * login long-poll thay vì gọi /check liên tục. Trả về status hiện tại, hoặc null nếu
     * phiên đã hết hạn/bị xoá.
     */
    String awaitStatusChange(String id, String knownStatus, long timeoutMillis) throws InterruptedException {
        Session session = get(id);
        if (session == null) return null;

        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (session) {
            while (session.status.equals(knownStatus)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                session.wait(remaining);
            }
            return session.status;
        }
    }

    boolean scan(String id, String userId, String username) {
        Session session = get(id);
        if (session == null || "approved".equals(session.status)) {
            return false;
        }
        synchronized (session) {
            if ("approved".equals(session.status)) return false;
            session.status = "scanned";
            session.userId = userId;
            session.username = username;
            session.notifyAll();
        }
        return true;
    }

    boolean approve(String id, String userId, String username) {
        Session session = get(id);
        if (session == null || !"scanned".equals(session.status) || !userId.equals(session.userId)) {
            return false;
        }
        synchronized (session) {
            if (!"scanned".equals(session.status) || !userId.equals(session.userId)) return false;
            session.status = "approved";
            session.username = username;
            session.notifyAll();
        }
        return true;
    }

    void remove(String id) {
        sessions.remove(id);
    }

    boolean cancel(String id, String userId) {
        Session session = get(id);
        if (session == null || !"scanned".equals(session.status) || !userId.equals(session.userId)) {
            return false;
        }
        synchronized (session) {
            if (!"scanned".equals(session.status) || !userId.equals(session.userId)) return false;
            session.status = "pending";
            session.userId = null;
            session.username = null;
            session.notifyAll();
        }
        return true;
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
