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
 *   POST /realms/{realm}/qr-login/scan     — app gọi ngay sau khi quét (kèm Bearer
 *                                             access_token) để ghi nhận danh tính, CHƯA cho
 *                                             web đăng nhập — app phải hiển thị màn hình xác
 *                                             nhận (biometric/nhập lại mật khẩu) trước
 *   POST /realms/{realm}/qr-login/approve  — app gọi SAU KHI người dùng xác nhận trên điện
 *                                             thoại, mới thật sự cho phép web đăng nhập
 *   POST /realms/{realm}/qr-login/cancel   — app gọi khi người dùng từ chối/huỷ xác nhận
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

    // App gọi ngay sau khi camera đọc được mã QR — chỉ ghi nhận "ai đang muốn approve",
    // KHÔNG cấp quyền đăng nhập cho web ở bước này. App phải tự hiển thị màn hình xác nhận
    // (biometric hoặc nhập lại mật khẩu) trước khi cho phép gọi /approve.
    @POST
    @Path("scan")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response scan(Map<String, Object> body, @Context HttpHeaders headers) {
        TokenIntrospection.Result introspected = introspectBearer(headers);
        if (introspected == null) {
            return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized", "Token không hợp lệ hoặc thiếu Authorization: Bearer <token>");
        }

        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Thiếu session_id");
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null) {
            return errorResponse(Response.Status.NOT_FOUND, "not_found", "Phiên QR không tồn tại hoặc đã hết hạn");
        }

        boolean scanned = store.scan(sessionId, introspected.userId(), introspected.username());
        if (!scanned) {
            return errorResponse(Response.Status.CONFLICT, "already_used", "Phiên QR đã được xác nhận hoặc đã xử lý");
        }

        return Response.ok(Map.of("status", "scanned", "username", introspected.username())).build();
    }

    // App gọi SAU KHI người dùng xác nhận thành công bằng biometric/nhập lại mật khẩu trên
    // chính điện thoại — đây là bước thật sự cấp quyền cho web đăng nhập.
    @POST
    @Path("approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response approve(Map<String, Object> body, @Context HttpHeaders headers) {
        TokenIntrospection.Result introspected = introspectBearer(headers);
        if (introspected == null) {
            return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized", "Token không hợp lệ hoặc thiếu Authorization: Bearer <token>");
        }

        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Thiếu session_id");
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null) {
            return errorResponse(Response.Status.NOT_FOUND, "not_found", "Phiên QR không tồn tại hoặc đã hết hạn");
        }
        if (!"scanned".equals(qrSession.status)) {
            return errorResponse(Response.Status.CONFLICT, "not_scanned", "Phiên QR chưa được quét hoặc đã được xử lý — cần gọi /scan trước");
        }

        boolean approved = store.approve(sessionId, introspected.userId(), introspected.username());
        if (!approved) {
            return errorResponse(Response.Status.CONFLICT, "already_used", "Phiên QR đã được xử lý hoặc thuộc về người dùng khác");
        }

        return Response.ok(Map.of("status", "approved")).build();
    }

    // App gọi khi người dùng bấm "Từ chối" hoặc biometric thất bại — trả phiên QR về trạng
    // thái chờ quét lại, thay vì để web bị treo mãi ở "scanned".
    @POST
    @Path("cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancel(Map<String, Object> body, @Context HttpHeaders headers) {
        TokenIntrospection.Result introspected = introspectBearer(headers);
        if (introspected == null) {
            return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized", "Token không hợp lệ hoặc thiếu Authorization: Bearer <token>");
        }

        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Thiếu session_id");
        }

        boolean cancelled = QrLoginSessionStore.getInstance().cancel(sessionId, introspected.userId());
        if (!cancelled) {
            return errorResponse(Response.Status.CONFLICT, "invalid_state", "Phiên QR không ở trạng thái chờ xác nhận của bạn");
        }
        return Response.ok(Map.of("status", "pending")).build();
    }

    private TokenIntrospection.Result introspectBearer(HttpHeaders headers) {
        String authHeader = headers.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String accessToken = authHeader.substring("Bearer ".length());
        try {
            return TokenIntrospection.verify(session, accessToken);
        } catch (Exception e) {
            logger.warnf("QR: token verify failed: %s", e.getMessage());
            return null;
        }
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
