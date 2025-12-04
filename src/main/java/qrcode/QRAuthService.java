package qrcode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QRAuthService {

    private static final Logger logger = LoggerFactory.getLogger(QRAuthService.class);
    private static final int DEFAULT_TOKEN_VALIDITY_MINUTES = 5;
    private static final int DEFAULT_TOKEN_LENGTH = 32;

    private final Map<String, User> users;
    private final Map<String, AuthSession> activeSessions;
    private final Map<String, PairedDevice> pairedDevices;
    private final SecureRandom secureRandom;
    private String serverUrl;

    public QRAuthService() {
        this("http://localhost:8080");
    }

    public QRAuthService(String serverUrl) {
        this.users = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        this.pairedDevices = new ConcurrentHashMap<>();
        this.secureRandom = new SecureRandom();
        this.serverUrl = serverUrl;
        setupDummyUsers();
    }

    // --- Inner Classes for Data Modeling ---

    public static class User {
        public String email;
        public String displayName;
        public String userId;
        public long createdAt;
        public long lastLogin;
        public List<String> deviceIds;

        public User(String email, String displayName) {
            this.email = email;
            this.displayName = displayName;
            this.userId = "USER_" + UUID.randomUUID().toString().substring(0, 8);
            this.createdAt = System.currentTimeMillis();
            this.lastLogin = System.currentTimeMillis();
            this.deviceIds = new ArrayList<>();
        }
    }

    public static class QRAuthData {
        public String token;
        public String url;
        public long exp;
        public String type;
        public String sessionId;

        public String toJSON() {
            return String.format(
                    "{\"token\":\"%s\",\"url\":\"%s\",\"exp\":%d,\"type\":\"%s\",\"session\":\"%s\"}",
                    token, url, exp, type, sessionId
            );
        }
    }

    /**
     * Represents a pending or confirmed authentication request.
     */
    public static class AuthSession {
        public String sessionId;
        public String userId;
        public String authToken;
        public long createdAt;
        public long expiresAt;
        public long authenticatedAt;
        public SessionStatus status;
        public String deviceId;

        public AuthSession(String sessionId, String userId, String authToken, long expiresAt) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.authToken = authToken;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = expiresAt;
            this.status = SessionStatus.PENDING;
        }
    }

    public static class PairedDevice {
        public String deviceId;
        public String userId;
        public String publicKey;
        public long lastUsed;
    }

    public enum SessionStatus {
        PENDING, SCANNED, AUTHENTICATED, EXPIRED, CANCELLED
    }

    // --- Core Service Methods ---

    /**
     * Generates a new, time-limited authentication session and returns QR code data.
     * This is used by QRCodeWebServer
     */
    public QRAuthData generateLoginQR() throws Exception {
        String sessionId = "SESSION_" + UUID.randomUUID().toString();
        String authToken = generateSecureToken(DEFAULT_TOKEN_LENGTH);
        long expiresAt = System.currentTimeMillis() + (DEFAULT_TOKEN_VALIDITY_MINUTES * 60 * 1000);

        // Get local IP for mobile access
        String localIp = getLocalIP();

        // Create mobile-friendly URL
        String mobileUrl = String.format("http://%s:8080/verify?token=%s&session=%s",
                localIp, authToken, sessionId);

        // Create session
        AuthSession session = new AuthSession(sessionId, null, authToken, expiresAt);
        activeSessions.put(sessionId, session);

        // Create QR data
        QRAuthData qrData = new QRAuthData();
        qrData.token = authToken;
        qrData.url = mobileUrl;
        qrData.exp = expiresAt;
        qrData.type = "auth";
        qrData.sessionId = sessionId;

        logger.info("🎯 Mobile Login QR generated - URL: {}", mobileUrl);

        return qrData;
    }

    /**
     * Generates a new, time-limited authentication session and returns the QR code URL.
     * The URL points to the confirmation server (port 8082).
     * @param userEmail The user attempting to log in.
     * @return The URL string to be encoded in the QR code.
     * @throws Exception if the user is not found.
     */
    public String generateAuthSession(String userEmail) throws Exception {
        User user = getUserByEmail(userEmail);
        if (user == null) {
            throw new Exception("User not found: " + userEmail);
        }

        // 1. Generate unique session ID and token
        String sessionId = UUID.randomUUID().toString();
        String token = generateSecureToken(DEFAULT_TOKEN_LENGTH);
        long expirationTime = System.currentTimeMillis() + (DEFAULT_TOKEN_VALIDITY_MINUTES * 60 * 1000);

        // 2. Create and store the session
        AuthSession newSession = new AuthSession(sessionId, user.userId, token, expirationTime);
        activeSessions.put(sessionId, newSession);

        logger.info("Generated new AuthSession for {}: SessionId={}", user.displayName, sessionId);

        // 3. Construct the mobile-facing URL, pointing to the confirmation server (port 8082)
        String confirmationUrl = serverUrl.replace(":8080", ":8082")
                + "/confirm?session=" + sessionId
                + "&token=" + token;

        logger.info("QR Code URL: {}", confirmationUrl);
        return confirmationUrl;
    }

    /**
     * Confirms an active authentication session.
     * Called by the QRCodeConfirmationServer when the user clicks 'Confirm' on their phone.
     * @param sessionId The ID of the session.
     * @param token The one-time token from the QR code.
     * @return true if confirmation was successful and the token/session was valid.
     */
    public boolean confirmAuthSession(String sessionId, String token) {
        AuthSession session = activeSessions.get(sessionId);

        if (session == null) {
            logger.warn("Confirmation failed: Session is null: {}", sessionId);
            return false;
        }

        if (session.status == SessionStatus.AUTHENTICATED) {
            logger.warn("Confirmation failed: Session already confirmed: {}", sessionId);
            return false;
        }

        if (session.expiresAt < System.currentTimeMillis()) {
            logger.warn("Confirmation failed: Session expired: {}", sessionId);
            session.status = SessionStatus.EXPIRED;
            return false;
        }

        if (!session.authToken.equals(token)) {
            logger.warn("Confirmation failed: Invalid token for session: {}", sessionId);
            return false;
        }

        // Success: Mark as confirmed
        session.status = SessionStatus.AUTHENTICATED;
        session.authenticatedAt = System.currentTimeMillis();

        // Update user's last login
        User user = users.values().stream()
                .filter(u -> u.userId.equals(session.userId))
                .findFirst()
                .orElse(null);
        if (user != null) {
            user.lastLogin = System.currentTimeMillis();
        }

        logger.info("✅ Session {} confirmed by mobile device.", sessionId);
        return true;
    }

    /**
     * For mobile scanning QR code
     */
    public AuthSession scanQRCode(String authToken, String deviceId) {
        logger.info("📱 QR Code scanned by device: {}", deviceId);

        AuthSession session = activeSessions.values().stream()
                .filter(s -> authToken.equals(s.authToken))
                .findFirst()
                .orElse(null);

        if (session == null) {
            logger.warn("❌ Invalid auth token");
            return null;
        }

        if (session.status != SessionStatus.PENDING) {
            logger.warn("❌ Session already processed");
            return null;
        }

        if (System.currentTimeMillis() > session.expiresAt) {
            session.status = SessionStatus.EXPIRED;
            logger.warn("❌ Session expired");
            return null;
        }

        session.status = SessionStatus.SCANNED;
        session.deviceId = deviceId;

        logger.info("✓ QR Code scanned successfully");
        return session;
    }

    /**
     * Mobile confirms login with user selection
     */
    public User confirmLogin(String authToken, String deviceId, String email) {
        logger.info("🔐 User confirming login: {}", email);

        User user = getUserByEmail(email);
        if (user == null) {
            logger.warn("❌ User not found: {}", email);
            return null;
        }

        AuthSession session = activeSessions.values().stream()
                .filter(s -> authToken.equals(s.authToken))
                .findFirst()
                .orElse(null);

        if (session == null) {
            logger.warn("❌ Session not found");
            return null;
        }

        if (session.status != SessionStatus.SCANNED) {
            logger.warn("❌ Invalid session status: {}", session.status);
            return null;
        }

        session.status = SessionStatus.AUTHENTICATED;
        session.userId = user.userId;
        session.deviceId = deviceId;
        session.authenticatedAt = System.currentTimeMillis();

        user.lastLogin = System.currentTimeMillis();
        if (!user.deviceIds.contains(deviceId)) {
            user.deviceIds.add(deviceId);
        }

        logger.info("✅ Login confirmed! User: {}, Device: {}", user.displayName, deviceId);
        return user;
    }

    /**
     * Check if session is authenticated
     */
    public User checkAuthenticationStatus(String sessionId) {
        AuthSession session = activeSessions.get(sessionId);
        if (session == null) {
            return null;
        }

        if (session.status == SessionStatus.AUTHENTICATED) {
            // Find user by userId
            return users.values().stream()
                    .filter(user -> user.userId.equals(session.userId))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    /**
     * Get session status
     */
    public SessionStatus getSessionStatus(String sessionId) {
        AuthSession session = activeSessions.get(sessionId);
        return session != null ? session.status : null;
    }

    /**
     * Get session by ID
     */
    public AuthSession getSession(String sessionId) {
        AuthSession session = activeSessions.get(sessionId);
        if (session != null && session.expiresAt < System.currentTimeMillis()) {
            session.status = SessionStatus.EXPIRED;
            return null; // Session expired
        }
        return session;
    }

    // --- Accessors and Utilities ---

    public User getUserByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.email.equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public void setServerUrl(String url) {
        this.serverUrl = url;
    }

    private String generateSecureToken(int length) {
        byte[] randomBytes = new byte[length];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void setupDummyUsers() {
        registerUser("alice@example.com", "Alice Tester");
        registerUser("bob@example.com", "Bob Developer");
        registerUser("user@test.com", "Test User");
    }

    /**
     * Register a new user
     */
    public User registerUser(String email, String displayName) {
        User existingUser = getUserByEmail(email);
        if (existingUser != null) {
            return existingUser;
        }

        User user = new User(email, displayName);
        users.put(email.toLowerCase(), user);
        logger.info("✓ New user registered: {} ({})", displayName, email);
        return user;
    }

    public String getLocalIP() throws Exception {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isSiteLocalAddress() && addr.getHostAddress().contains(".")) {
                        return addr.getHostAddress();
                    }
                }
            }
            return "192.168.1.100";
        } catch (Exception e) {
            return "192.168.1.100";
        }
    }

    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        int removed = 0;

        Iterator<Map.Entry<String, AuthSession>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AuthSession> entry = iterator.next();
            if (currentTime > entry.getValue().expiresAt) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            logger.info("Cleaned up {} expired sessions", removed);
        }
    }
}