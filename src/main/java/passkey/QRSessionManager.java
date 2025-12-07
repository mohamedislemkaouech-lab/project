package passkey;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class QRSessionManager {

    private static final long EXPIRATION_MS = 5 * 60 * 1000; // 5 min
    private static final Map<String, QRSession> sessions = new ConcurrentHashMap<>();

    static {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            sessions.entrySet().removeIf(e -> e.getValue().isExpired());
        }, 0, 1, TimeUnit.MINUTES);
    }

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
        System.out.println("Created QR session " + id + " for " + username);
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
