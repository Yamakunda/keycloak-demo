package com.snp.keycloak.qrlogin;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

import java.util.Map;

/**
 * Endpoint REST tùy biến cho luồng "quét QR đăng nhập chéo thiết bị":
 *   POST /realms/{realm}/qr-login/start    — web gọi để sinh session_id + QR payload
 *   POST /realms/{realm}/qr-login/approve  — app gọi (kèm Bearer access_token) để tự
 *                                             động approve, không cần mở trình duyệt
 *   POST /realms/{realm}/qr-login/poll     — web gọi lặp lại để chờ kết quả
 *
 * Đây là bản port sang chạy ngay trong Keycloak của route Node.js
 * bookstore-api-qr/src/routes/qrSession.js — cùng logic, khác chỗ chạy.
 */
public class QrLoginResourceProvider implements RealmResourceProvider {

    private static final Logger logger = Logger.getLogger(QrLoginResourceProvider.class);

    private final KeycloakSession session;

    public QrLoginResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @POST
    @Path("start")
    @Produces(MediaType.APPLICATION_JSON)
    public Response start() {
        QrLoginSessionStore.Session created = QrLoginSessionStore.getInstance().createSession();
        return Response.ok(Map.of(
                "session_id", created.id,
                "expires_in", QrLoginSessionStore.TTL_SECONDS
        )).build();
    }

    @POST
    @Path("approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response approve(Map<String, Object> body, @Context HttpHeaders headers) {
        String authHeader = headers.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized", "Missing Authorization: Bearer <token>");
        }
        String accessToken = authHeader.substring("Bearer ".length());

        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Thiếu session_id");
        }

        TokenIntrospection.Result introspected;
        try {
            introspected = TokenIntrospection.verify(session, accessToken);
        } catch (Exception e) {
            logger.warnf("QR approve: token verify failed: %s", e.getMessage());
            return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized", "Token không hợp lệ: " + e.getMessage());
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null) {
            return errorResponse(Response.Status.NOT_FOUND, "not_found", "Phiên QR không tồn tại hoặc đã hết hạn");
        }
        if (!"pending".equals(qrSession.status)) {
            return errorResponse(Response.Status.CONFLICT, "already_used", "Phiên QR đã được xử lý");
        }

        boolean approved = store.approve(sessionId, introspected.userId(), introspected.username(), accessToken);
        if (!approved) {
            return errorResponse(Response.Status.CONFLICT, "already_used", "Phiên QR đã được xử lý");
        }

        return Response.ok(Map.of("status", "approved")).build();
    }

    // POST /qr-login/check — trang login (qr-login.ftl) gọi lặp lại chỉ để biết đã approved
    // chưa, KHÔNG issue token và KHÔNG xoá session (khác /poll dùng cho web/app rời rạc) —
    // sau khi thấy approved=true, trang login tự submit form để Authenticator.action() xử lý
    // tiếp trong flow chuẩn (setUser + success), lúc đó Keycloak mới thật sự issue token.
    @POST
    @Path("check")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response check(Map<String, Object> body) {
        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Thiếu session_id");
        }
        QrLoginSessionStore.Session qrSession = QrLoginSessionStore.getInstance().get(sessionId);
        if (qrSession == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("status", "expired")).build();
        }
        return Response.ok(Map.of("status", qrSession.status)).build();
    }

    @POST
    @Path("poll")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response poll(Map<String, Object> body) {
        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Thiếu session_id");
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("status", "expired")).build();
        }
        if ("pending".equals(qrSession.status)) {
            return Response.ok(Map.of("status", "pending")).build();
        }

        // Trả 1 lần rồi xoá — tránh issue token thêm lần nữa nếu web gọi poll trùng lặp.
        store.remove(sessionId);
        try {
            var tokenResponse = TokenIssuer.issueFor(session, qrSession.userId);
            return Response.ok(Map.of(
                    "status", "approved",
                    "username", qrSession.username,
                    "token", tokenResponse
            )).build();
        } catch (Exception e) {
            logger.error("QR poll: issue token failed", e);
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "server_error", "Không tạo được token: " + e.getMessage());
        }
    }

    private Response errorResponse(Response.Status status, String error, String message) {
        return Response.status(status).entity(Map.of("error", error, "message", message)).build();
    }

    @Override
    public void close() {
        // Không giữ tài nguyên nào cần đóng theo request scope.
    }
}
