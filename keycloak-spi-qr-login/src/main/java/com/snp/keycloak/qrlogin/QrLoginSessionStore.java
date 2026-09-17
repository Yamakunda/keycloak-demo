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
        // pending -> scanned (app đã quét, chờ xác nhận biometric/mật khẩu trên điện thoại)
        //         -> approved (app đã xác nhận xong, web được phép đăng nhập)
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

    // App vừa quét xong, chưa xác nhận biometric/mật khẩu — chỉ ghi nhận danh tính, KHÔNG
    // cho phép web đăng nhập ở bước này. Cho phép gọi lại nhiều lần khi vẫn đang "pending"
    // hoặc đã "scanned" trước đó (vd app quét lại), nhưng không cho quay lui từ "approved".
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
        }
        return true;
    }

    // App đã xác nhận xong (biometric/nhập lại mật khẩu) — chỉ hợp lệ khi đã qua bước "scanned"
    // của CHÍNH userId đó, để tránh 1 access_token khác chiếm quyền approve phiên đã bị quét bởi
    // người khác.
    boolean approve(String id, String userId, String username) {
        Session session = get(id);
        if (session == null || !"scanned".equals(session.status) || !userId.equals(session.userId)) {
            return false;
        }
        synchronized (session) {
            if (!"scanned".equals(session.status) || !userId.equals(session.userId)) return false;
            session.status = "approved";
            session.username = username;
        }
        return true;
    }

    void remove(String id) {
        sessions.remove(id);
    }

    // App huỷ xác nhận (bấm "Từ chối" hoặc biometric thất bại) — trả phiên về "pending" để
    // web vẫn hiện QR chờ quét lại, thay vì phải sinh phiên mới.
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
