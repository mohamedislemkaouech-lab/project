package org.example;

import qrcode.QRAuthService;
import qrcode.QRAuthService.User;
import qrcode.QRAuthService.AuthSession;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QRCodeConfirmationServer {

    private ServerSocket serverSocket;
    private final QRAuthService authService;
    private final int port;
    private boolean running = false;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private final String serverIp;

    public QRCodeConfirmationServer(int port, QRAuthService authService) throws Exception {
        this.port = port;
        this.authService = authService;
        this.serverIp = getLocalIP();
    }

    private String getLocalIP() throws Exception {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "192.168.1.100";
        }
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;

            System.out.println("\n" + "=".repeat(60));
            System.out.println("🔐 QR CODE CONFIRMATION SERVER");
            System.out.println("=".repeat(60));
            System.out.println("\n🌐 Server running on:");
            System.out.println("   http://localhost:" + port);
            System.out.println("   http://" + serverIp + ":" + port);
            System.out.println("\n📱 Waiting for mobile confirmations...");
            System.out.println("=".repeat(60));

            new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        threadPool.submit(() -> handleRequest(clientSocket));
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("Error accepting connection: " + e.getMessage());
                        }
                    }
                }
            }).start();

        } catch (IOException e) {
            System.err.println("❌ Could not start server on port " + port + ": " + e.getMessage());
            System.out.println("⚠️  Trying port " + (port + 1) + "...");
            try {
                serverSocket = new ServerSocket(port + 1);
                running = true;
                startServerThread();
            } catch (IOException ex) {
                System.err.println("❌ Failed to start on alternative port: " + ex.getMessage());
            }
        }
    }

    private void startServerThread() {
        new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleRequest(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private void handleRequest(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                sendError(out, "Empty Request");
                return;
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                sendError(out, "Invalid Request");
                return;
            }

            String method = requestParts[0];
            String fullPath = requestParts[1];

            // Read headers to get content length for POST requests
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim().toLowerCase();
                    String value = line.substring(colonIndex + 1).trim();
                    headers.put(key, value);
                }
            }

            String path = fullPath.contains("?") ? fullPath.split("\\?")[0] : fullPath;
            Map<String, String> params = parseQueryParams(fullPath);

            // Handle POST body if present
            if (method.equalsIgnoreCase("POST")) {
                String postBody = readPostBody(in, headers);
                params.putAll(parsePostBody(postBody));
            }

            System.out.println("📡 Request: " + method + " " + path);

            // Route requests
            if (path.equals("/") || path.equals("/index.html")) {
                serveHomePage(out);
            } else if (path.equals("/confirm")) {
                handleConfirmationPage(out, params);
            } else if (path.equals("/action/confirm")) {
                handleConfirmationAction(out, params);
            } else if (path.equals("/success")) {
                serveSuccessPage(out, params);
            } else if (path.equals("/check-status")) {
                handleCheckStatus(out, params);
            } else {
                serveHomePage(out);
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private String readPostBody(BufferedReader in, Map<String, String> headers) throws IOException {
        if (!headers.containsKey("content-length")) {
            return "";
        }

        int contentLength = Integer.parseInt(headers.get("content-length"));
        if (contentLength <= 0) {
            return "";
        }

        char[] buffer = new char[contentLength];
        int bytesRead = in.read(buffer, 0, contentLength);
        return new String(buffer, 0, bytesRead);
    }

    /** Handles serving the HTML page with the "Confirm" button. */
    private void handleConfirmationPage(PrintWriter out, Map<String, String> params) {
        String token = params.get("token");
        String sessionId = params.get("session");
        String email = params.get("email");

        if (token == null || sessionId == null) {
            sendError(out, "❌ Invalid confirmation link. Missing session or token.");
            return;
        }

        try {
            // Get session from auth service
            AuthSession session = authService.getSession(sessionId);

            if (session == null) {
                sendError(out, "❌ Authentication session is expired or invalid.");
                return;
            }

            // Check if already confirmed
            if (session.status == QRAuthService.SessionStatus.AUTHENTICATED) {
                sendResponse(out, createAlreadyConfirmedHtml());
                return;
            }

            // Get user info
            User user = null;
            if (session.userId != null) {
                user = authService.getAllUsers().stream()
                        .filter(u -> u.userId.equals(session.userId))
                        .findFirst()
                        .orElse(null);
            }

            // If email provided in URL, use that
            if (email != null && user == null) {
                user = authService.getUserByEmail(email);
            }

            // Default to demo user if no user found
            if (user == null) {
                user = authService.getUserByEmail("alice@example.com");
                if (user == null) {
                    user = authService.registerUser("alice@example.com", "Alice Tester");
                }
            }

            // Serve the confirmation page
            sendResponse(out, createConfirmationHtml(user.displayName, user.email, sessionId, token));

        } catch (Exception e) {
            System.err.println("❌ Error in /confirm: " + e.getMessage());
            sendError(out, "Server error: " + e.getMessage());
        }
    }

    /** Handles the button click from the mobile device to confirm the login. */
    private void handleConfirmationAction(PrintWriter out, Map<String, String> params) {
        String token = params.get("token");
        String sessionId = params.get("session");
        String email = params.get("email");

        if (token == null || sessionId == null) {
            sendError(out, "❌ Invalid confirmation action.");
            return;
        }

        try {
            // Confirm the session
            boolean confirmed = authService.confirmAuthSession(sessionId, token);

            if (confirmed) {
                // Get user info for success page
                AuthSession session = authService.getSession(sessionId);
                String userName = "User";
                if (session != null && session.userId != null) {
                    User user = authService.getAllUsers().stream()
                            .filter(u -> u.userId.equals(session.userId))
                            .findFirst()
                            .orElse(null);
                    if (user != null) {
                        userName = user.displayName;
                    }
                }

                String successHtml = createSuccessHtml(userName, sessionId);
                sendResponse(out, successHtml);
            } else {
                sendError(out, "❌ Confirmation failed. Token expired or invalid.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error in /action/confirm: " + e.getMessage());
            sendError(out, "Server error during confirmation.");
        }
    }

    /** Check session status (for polling) */
    private void handleCheckStatus(PrintWriter out, Map<String, String> params) {
        String sessionId = params.get("session");

        if (sessionId == null) {
            sendJsonResponse(out, "{\"status\":\"error\",\"message\":\"No session ID\"}");
            return;
        }

        try {
            AuthSession session = authService.getSession(sessionId);

            if (session == null) {
                sendJsonResponse(out, "{\"status\":\"expired\",\"message\":\"Session not found\"}");
            } else if (session.status == QRAuthService.SessionStatus.AUTHENTICATED) {
                sendJsonResponse(out, String.format(
                        "{\"status\":\"authenticated\",\"sessionId\":\"%s\",\"authenticated\":true}",
                        sessionId
                ));
            } else {
                sendJsonResponse(out, String.format(
                        "{\"status\":\"%s\",\"authenticated\":false}",
                        session.status.toString().toLowerCase()
                ));
            }
        } catch (Exception e) {
            sendJsonResponse(out, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // --- HTML Generation Methods (Mobile-Friendly) ---

    private void serveHomePage(PrintWriter out) {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>QR Code Confirmation Server</title>
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
                        text-align: center; 
                        padding: 20px; 
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        color: white;
                    }
                    .container { 
                        max-width: 500px; 
                        margin: 40px auto; 
                        background: rgba(255, 255, 255, 0.1);
                        padding: 40px; 
                        border-radius: 20px;
                        backdrop-filter: blur(10px);
                    }
                    h1 { color: white; margin-bottom: 10px; }
                    .status { 
                        background: rgba(255, 255, 255, 0.2); 
                        padding: 20px; 
                        border-radius: 10px; 
                        margin: 20px 0; 
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🔐 QR Code Confirmation Server</h1>
                    <div class="status">
                        <p><strong>Status:</strong> ✅ Running</p>
                        <p><strong>Port:</strong> %d</p>
                        <p><strong>IP:</strong> %s</p>
                    </div>
                    <p>This server handles mobile confirmations for QR code authentication.</p>
                    <p>Scan a QR code from the main server to begin.</p>
                </div>
            </body>
            </html>
            """.formatted(port, serverIp);

        sendResponse(out, html);
    }

    private void serveSuccessPage(PrintWriter out, Map<String, String> params) {
        String userName = params.getOrDefault("user", "User");
        String sessionId = params.getOrDefault("session", "");

        String html = createSuccessHtml(userName, sessionId);
        sendResponse(out, html);
    }

    private String createConfirmationHtml(String userName, String userEmail, String sessionId, String token) {
        String actionUrl = String.format("/action/confirm?session=%s&token=%s",
                sessionId, token);

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Confirm Login</title>
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
                        text-align: center; 
                        padding: 20px; 
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        color: white;
                    }
                    .card { 
                        background: rgba(255, 255, 255, 0.1);
                        border-radius: 20px; 
                        padding: 40px; 
                        max-width: 400px; 
                        margin: 40px auto; 
                        backdrop-filter: blur(10px);
                    }
                    .user-info {
                        background: rgba(255, 255, 255, 0.2);
                        padding: 20px;
                        border-radius: 10px;
                        margin: 20px 0;
                    }
                    .confirm-btn {
                        display: inline-block;
                        padding: 15px 40px;
                        background: #4CAF50; 
                        color: white;
                        text-decoration: none;
                        border-radius: 10px;
                        font-size: 18px;
                        font-weight: bold;
                        margin: 20px 0;
                        border: none;
                        cursor: pointer;
                        width: 100%%;
                    }
                    .cancel-btn {
                        display: inline-block;
                        padding: 10px 20px;
                        background: #dc3545;
                        color: white;
                        text-decoration: none;
                        border-radius: 8px;
                        margin: 10px;
                        border: none;
                        cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>🔐 Login Request</h1>
                    <p>A login attempt was initiated on your desktop.</p>
                    
                    <div class="user-info">
                        <h2>%s</h2>
                        <p>%s</p>
                        <p><small>Session: %s</small></p>
                    </div>
                    
                    <p>Do you authorize this login?</p>
                    
                    <form method="POST" action="%s">
                        <button type="submit" class="confirm-btn">
                            ✅ Yes, Confirm Login
                        </button>
                    </form>
                    
                    <button onclick="window.close()" class="cancel-btn">
                        ❌ No, Cancel
                    </button>
                    
                    <p style="margin-top: 30px; font-size: 0.9em; opacity: 0.8;">
                        If this wasn't you, simply close this page.
                    </p>
                </div>
                
                <script>
                    // Auto-refresh to check status
                    setInterval(() => {
                        fetch('/check-status?session=%s')
                            .then(response => response.json())
                            .then(data => {
                                if (data.authenticated) {
                                    window.location.href = '/success?session=%s&user=%s';
                                }
                            });
                    }, 3000);
                </script>
            </body>
            </html>
            """, userName, userEmail,
                sessionId.length() > 10 ? sessionId.substring(0, 10) + "..." : sessionId,
                actionUrl, sessionId, sessionId, userName);
    }

    private String createSuccessHtml(String userName, String sessionId) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Login Confirmed</title>
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, sans-serif; 
                        text-align: center; 
                        padding: 40px; 
                        background: linear-gradient(135deg, #4CAF50 0%%, #45a049 100%%);
                        min-height: 100vh;
                        color: white;
                    }
                    .card { 
                        background: rgba(255, 255, 255, 0.2);
                        border-radius: 20px; 
                        padding: 40px; 
                        max-width: 400px; 
                        margin: 40px auto; 
                        backdrop-filter: blur(10px);
                    }
                    .success-icon {
                        font-size: 80px;
                        margin: 20px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="success-icon">✅</div>
                    <h1>Login Confirmed!</h1>
                    <p>You have successfully authorized the login for:</p>
                    <h2>%s</h2>
                    <p>You can now close this browser window.</p>
                    <p>The desktop computer will automatically log you in.</p>
                    
                    <div style="margin-top: 30px; padding: 20px; background: rgba(255, 255, 255, 0.2); border-radius: 10px;">
                        <p><strong>Session:</strong> %s</p>
                        <p><small>This page will close automatically in 5 seconds...</small></p>
                    </div>
                </div>
                
                <script>
                    // Close window after 5 seconds
                    setTimeout(() => {
                        window.close();
                    }, 5000);
                </script>
            </body>
            </html>
            """, userName,
                sessionId.length() > 10 ? sessionId.substring(0, 10) + "..." : sessionId);
    }

    private String createAlreadyConfirmedHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Already Confirmed</title>
                <style>
                    body { 
                        font-family: sans-serif; 
                        text-align: center; 
                        padding: 40px; 
                        background-color: #fff3e0; 
                    }
                    .card { 
                        background-color: white; 
                        border: 2px solid #ff9800; 
                        border-radius: 12px; 
                        padding: 30px; 
                        max-width: 400px; 
                        margin: 40px auto; 
                    }
                    h1 { color: #ff9800; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>⚠️ Session Already Active</h1>
                    <p>This login session was already confirmed.</p>
                    <p>You can close this page.</p>
                </div>
            </body>
            </html>
            """;
    }

    // --- Utility Methods ---

    private Map<String, String> parseQueryParams(String fullPath) {
        Map<String, String> params = new HashMap<>();
        if (!fullPath.contains("?")) {
            return params;
        }
        String queryString = fullPath.substring(fullPath.indexOf('?') + 1);
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            try {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
                    params.put(key, value);
                }
            } catch (UnsupportedEncodingException e) {
                // Ignore, should not happen with UTF-8
            }
        }
        return params;
    }

    private Map<String, String> parsePostBody(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return params;
        }
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            try {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
                    params.put(key, value);
                }
            } catch (UnsupportedEncodingException e) {
                // Ignore
            }
        }
        return params;
    }

    private void sendResponse(PrintWriter out, String html) {
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: text/html; charset=UTF-8");
        out.println("Content-Length: " + html.getBytes(StandardCharsets.UTF_8).length);
        out.println("Connection: close");
        out.println();
        out.println(html);
    }

    private void sendJsonResponse(PrintWriter out, String json) {
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + json.length());
        out.println("Connection: close");
        out.println();
        out.println(json);
    }

    private void sendError(PrintWriter out, String message) {
        String html = String.format("""
            <!DOCTYPE html>
            <html>
            <head><title>Error</title></head>
            <body style="font-family: sans-serif; padding: 20px;">
                <h1 style="color: #dc3545;">❌ Error</h1>
                <p>%s</p>
                <a href="/">Back to home</a>
            </body>
            </html>
            """, message);
        sendResponse(out, html);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            threadPool.shutdownNow();
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void main(String[] args) {
        try {
            QRAuthService authService = new QRAuthService();
            QRCodeConfirmationServer server = new QRCodeConfirmationServer(8082, authService);
            server.start();

            System.out.println("\n✅ Confirmation Server is running!");
            System.out.println("\nPress Enter to stop the server...");
            new Scanner(System.in).nextLine();

            server.stop();
            System.out.println("Server stopped.");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}