package org.example;

import qrcode.QRAuthService;
import qrcode.QRAuthService.User;
import qrcode.QRCodeGenerator;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Serveur web qui montre UNIQUEMENT votre QR code
 */
public class PersonalAuthServer {

    private ServerSocket serverSocket;
    private final QRAuthService authService;
    private final int port;
    private final String serverIp;
    private String targetUserEmail; // VOTRE email

    public PersonalAuthServer(int port, QRAuthService authService, String userEmail) throws Exception {
        this.port = port;
        this.authService = authService;
        this.serverIp = getLocalIP();
        this.targetUserEmail = userEmail;
    }

    private String getLocalIP() throws Exception {
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

    public void start() {
        try {
            serverSocket = new ServerSocket(port);

            System.out.println("\n" + "=".repeat(60));
            System.out.println("🔐 SERVEUR PERSO POUR: " + targetUserEmail);
            System.out.println("=".repeat(60));
            System.out.println("\n🌐 URLs:");
            System.out.println("   Computer: http://localhost:" + port);
            System.out.println("   Phone:    http://" + serverIp + ":" + port);
            System.out.println("\n📱 Sur votre téléphone:");
            System.out.println("   Visitez: http://" + serverIp + ":" + port);
            System.out.println("=".repeat(60));

            // Démarrer dans un thread séparé
            new Thread(this::handleConnections).start();

        } catch (IOException e) {
            System.out.println("❌ Port " + port + " occupé. Essayez: " + (port + 1));
        }
    }

    private void handleConnections() {
        while (true) {
            try {
                Socket client = serverSocket.accept();
                new Thread(() -> handleRequest(client)).start();
            } catch (IOException e) {
                break;
            }
        }
    }

    private void handleRequest(Socket client) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)
        ) {
            String request = in.readLine();
            if (request == null) return;

            String[] parts = request.split(" ");
            if (parts.length < 2) return;

            String path = parts[1];

            if (path.equals("/") || path.equals("/index.html")) {
                servePersonalQRPage(out);
            } else if (path.startsWith("/login")) {
                serveAutoLogin(out, path);
            } else if (path.startsWith("/auto-login")) {
                serveDirectLogin(out, path);
            } else {
                servePersonalQRPage(out);
            }

        } catch (Exception e) {
            System.out.println("Erreur: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException e) {}
        }
    }

    /**
     * Page avec VOTRE QR code uniquement
     */
    private void servePersonalQRPage(PrintWriter out) {
        try {
            User user = authService.getUserByEmail(targetUserEmail);
            if (user == null) {
                user = authService.registerUser(targetUserEmail, targetUserEmail.split("@")[0]);
            }

            // Générer URL unique pour cet utilisateur
            String token = "PERSONAL_" + System.currentTimeMillis();
            String loginUrl = String.format(
                    "http://%s:%d/auto-login?user=%s&token=%s&auto=1",
                    serverIp, port,
                    java.net.URLEncoder.encode(targetUserEmail, "UTF-8"),
                    token
            );

            // Générer le QR code
            QRCodeGenerator generator = new QRCodeGenerator();
            String qrImage = generator.generateQRCodeDataURL(loginUrl);

            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Votre QR Code Personnel</title>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            text-align: center;
                            padding: 20px;
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                            color: white;
                            min-height: 100vh;
                        }
                        .container {
                            max-width: 500px;
                            margin: 0 auto;
                            background: rgba(255, 255, 255, 0.1);
                            padding: 40px;
                            border-radius: 20px;
                            backdrop-filter: blur(10px);
                        }
                        .qr-code {
                            margin: 30px 0;
                            padding: 20px;
                            background: white;
                            border-radius: 15px;
                            display: inline-block;
                        }
                        .user-info {
                            background: rgba(255, 255, 255, 0.2);
                            padding: 20px;
                            border-radius: 10px;
                            margin: 20px 0;
                        }
                        h1 {
                            font-size: 2.5em;
                            margin-bottom: 10px;
                        }
                        .instructions {
                            text-align: left;
                            background: rgba(255, 255, 255, 0.2);
                            padding: 20px;
                            border-radius: 10px;
                            margin-top: 30px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🔐 Votre QR Code</h1>
                        <p>Scannez pour vous connecter automatiquement</p>
                        
                        <div class="user-info">
                            <h2>%s</h2>
                            <p>%s</p>
                        </div>
                        
                        <div class="qr-code">
                            <img src="%s" width="250" height="250" alt="QR Code">
                        </div>
                        
                        <div class="instructions">
                            <h3>📋 Comment utiliser:</h3>
                            <ol>
                                <li>Ouvrez l'appareil photo de votre téléphone</li>
                                <li>Scannez le QR code ci-dessus</li>
                                <li>Cliquez sur le lien qui apparaît</li>
                                <li>Vous serez connecté automatiquement!</li>
                            </ol>
                        </div>
                        
                        <div style="margin-top: 30px;">
                            <p><small>Ce QR code est unique à votre compte</small></p>
                        </div>
                    </div>
                </body>
                </html>
                """, user.displayName, user.email, qrImage);

            sendResponse(out, html);

        } catch (Exception e) {
            String errorHtml = "<h1>Erreur: " + e.getMessage() + "</h1>";
            sendResponse(out, errorHtml);
        }
    }

    /**
     * Login automatique - redirige directement vers le compte
     */
    private void serveAutoLogin(PrintWriter out, String path) {
        try {
            // Extraire les paramètres
            Map<String, String> params = parseQueryParams(path);
            String userEmail = params.getOrDefault("user", targetUserEmail);

            User user = authService.getUserByEmail(userEmail);
            if (user == null) {
                user = authService.registerUser(userEmail, userEmail.split("@")[0]);
            }

            // Page de succès
            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Login Réussi!</title>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            text-align: center;
                            padding: 50px;
                            background: linear-gradient(135deg, #4CAF50 0%%, #45a049 100%%);
                            color: white;
                        }
                        .success {
                            font-size: 100px;
                            margin: 20px;
                        }
                        .user-card {
                            background: rgba(255, 255, 255, 0.2);
                            padding: 30px;
                            border-radius: 15px;
                            margin: 30px auto;
                            max-width: 400px;
                        }
                    </style>
                </head>
                <body>
                    <div class="success">✅</div>
                    <h1>Login Réussi!</h1>
                    
                    <div class="user-card">
                        <h2>Bienvenue, %s!</h2>
                        <p><strong>Email:</strong> %s</p>
                        <p><strong>Heure:</strong> %s</p>
                    </div>
                    
                    <p>Vous êtes maintenant connecté avec votre compte personnel.</p>
                    <p>Aucun mot de passe nécessaire! 🎉</p>
                </body>
                </html>
                """, user.displayName, user.email, new Date());

            sendResponse(out, html);

        } catch (Exception e) {
            String errorHtml = "<h1>Erreur de login: " + e.getMessage() + "</h1>";
            sendResponse(out, errorHtml);
        }
    }

    /**
     * Login direct - pour les liens manuels
     */
    private void serveDirectLogin(PrintWriter out, String path) {
        try {
            Map<String, String> params = parseQueryParams(path);
            String email = params.getOrDefault("email", targetUserEmail);

            // Page avec redirection automatique
            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Connexion en cours...</title>
                    <meta charset="UTF-8">
                    <meta http-equiv="refresh" content="2;url=/login?user=%s">
                    <style>
                        body {
                            font-family: Arial;
                            text-align: center;
                            padding: 50px;
                        }
                        .loader {
                            border: 8px solid #f3f3f3;
                            border-top: 8px solid #3498db;
                            border-radius: 50%%;
                            width: 60px;
                            height: 60px;
                            animation: spin 2s linear infinite;
                            margin: 30px auto;
                        }
                        @keyframes spin {
                            0%% { transform: rotate(0deg); }
                            100%% { transform: rotate(360deg); }
                        }
                    </style>
                </head>
                <body>
                    <h1>🔐 Connexion en cours...</h1>
                    <div class="loader"></div>
                    <p>Connexion automatique à votre compte...</p>
                </body>
                </html>
                """, java.net.URLEncoder.encode(email, "UTF-8"));

            sendResponse(out, html);

        } catch (Exception e) {
            String errorHtml = "<h1>Erreur: " + e.getMessage() + "</h1>";
            sendResponse(out, errorHtml);
        }
    }

    private Map<String, String> parseQueryParams(String path) {
        Map<String, String> params = new HashMap<>();
        if (path.contains("?")) {
            String query = path.split("\\?")[1];
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    try {
                        params.put(keyValue[0], java.net.URLDecoder.decode(keyValue[1], "UTF-8"));
                    } catch (Exception e) {
                        params.put(keyValue[0], keyValue[1]);
                    }
                }
            }
        }
        return params;
    }

    private void sendResponse(PrintWriter out, String html) {
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: text/html; charset=UTF-8");
        out.println("Content-Length: " + html.getBytes().length);
        out.println("Connection: close");
        out.println();
        out.println(html);
    }

    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            System.out.println("=".repeat(60));
            System.out.println("🔐 SERVEUR PERSO POUR VOTRE COMPTE");
            System.out.println("=".repeat(60));

            System.out.print("\nEntrez VOTRE email: ");
            String email = scanner.nextLine().trim();

            QRAuthService authService = new QRAuthService();
            PersonalAuthServer server = new PersonalAuthServer(8080, authService, email);
            server.start();

            System.out.println("\n✅ Serveur démarré!");
            System.out.println("\nAppuyez sur Entrée pour arrêter...");
            scanner.nextLine();

            server.stop();
            System.out.println("Serveur arrêté.");

        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
    }
}