package passkey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QRSessionManager {
    private static final long EXPIRATION_MS = 5 * 60 * 1000; // 5 minutes
    private static final Map<String, QRSession> sessions = new HashMap<>();

    public static class QRSession {
        public String username;
        public long createdAt;

        public QRSession(String username) {
            this.username = username;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRATION_MS;
        }
    }

    public static String createSession(String username) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new QRSession(username));
        return id;
    }

    public static QRSession getSession(String id) {
        QRSession session = sessions.get(id);
        if (session == null || session.isExpired()) {
            sessions.remove(id);
            return null;
        }
        return session;
    }

    public static void removeSession(String id) {
        sessions.remove(id);
    }
}
