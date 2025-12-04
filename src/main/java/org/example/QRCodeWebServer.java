// File: src/main/java/org/example/QRCodeWebServer.java
package org.example;

import io.javalin.Javalin;
import qrcode.QRAuthService;
import qrcode.QRAuthService.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Web Server for QR Code Authentication
 * Provides web pages that users see when scanning QR codes
 */
public class QRCodeWebServer {

    private final Javalin app;
    private final QRAuthService authService;
    private final int port;

    public QRCodeWebServer(int port, QRAuthService authService) {
        this.port = port;
        this.authService = authService;
        this.app = createJavalinApp();
    }

    private Javalin createJavalinApp() {
        // Remove static files configuration since we don't need it
        return Javalin.create(config -> {
            // No static files needed - we serve all HTML directly
        });
    }

    private void setupRoutes(Javalin javalin) {
        // Home page - shows login options
        javalin.get("/", ctx -> {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>QR Auth Demo</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                        .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                        .header { text-align: center; margin-bottom: 30px; }
                        .button { display: inline-block; padding: 12px 24px; background: #007bff; color: white; text-decoration: none; border-radius: 5px; margin: 10px; }
                        .button:hover { background: #0056b3; }
                        .feature-list { margin: 20px 0; }
                        .feature { margin: 10px 0; padding: 10px; background: #f8f9fa; border-radius: 5px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>🔐 QR Code Authentication</h1>
                            <p>Experience passwordless login with QR codes</p>
                        </div>

                        <div style="text-align: center;">
                            <a href="/generate-login" class="button">Generate Login QR Code</a>
                            <a href="/users" class="button">View All Users</a>
                        </div>

                        <div class="feature-list">
                            <h3>How it works:</h3>
                            <div class="feature">
                                <strong>1. Generate QR Code</strong> - Click the button above to create a login QR code
                            </div>
                            <div class="feature">
                                <strong>2. Scan with Phone</strong> - Use your phone's camera to scan the QR code
                            </div>
                            <div class="feature">
                                <strong>3. Confirm Login</strong> - Tap confirm on your phone to login
                            </div>
                            <div class="feature">
                                <strong>4. Automatic Access</strong> - You're logged in without typing a password!
                            </div>
                        </div>

                        <div style="margin-top: 30px; padding: 20px; background: #e7f3ff; border-radius: 5px;">
                            <h4>💡 No Passwords Required!</h4>
                            <p>This demo shows how you can authenticate users without them ever typing a password. 
                            Just scan and confirm!</p>
                        </div>
                    </div>
                </body>
                </html>
                """;
            ctx.html(html);
        });

        // Generate QR code for login
        javalin.get("/generate-login", ctx -> {
            try {
                // Generate QR code data
                QRAuthService.QRAuthData qrData = authService.generateLoginQR();

                String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Scan QR Code</title>
                        <style>
                            body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                            .container { max-width: 600px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; }
                            .qr-code { margin: 20px 0; padding: 20px; border: 2px dashed #ddd; border-radius: 10px; }
                            .instructions { text-align: left; margin: 20px 0; padding: 15px; background: #f8f9fa; border-radius: 5px; }
                            .status { margin: 20px 0; padding: 15px; border-radius: 5px; }
                            .status.pending { background: #fff3cd; border: 1px solid #ffeaa7; }
                            .status.authenticated { background: #d1ecf1; border: 1px solid #bee5eb; }
                            .button { padding: 10px 20px; background: #28a745; color: white; text-decoration: none; border-radius: 5px; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>📱 Scan to Login</h1>
                            <p>Scan this QR code with your phone's camera to login</p>
                            
                            <div class="qr-code">
                                <p><strong>Session ID:</strong> <span id="sessionId">%s</span></p>
                                <p><strong>QR Code Data:</strong></p>
                                <div style="padding: 10px; background: #f8f9fa; border-radius: 5px; font-family: monospace; word-break: break-all;">
                                    %s
                                </div>
                                <p style="margin-top: 15px; color: #666;">
                                    <em>In a real implementation, this would be a scannable QR code image.</em>
                                </p>
                            </div>

                            <div class="instructions">
                                <h3>How to test the flow:</h3>
                                <ol>
                                    <li>Open this URL on your phone: <a href="/mobile-test?token=%s">http://localhost:%d/mobile-test?token=%s</a></li>
                                    <li>Or manually go to: http://localhost:%d/verify?token=%s</li>
                                    <li>Select your account and confirm login</li>
                                    <li>You'll be automatically logged in here!</li>
                                </ol>
                            </div>

                            <div class="status pending" id="statusArea">
                                <strong>Status:</strong> <span id="statusText">Waiting for scan...</span>
                            </div>

                            <div>
                                <a href="/" class="button">Back to Home</a>
                                <button onclick="checkStatus()" class="button">Check Status</button>
                            </div>
                        </div>

                        <script>
                            const sessionId = "%s";
                            
                            async function checkStatus() {
                                try {
                                    const response = await fetch('/check-auth/' + sessionId);
                                    const data = await response.json();
                                    
                                    const statusText = document.getElementById('statusText');
                                    const statusArea = document.getElementById('statusArea');
                                    
                                    if (data.authenticated) {
                                        statusText.textContent = '✅ Authenticated as ' + data.user.displayName;
                                        statusArea.className = 'status authenticated';
                                        // Redirect to dashboard after 2 seconds
                                        setTimeout(() => {
                                            window.location.href = '/dashboard?sessionId=' + sessionId;
                                        }, 2000);
                                    } else {
                                        statusText.textContent = '⏳ ' + data.status;
                                    }
                                } catch (error) {
                                    console.error('Error checking status:', error);
                                }
                            }
                            
                            // Check status every 3 seconds
                            setInterval(checkStatus, 3000);
                        </script>
                    </body>
                    </html>
                    """.formatted(
                        qrData.sessionId,
                        qrData.toJSON(),
                        qrData.token, port, qrData.token,
                        port, qrData.token,
                        qrData.sessionId
                );

                ctx.html(html);
            } catch (Exception e) {
                ctx.html("Error generating QR code: " + e.getMessage());
            }
        });

        // Mobile test page - easy access for testing
        javalin.get("/mobile-test", ctx -> {
            String token = ctx.queryParam("token");
            if (token == null) {
                ctx.redirect("/generate-login");
                return;
            }
            ctx.redirect("/verify?token=" + token + "&device=MOBILE_TEST");
        });

        // Mobile verification endpoint - this is what the QR code points to
        javalin.get("/verify", ctx -> {
            String token = ctx.queryParam("token");
            String deviceId = ctx.queryParam("device");
            if (deviceId == null) deviceId = "MOBILE_" + System.currentTimeMillis();

            if (token == null || token.isEmpty()) {
                ctx.html("Invalid token");
                return;
            }

            // Simulate mobile device scanning
            QRAuthService.AuthSession scannedSession = authService.scanQRCode(token, deviceId);

            if (scannedSession != null) {
                String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Confirm Login</title>
                        <style>
                            body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                            .container { max-width: 400px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                            .user-option { padding: 15px; margin: 10px 0; border: 2px solid #e9ecef; border-radius: 5px; cursor: pointer; }
                            .user-option:hover { border-color: #007bff; background: #f8f9fa; }
                            .user-option.selected { border-color: #28a745; background: #d4edda; }
                            .button { width: 100%%; padding: 12px; background: #007bff; color: white; border: none; border-radius: 5px; font-size: 16px; cursor: pointer; }
                            .button:disabled { background: #6c757d; cursor: not-allowed; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>🔐 Confirm Login</h1>
                            <p>Select your account to login:</p>
                            
                            <form action="/confirm-login" method="post">
                                <input type="hidden" name="token" value="%s">
                                <input type="hidden" name="deviceId" value="%s">
                                
                                <div class="user-option" onclick="selectUser('alice@example.com')">
                                    <strong>Alice Smith</strong><br>
                                    <small>alice@example.com</small>
                                </div>
                                
                                <div class="user-option" onclick="selectUser('bob@example.com')">
                                    <strong>Bob Johnson</strong><br>
                                    <small>bob@example.com</small>
                                </div>
                                
                                <div class="user-option" onclick="selectUser('charlie@example.com')">
                                    <strong>Charlie Brown</strong><br>
                                    <small>charlie@example.com</small>
                                </div>
                                
                                <input type="hidden" name="email" id="selectedEmail">
                                
                                <button type="submit" class="button" id="confirmButton" disabled>Confirm Login</button>
                            </form>
                            
                            <p style="margin-top: 20px; font-size: 14px; color: #6c757d;">
                                <strong>Device:</strong> %s
                            </p>
                        </div>

                        <script>
                            function selectUser(email) {
                                document.getElementById('selectedEmail').value = email;
                                document.getElementById('confirmButton').disabled = false;
                                
                                // Visual feedback
                                const options = document.querySelectorAll('.user-option');
                                options.forEach(opt => opt.classList.remove('selected'));
                                event.currentTarget.classList.add('selected');
                            }
                        </script>
                    </body>
                    </html>
                    """.formatted(token, deviceId, deviceId);
                ctx.html(html);
            } else {
                ctx.html("Invalid or expired QR code");
            }
        });

        // Mobile confirmation endpoint
        javalin.post("/confirm-login", ctx -> {
            String token = ctx.formParam("token");
            String deviceId = ctx.formParam("deviceId");
            String email = ctx.formParam("email");

            User user = authService.confirmLogin(token, deviceId, email);

            if (user != null) {
                String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Login Successful</title>
                        <style>
                            body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                            .container { max-width: 400px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; }
                            .success { color: #28a745; font-size: 48px; margin: 20px 0; }
                            .button { display: inline-block; padding: 10px 20px; background: #007bff; color: white; text-decoration: none; border-radius: 5px; margin: 10px; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="success">✅</div>
                            <h1>Login Successful!</h1>
                            
                            <div style="text-align: left; margin: 20px 0;">
                                <p><strong>Welcome back!</strong></p>
                                <p><strong>Name:</strong> %s</p>
                                <p><strong>Email:</strong> %s</p>
                            </div>
                            
                            <p>You have successfully logged in using QR code authentication.</p>
                            <p>No password was required! 🎉</p>
                            
                            <div style="margin-top: 30px;">
                                <p>You can now return to the computer where you'll be automatically logged in.</p>
                                <a href="/" class="button">Back to Home</a>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(user.displayName, user.email);
                ctx.html(html);
            } else {
                ctx.html("Login failed");
            }
        });

        // Check authentication status (for polling)
        javalin.get("/check-auth/{sessionId}", ctx -> {
            String sessionId = ctx.pathParam("sessionId");
            User user = authService.checkAuthenticationStatus(sessionId);

            Map<String, Object> response = new HashMap<>();
            if (user != null) {
                response.put("authenticated", true);
                response.put("user", user);
                response.put("sessionId", sessionId);
            } else {
                response.put("authenticated", false);
                QRAuthService.SessionStatus status = authService.getSessionStatus(sessionId);
                response.put("status", status != null ? status.toString() : "UNKNOWN");
            }

            ctx.json(response);
        });

        // Dashboard for authenticated users
        javalin.get("/dashboard", ctx -> {
            // In a real app, you'd have proper session management
            // For demo, we'll use query parameter
            String sessionId = ctx.queryParam("sessionId");

            if (sessionId != null) {
                User user = authService.checkAuthenticationStatus(sessionId);
                if (user != null) {
                    String html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>Dashboard</title>
                            <style>
                                body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                                .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                                .welcome { background: #d4edda; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
                                .button { display: inline-block; padding: 10px 20px; background: #007bff; color: white; text-decoration: none; border-radius: 5px; margin: 5px; }
                                .button.logout { background: #dc3545; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="welcome">
                                    <h1>🎉 Welcome, %s!</h1>
                                    <p>You have successfully accessed your account using QR code authentication.</p>
                                </div>

                                <h2>Your Account</h2>
                                <div style="margin: 20px 0;">
                                    <p><strong>User ID:</strong> %s</p>
                                    <p><strong>Email:</strong> %s</p>
                                    <p><strong>Last Login:</strong> Just now</p>
                                </div>

                                <h3>What you can do:</h3>
                                <ul>
                                    <li>View your profile information</li>
                                    <li>Access secured content</li>
                                    <li>Manage your devices</li>
                                    <li>And much more!</li>
                                </ul>

                                <div style="margin-top: 30px; padding: 20px; background: #f8f9fa; border-radius: 5px;">
                                    <h4>💡 Passwordless Authentication</h4>
                                    <p>You accessed this account without ever typing a password! This is more secure and convenient than traditional password-based login.</p>
                                </div>

                                <div style="margin-top: 30px;">
                                    <a href="/generate-login" class="button">Generate New QR Code</a>
                                    <a href="/" class="button logout">Logout</a>
                                </div>
                            </div>
                        </body>
                        </html>
                        """.formatted(user.displayName, user.userId, user.email);
                    ctx.html(html);
                    return;
                }
            }

            ctx.redirect("/");
        });

        // API endpoint to get session status
        javalin.get("/api/session/{sessionId}", ctx -> {
            String sessionId = ctx.pathParam("sessionId");
            QRAuthService.SessionStatus status = authService.getSessionStatus(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("status", status != null ? status.toString() : "NOT_FOUND");

            ctx.json(response);
        });

        // Test endpoint to see all users
        javalin.get("/users", ctx -> {
            StringBuilder html = new StringBuilder("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>All Users</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; }
                        .user { padding: 10px; margin: 5px; background: #f8f9fa; border-radius: 5px; }
                    </style>
                </head>
                <body>
                    <h1>Registered Users</h1>
                """);

            for (User user : authService.getAllUsers()) {
                html.append("""
                    <div class="user">
                        <strong>%s</strong> (%s)<br>
                        <small>ID: %s | Created: %s</small>
                    </div>
                    """.formatted(
                        user.displayName,
                        user.email,
                        user.userId,
                        new java.util.Date(user.createdAt)
                ));
            }

            html.append("""
                    <br>
                    <a href="/">Back to Home</a>
                </body>
                </html>
                """);

            ctx.html(html.toString());
        });
    }

    public void start() {
        setupRoutes(app);

        try {
            app.start(port);
            System.out.println("🚀 Web server started on http://localhost:" + port);
            System.out.println("📱 You can now scan QR codes and they will open in your browser!");
            System.out.println("\n🌐 Available pages:");
            System.out.println("   • http://localhost:" + port + " - Home page");
            System.out.println("   • http://localhost:" + port + "/generate-login - Generate QR code");
            System.out.println("   • http://localhost:" + port + "/users - View all users");
            System.out.println("\n📱 Test the flow:");
            System.out.println("   1. Go to http://localhost:" + port + "/generate-login");
            System.out.println("   2. Use the test link on your phone");
            System.out.println("   3. Select account and confirm");
            System.out.println("   4. Watch automatic login on desktop!");
        } catch (Exception e) {
            // If port is busy, try alternative port
            if (e.getMessage().contains("Port already in use") || e.getMessage().contains("Address already in use")) {
                System.out.println("⚠️  Port " + port + " is busy. Trying alternative port 8081...");
                try {
                    app.stop();
                    Javalin newApp = Javalin.create();
                    setupRoutes(newApp);
                    newApp.start(8081);
                    System.out.println("🚀 Web server started on http://localhost:8081");
                    System.out.println("📱 You can now scan QR codes and they will open in your browser!");
                    System.out.println("\n🌐 Available pages:");
                    System.out.println("   • http://localhost:8081 - Home page");
                    System.out.println("   • http://localhost:8081/generate-login - Generate QR code");
                    System.out.println("   • http://localhost:8081/users - View all users");
                } catch (Exception ex) {
                    System.out.println("❌ Failed to start web server on alternative port: " + ex.getMessage());
                }
            } else {
                System.out.println("❌ Failed to start web server: " + e.getMessage());
            }
        }
    }

    public void stop() {
        app.stop();
    }

    public static void main(String[] args) {
        QRAuthService authService = new QRAuthService("http://localhost:8080");
        QRCodeWebServer server = new QRCodeWebServer(8080, authService);
        server.start();

        // Keep server running
        System.out.println("\nPress Enter to stop the server...");
        try {
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }

        server.stop();
        System.out.println("Web server stopped.");
    }
}