package org.example;

import qrcode.QRCodeGenerator;
import qrcode.QRAuthService;
import qrcode.QRAuthService.User;
import java.util.Scanner;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Générateur de QR codes pour mobile avec compte spécifique
 */
public class MobileQRGenerator {

    private final QRCodeGenerator qrGenerator;
    private final QRAuthService authService;
    private final String serverIp;
    private final int port;

    public MobileQRGenerator(QRAuthService authService) throws Exception {
        this.qrGenerator = new QRCodeGenerator();
        this.authService = authService;
        this.serverIp = getLocalIP();
        this.port = 8080;
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

    /**
     * Génère un QR code POUR VOTRE COMPTE uniquement
     */
    public void generateQRForMyAccount(String userEmail) {
        try {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🔐 GÉNÉRATION QR POUR VOTRE COMPTE");
            System.out.println("=".repeat(60));

            // Trouver votre utilisateur
            User user = authService.getUserByEmail(userEmail);
            if (user == null) {
                System.out.println("❌ Compte non trouvé: " + userEmail);
                System.out.println("Création d'un nouveau compte...");
                user = authService.registerUser(userEmail, userEmail.split("@")[0]);
            }

            System.out.println("✅ Compte trouvé: " + user.displayName);

            // Générer token unique
            String token = "USER_" + System.currentTimeMillis();
            String sessionId = "SESS_" + user.userId;

            // URL avec VOTRE compte pré-sélectionné
            String mobileUrl = String.format(
                    "http://%s:%d/login?user=%s&token=%s&session=%s",
                    serverIp, port,
                    java.net.URLEncoder.encode(userEmail, "UTF-8"),
                    token,
                    sessionId
            );

            // Afficher l'URL
            System.out.println("\n🌐 URL PERSONNALISÉE POUR VOUS:");
            System.out.println(mobileUrl);

            // Générer le QR code
            String qrFilename = "my-account-qr.png";
            qrGenerator.generateQRCodeToFile(mobileUrl, qrFilename);

            System.out.println("\n✅ QR CODE GÉNÉRÉ: " + qrFilename);
            System.out.println("\n📱 SUR VOTRE TÉLÉPHONE:");
            System.out.println("1. Transférez " + qrFilename + " sur votre téléphone");
            System.out.println("2. Ouvrez l'image dans la galerie");
            System.out.println("3. Scannez avec l'appareil photo");
            System.out.println("4. Vous serez connecté automatiquement!");

            // Afficher aussi un QR code dans la console
            displayTextQR(mobileUrl);

        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Affiche un QR code texte dans la console
     */
    private void displayTextQR(String url) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📋 URL À COPIER MANUELLEMENT:");
        System.out.println(url);
        System.out.println("=".repeat(60));
    }

    /**
     * Page web simple qui affiche VOTRE QR code
     */
    public String generatePersonalQRPage(String userEmail) {
        try {
            User user = authService.getUserByEmail(userEmail);
            if (user == null) {
                return "<h1>❌ Compte non trouvé</h1>";
            }

            String token = "TOKEN_" + System.currentTimeMillis();
            String personalUrl = String.format(
                    "http://%s:%d/auto-login?user=%s&token=%s",
                    serverIp, port,
                    java.net.URLEncoder.encode(userEmail, "UTF-8"),
                    token
            );

            // Générer le QR code en base64
            String qrBase64 = qrGenerator.generateQRCodeDataURL(personalUrl);

            return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Votre QR Code Personnel</title>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { font-family: Arial; padding: 20px; text-align: center; }
                        .user-info { background: #e7f3ff; padding: 15px; border-radius: 10px; margin: 20px; }
                        .qr-container { margin: 30px; padding: 20px; border: 3px solid #007bff; border-radius: 15px; display: inline-block; }
                        .instructions { text-align: left; max-width: 500px; margin: 0 auto; }
                    </style>
                </head>
                <body>
                    <h1>🔐 Votre QR Code Personnel</h1>
                    
                    <div class="user-info">
                        <h3>Compte: %s</h3>
                        <p><strong>Nom:</strong> %s</p>
                        <p><strong>Email:</strong> %s</p>
                    </div>
                    
                    <div class="qr-container">
                        <h3>📱 Scannez ceci avec votre téléphone:</h3>
                        <img src="%s" width="300" height="300" alt="QR Code">
                        <p><small>Ce QR code est unique à votre compte</small></p>
                    </div>
                    
                    <div class="instructions">
                        <h3>📋 Instructions:</h3>
                        <ol>
                            <li>Ouvrez l'appareil photo de votre téléphone</li>
                            <li>Poinez vers le QR code ci-dessus</li>
                            <li>Cliquez sur le lien qui apparaît</li>
                            <li>Vous serez connecté automatiquement!</li>
                        </ol>
                    </div>
                </body>
                </html>
                """, user.displayName, user.displayName, user.email, qrBase64);

        } catch (Exception e) {
            return "<h1>Erreur: " + e.getMessage() + "</h1>";
        }
    }

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            System.out.println("=".repeat(60));
            System.out.println("🎯 GÉNÉRATEUR QR PERSO");
            System.out.println("=".repeat(60));

            System.out.print("\nEntrez votre email: ");
            String email = scanner.nextLine().trim();

            QRAuthService authService = new QRAuthService();
            MobileQRGenerator generator = new MobileQRGenerator(authService);

            // Option 1: Générer fichier PNG
            generator.generateQRForMyAccount(email);

            // Option 2: Générer page web
            System.out.println("\n\n" + "=".repeat(60));
            System.out.println("🌐 PAGE WEB AVEC VOTRE QR CODE:");
            System.out.println("=".repeat(60));

            String htmlPage = generator.generatePersonalQRPage(email);
            System.out.println(htmlPage);

        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
        }
    }
}