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
    @Path("scan")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response scan(Map<String, Object> body, @Context HttpHeaders headers) {
        TokenIntrospection.Result introspected = introspectBearer(headers);
        if (introspected == null) {
            return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized", "Invalid token or missing Authorization: Bearer <token>");
        }

        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Missing session_id");
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null) {
            return errorResponse(Response.Status.NOT_FOUND, "not_found", "QR session does not exist or has expired");
        }

        boolean scanned = store.scan(sessionId, introspected.userId(), introspected.username());
        if (!scanned) {
            return errorResponse(Response.Status.CONFLICT, "already_used", "QR session has already been confirmed or processed");
        }

        return Response.ok(Map.of("status", "scanned", "username", introspected.username())).build();
    }

    @POST
    @Path("approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response approve(Map<String, Object> body, @Context HttpHeaders headers) {
        TokenIntrospection.Result introspected = introspectBearer(headers);
        if (introspected == null) {
            return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized", "Invalid token or missing Authorization: Bearer <token>");
        }

        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Missing session_id");
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null) {
            return errorResponse(Response.Status.NOT_FOUND, "not_found", "QR session does not exist or has expired");
        }
        if (!"scanned".equals(qrSession.status)) {
            return errorResponse(Response.Status.CONFLICT, "not_scanned", "QR session has not been scanned yet or has already been processed — call /scan first");
        }

        boolean approved = store.approve(sessionId, introspected.userId(), introspected.username());
        if (!approved) {
            return errorResponse(Response.Status.CONFLICT, "already_used", "QR session has already been processed or belongs to another user");
        }

        return Response.ok(Map.of("status", "approved")).build();
    }

    @POST
    @Path("cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancel(Map<String, Object> body, @Context HttpHeaders headers) {
        TokenIntrospection.Result introspected = introspectBearer(headers);
        if (introspected == null) {
            return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized", "Invalid token or missing Authorization: Bearer <token>");
        }

        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Missing session_id");
        }

        boolean cancelled = QrLoginSessionStore.getInstance().cancel(sessionId, introspected.userId());
        if (!cancelled) {
            return errorResponse(Response.Status.CONFLICT, "invalid_state", "QR session is not awaiting your confirmation");
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

    private static final long LONG_POLL_TIMEOUT_MILLIS = 30_000;

    @POST
    @Path("check")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response check(Map<String, Object> body) {
        String sessionId = (String) body.get("session_id");
        if (sessionId == null || sessionId.isBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "invalid_request", "Missing session_id");
        }

        QrLoginSessionStore store = QrLoginSessionStore.getInstance();
        QrLoginSessionStore.Session qrSession = store.get(sessionId);
        if (qrSession == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("status", "expired")).build();
        }

        // Khi client gửi known_status, giữ request treo tới khi trạng thái đổi (hoặc timeout)
        // để không phải poll liên tục. Không gửi thì trả trạng thái hiện tại ngay.
        String knownStatus = (String) body.get("known_status");
        if (knownStatus == null || knownStatus.isBlank()) {
            return Response.ok(Map.of("status", qrSession.status)).build();
        }

        String status;
        try {
            status = store.awaitStatusChange(sessionId, knownStatus, LONG_POLL_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = qrSession.status;
        }

        if (status == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("status", "expired")).build();
        }
        return Response.ok(Map.of("status", status)).build();
    }

    private Response errorResponse(Response.Status status, String error, String message) {
        return Response.status(status).entity(Map.of("error", error, "message", message)).build();
    }

    @Override
    public void close() {
    }
}
