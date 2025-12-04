package org.example;

import qrcode.QRAuthService;
import qrcode.QRAuthService.User;
import qrcode.QRAuthService.QRAuthData;
import qrcode.QRAuthService.AuthSession;
import qrcode.QRCodeGenerator;
import qrcode.QRCodeScanner;

import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class Main {

    private static String SERVER_URL;
    private static final Scanner scanner = new Scanner(System.in);
    private static String localIp;
    private static QRCodeWebServer webServer = null;

    public static void main(String[] args) {
        printHeader();

        try {
            // Detect local IP
            localIp = getLocalIP();
            SERVER_URL = "http://" + localIp + ":8080";

            System.out.println("📡 Detected IP: " + localIp);
            System.out.println("🌐 Server URL: " + SERVER_URL);
            System.out.println();

            // Initialize services
            QRAuthService authService = new QRAuthService(SERVER_URL);
            QRCodeGenerator generator = new QRCodeGenerator();
            QRCodeScanner qrScanner = new QRCodeScanner();

            showAvailableUsers(authService);
            printNetworkInfo();

            boolean running = true;

            while (running) {
                printMenu();
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> testQRCodeGeneration(generator);
                    case "2" -> testQRCodeScanning(qrScanner);
                    case "3" -> testAuthenticationFlow(authService);
                    case "4" -> System.out.println("❌ Device pairing not implemented in this version");
                    case "5" -> testCompleteAuthFlow(authService, generator, qrScanner);
                    case "6" -> testPasswordlessLogin(authService, generator, qrScanner);
                    case "7" -> testUserRegistration(authService);
                    case "8" -> showAllUsers(authService);
                    case "9" -> System.out.println("❌ Statistics not implemented in this version");
                    case "10" -> cleanupSessions(authService);
                    case "11" -> runAllTests(authService, generator, qrScanner);
                    case "12" -> startOrStopWebServer(authService);
                    case "13" -> generateMobileTestQR(generator);
                    case "14" -> generateQRForMyAccount(authService, generator);
                    case "15" -> startPersonalServer(authService);
                    case "16" -> startConfirmationServer(authService);
                    case "17" -> generateQRWithServerRunning(generator);
                    case "18" -> checkServerStatus(); // Added missing option 16
                    case "0" -> {
                        stopAllServers();
                        System.out.println("\n👋 Goodbye!");
                        running = false;
                    }
                    default -> System.out.println("❌ Invalid option.");
                }

                if (running && !choice.equals("0")) {
                    System.out.println("\nPress Enter to continue to menu...");
                    scanner.nextLine();
                }
            }

        } catch (Exception e) {
            System.err.println("\n❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private static void printHeader() {
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║       QR CODE AUTHENTICATION SYSTEM                  ║");
        System.out.println("║       Passwordless Login with Mobile QR              ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printNetworkInfo() {
        System.out.println("📡 NETWORK INFORMATION:");
        System.out.println("   Your IP: " + localIp);
        System.out.println("   On phone, visit: http://" + localIp + ":8080");
        System.out.println("   Make sure phone is on same WiFi");
        System.out.println();
    }

    private static String getLocalIP() {
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

    private static void showAvailableUsers(QRAuthService authService) {
        System.out.println("📋 Available Users:");
        List<User> users = authService.getAllUsers();
        if (users.isEmpty()) {
            System.out.println("   No users found. Use option 7 to register users.");
        } else {
            for (User user : users) {
                System.out.println("   • " + user.displayName + " (" + user.email + ")");
            }
        }
        System.out.println();
    }

    private static void printMenu() {
        System.out.println("\n┌──────────────────────────────────────────────────────┐");
        System.out.println("│                  MAIN MENU                           │");
        System.out.println("├──────────────────────────────────────────────────────┤");

        if (webServer != null) {
            System.out.println("│  🟢 WEB SERVER ACTIVE at http://" + localIp + ":8080  │");
            System.out.println("├──────────────────────────────────────────────────────┤");
        }

        System.out.println("│  WEB SERVER                                          │");
        System.out.println("│  12. " + (webServer != null ? "🔴 Stop Web Server" : "🟢 Start Web Server") + "                │");
        System.out.println("│  18. 📊 Check Server Status                          │");
        System.out.println("│                                                      │");
        System.out.println("│  QR CODES (Requires active server)                   │");
        System.out.println("│  13. 📱 Test QR Codes                               │");
        System.out.println("│  14. 🎯 QR for MY account                           │");
        System.out.println("│  17. 🔗 QR with active server                       │");
        System.out.println("│                                                      │");
        System.out.println("│  OTHER TESTS                                         │");
        System.out.println("│  1. Test QR Generation                               │");
        System.out.println("│  2. Test QR Scanning                                 │");
        System.out.println("│  3. Test Auth Flow                                   │");
        System.out.println("│  4. Test Device Pairing                              │");
        System.out.println("│  5. Test Complete Auth                               │");
        System.out.println("│  6. Test Passwordless Login                          │");
        System.out.println("│  7. Register New User                                │");
        System.out.println("│  8. Show All Users                                   │");
        System.out.println("│  9. Show Statistics                                  │");
        System.out.println("│  10. Cleanup Sessions                                │");
        System.out.println("│  11. Run All Tests                                   │");
        System.out.println("│  15. Personal Server (8081)                          │");
        System.out.println("│  16. Confirmation Server (8082)                      │");
        System.out.println("│                                                      │");
        System.out.println("│  0. Exit                                             │");
        System.out.println("└──────────────────────────────────────────────────────┘");
        System.out.print("\nChoice: ");
    }

    /**
     * Option 12: Start/Stop Web Server
     */
    private static void startOrStopWebServer(QRAuthService authService) {
        if (webServer != null) {
            // Stop the server
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🛑 STOPPING WEB SERVER");
            System.out.println("=".repeat(60));

            webServer.stop();
            webServer = null;
            System.out.println("✅ Web server stopped.");
        } else {
            // Start the server
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🚀 STARTING WEB SERVER");
            System.out.println("=".repeat(60));

            try {
                webServer = new QRCodeWebServer(8080, authService);
                webServer.start();

                System.out.println("\n✅ WEB SERVER IS NOW RUNNING!");
                System.out.println("\n📱 IMPORTANT - ACTIVE SERVER:");
                System.out.println("   • The server continues running");
                System.out.println("   • Your phone can now scan QR codes");
                System.out.println("   • Visit: http://" + localIp + ":8080");
                System.out.println("\n💡 To stop the server, choose option 12 again.");
                System.out.println("\n⚠️  DO NOT PRESS ENTER HERE!");
                System.out.println("   The server continues running in background.");

            } catch (Exception e) {
                System.err.println("❌ Error starting web server: " + e.getMessage());
                webServer = null;
            }
        }
    }

    /**
     * Option 18: Check server status
     */
    private static void checkServerStatus() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 SERVER STATUS");
        System.out.println("=".repeat(60));

        if (webServer != null) {
            System.out.println("\n🟢 WEB SERVER IS RUNNING");
            System.out.println("   URL: http://" + localIp + ":8080");
            System.out.println("   Port: 8080");
            System.out.println("\n📱 On your phone:");
            System.out.println("   1. Make sure you're on the same WiFi");
            System.out.println("   2. Open browser");
            System.out.println("   3. Visit: http://" + localIp + ":8080");
            System.out.println("   4. Scan QR codes");
        } else {
            System.out.println("\n🔴 WEB SERVER IS STOPPED");
            System.out.println("   To scan QR codes, start the server first (option 12)");
        }
    }

    /**
     * Option 17: Generate QR with active server
     */
    private static void generateQRWithServerRunning(QRCodeGenerator generator) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔗 GENERATE QR WITH ACTIVE SERVER");
        System.out.println("=".repeat(60));

        if (webServer == null) {
            System.out.println("\n❌ ERROR: Web server is not started!");
            System.out.println("📋 To generate functional QR codes:");
            System.out.println("   1. Choose option 12 to start the server");
            System.out.println("   2. Return to this option");
            System.out.println("   3. Your QR codes will contain active URLs");
            return;
        }

        try {
            System.out.println("\n✅ Active server detected: http://" + localIp + ":8080");

            System.out.print("\nWhat type of QR code do you want to generate?");
            System.out.println("\n1. Home page");
            System.out.println("2. Login page");
            System.out.println("3. Test page");
            System.out.println("4. Custom page");
            System.out.print("Choice: ");

            String qrChoice = scanner.nextLine().trim();
            String url = "";
            String filename = "";

            switch (qrChoice) {
                case "1":
                    url = "http://" + localIp + ":8080";
                    filename = "home-qr.png";
                    break;
                case "2":
                    url = "http://" + localIp + ":8080/generate-login";
                    filename = "login-qr.png";
                    break;
                case "3":
                    url = "http://" + localIp + ":8080/mobile-test";
                    filename = "test-qr.png";
                    break;
                case "4":
                    System.out.print("Enter path (ex: /verify, /users): ");
                    String path = scanner.nextLine().trim();
                    url = "http://" + localIp + ":8080" + (path.startsWith("/") ? path : "/" + path);
                    filename = "custom-" + path.replace("/", "") + "-qr.png";
                    break;
                default:
                    url = "http://" + localIp + ":8080";
                    filename = "default-qr.png";
            }

            generator.generateQRCodeToFile(url, filename);

            System.out.println("\n✅ QR CODE GENERATED: " + filename);
            System.out.println("\n🌐 URL in QR code:");
            System.out.println(url);
            System.out.println("\n📱 TO SCAN:");
            System.out.println("1. Transfer " + filename + " to your phone");
            System.out.println("2. Open image in gallery");
            System.out.println("3. Scan with camera app");
            System.out.println("4. Click the link");
            System.out.println("\n⚠️  MAKE SURE:");
            System.out.println("   • Server is still active (option 12)");
            System.out.println("   • Your phone is on same WiFi");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    /**
     * Stop all servers before quitting
     */
    private static void stopAllServers() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🛑 STOPPING ALL SERVERS");
        System.out.println("=".repeat(60));

        if (webServer != null) {
            webServer.stop();
            System.out.println("✅ Web server stopped.");
        }

        System.out.println("All servers stopped.");
    }

    // Option 13: Generate mobile test QR
    private static void generateMobileTestQR(QRCodeGenerator generator) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("GENERATE MOBILE TEST QR CODES");
        System.out.println("=".repeat(60));

        try {
            System.out.println("\n📱 Generating test QR codes for mobile...");

            // QR 1: Google (for testing scanning works)
            generator.generateQRCodeToFile("https://www.google.com", "mobile-test-google.png");
            System.out.println("✅ QR 1: mobile-test-google.png (opens Google)");

            // QR 2: Your server
            String serverUrl = "http://" + localIp + ":8080";
            generator.generateQRCodeToFile(serverUrl, "mobile-test-server.png");
            System.out.println("✅ QR 2: mobile-test-server.png (opens your server)");
            if (webServer == null) {
                System.out.println("   ⚠️  Note: Server must be running (option 12) for this to work");
            }

            // QR 3: Generate login page
            String generateLoginUrl = serverUrl + "/generate-login";
            generator.generateQRCodeToFile(generateLoginUrl, "mobile-test-generate.png");
            System.out.println("✅ QR 3: mobile-test-generate.png (generate login QR page)");

            // QR 4: Direct verification
            String verifyUrl = serverUrl + "/verify?test=mobile";
            generator.generateQRCodeToFile(verifyUrl, "mobile-test-verify.png");
            System.out.println("✅ QR 4: mobile-test-verify.png (verification page)");

            System.out.println("\n" + "=".repeat(60));
            System.out.println("📋 HOW TO TEST ON YOUR PHONE:");
            System.out.println("=".repeat(60));
            System.out.println("\n1. Transfer PNG files to your phone:");
            System.out.println("   • Email them to yourself");
            System.out.println("   • Use Google Drive/WhatsApp");
            System.out.println("   • Or USB cable");
            System.out.println("\n2. On your phone:");
            System.out.println("   • Open the image in your gallery");
            System.out.println("   • Point your camera at the QR code");
            System.out.println("   • Click the link that appears");
            System.out.println("\n3. Start with mobile-test-google.png");
            System.out.println("   (If Google opens, scanning works!)");
            System.out.println("\n4. Then try mobile-test-server.png");
            System.out.println("   Should open: " + serverUrl);
            System.out.println("\n💡 IMPORTANT: For QR codes 2-4 to work:");
            System.out.println("   • Server must be running (option 12)");
            System.out.println("   • Phone must be on same WiFi");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    /**
     * Option 14: Generate QR for YOUR account
     */
    private static void generateQRForMyAccount(QRAuthService authService, QRCodeGenerator generator) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🎯 QR CODE FOR YOUR ACCOUNT");
        System.out.println("=".repeat(60));

        try {
            System.out.print("\nEnter YOUR email: ");
            String myEmail = scanner.nextLine().trim();

            // Verify/create account
            User myUser = authService.getUserByEmail(myEmail);
            if (myUser == null) {
                System.out.print("Your name: ");
                String displayName = scanner.nextLine().trim();
                myUser = authService.registerUser(myEmail, displayName);
                System.out.println("✅ New account created!");
            }

            System.out.println("\n👤 Your account: " + myUser.displayName);

            // Generate auth session URL
            String authUrl = authService.generateAuthSession(myEmail);

            // Generate the QR code
            String filename = myEmail.split("@")[0] + "-personal-qr.png";
            generator.generateQRCodeToFile(authUrl, filename);

            System.out.println("\n✅ QR CODE GENERATED: " + filename);
            System.out.println("\n🌐 Your personal URL:");
            System.out.println(authUrl);

            if (webServer != null) {
                System.out.println("\n✅ READY TO SCAN!");
                System.out.println("\n📱 ON YOUR PHONE:");
                System.out.println("1. Transfer " + filename + " to your phone");
                System.out.println("2. Open image in gallery");
                System.out.println("3. Scan with camera");
                System.out.println("4. Click the link");
            } else {
                System.out.println("\n⚠️  SERVER INACTIVE - QR CODE WON'T WORK!");
                System.out.println("Start server with option 12 before scanning.");
            }

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Option 15: Start PersonalAuthServer
     */
    private static void startPersonalServer(QRAuthService authService) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔐 PERSONAL SERVER FOR YOUR ACCOUNT");
        System.out.println("=".repeat(60));

        try {
            System.out.print("\nEnter YOUR email: ");
            String email = scanner.nextLine().trim();

            // Create personal server (on different port: 8081)
            PersonalAuthServer server = new PersonalAuthServer(8081, authService, email);
            server.start();

            System.out.println("\n✅ Personal server started on port 8081!");
            System.out.println("\n📱 ON YOUR PHONE:");
            System.out.println("1. Make sure you're on same WiFi");
            System.out.println("2. Open browser");
            System.out.println("3. Visit: http://" + localIp + ":8081");
            System.out.println("\n⚠️  THIS SERVER REMAINS ACTIVE!");
            System.out.println("To stop it, close the application.");
            System.out.println("\nPress Enter to return to menu...");
            scanner.nextLine();

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Other test methods
    private static void testQRCodeGeneration(QRCodeGenerator generator) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST QR CODE GENERATION");
        System.out.println("=".repeat(60));

        try {
            System.out.println("\n📱 Generating test QR codes...");

            // Test URL QR codes (mobile-friendly)
            String testUrl = "https://www.google.com";
            generator.generateQRCodeToFile(testUrl, "test-url.png");
            System.out.println("✓ test-url.png generated");

            String localUrl = "http://" + localIp + ":8080";
            generator.generateQRCodeToFile(localUrl, "test-local.png");
            System.out.println("✓ test-local.png generated");

            System.out.println("\n✅ QR codes generated!");
            System.out.println("\n📱 To test on phone:");
            System.out.println("1. Transfer .png files to phone");
            System.out.println("2. Open image in gallery");
            System.out.println("3. Scan with camera app");
            System.out.println("4. Click the link");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private static void testQRCodeScanning(QRCodeScanner qrScanner) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST QR CODE SCANNING");
        System.out.println("=".repeat(60));

        try {
            System.out.println("\n🔍 Testing QR code scanning...");

            // First create a test QR code
            QRCodeGenerator generator = new QRCodeGenerator();
            String testData = "Test Data: " + System.currentTimeMillis();
            generator.generateQRCodeToFile(testData, "scan-test.png");

            // Scan it
            String scannedData = qrScanner.scanFromFile("scan-test.png");
            System.out.println("Scanned: " + scannedData);

            if (testData.equals(scannedData)) {
                System.out.println("✅ Scan successful!");
            } else {
                System.out.println("❌ Scan failed!");
            }

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private static void testAuthenticationFlow(QRAuthService authService) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST AUTHENTICATION FLOW");
        System.out.println("=".repeat(60));

        try {
            System.out.println("\n👤 Testing authentication flow...");

            // Get a user
            User user = authService.getUserByEmail("alice@example.com");
            if (user == null) {
                System.out.println("❌ User alice@example.com not found");
                return;
            }

            // Generate auth session
            String authUrl = authService.generateAuthSession(user.email);
            System.out.println("✓ Auth URL generated: " + authUrl.substring(0, Math.min(authUrl.length(), 50)) + "...");

            System.out.println("\n✅ Authentication flow test complete");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private static void testCompleteAuthFlow(QRAuthService authService,
                                             QRCodeGenerator generator,
                                             QRCodeScanner scanner) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST COMPLETE AUTH FLOW");
        System.out.println("=".repeat(60));

        try {
            System.out.println("\n🖥️  Desktop: Creating session...");

            // Get a user
            User user = authService.getUserByEmail("bob@example.com");
            if (user == null) {
                System.out.println("❌ User bob@example.com not found");
                return;
            }

            // Generate auth URL
            String authUrl = authService.generateAuthSession(user.email);

            // Generate QR code
            generator.generateQRCodeToFile(authUrl, "complete-auth-qr.png");
            System.out.println("✓ QR code generated: complete-auth-qr.png");
            System.out.println("📱 Mobile URL: " + authUrl);

            System.out.println("\n✅ Complete auth flow test ready");
            System.out.println("\n💡 To test on phone:");
            System.out.println("1. Transfer complete-auth-qr.png to phone");
            System.out.println("2. Scan with camera");
            System.out.println("3. Open the link");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private static void testPasswordlessLogin(QRAuthService authService,
                                              QRCodeGenerator generator,
                                              QRCodeScanner scanner) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST PASSWORDLESS LOGIN");
        System.out.println("=".repeat(60));

        try {
            System.out.println("\n🎯 Generating login QR...");

            // Get a user
            User user = authService.getUserByEmail("alice@example.com");
            if (user == null) {
                System.out.println("❌ User alice@example.com not found");
                return;
            }

            // Generate QR data
            QRAuthData qrData = authService.generateLoginQR();

            System.out.println("✓ QR data generated:");
            System.out.println("   Session: " + qrData.sessionId);
            System.out.println("   Mobile URL: " + qrData.url);

            // Generate QR code image
            generator.generateQRCodeToFile(qrData.url, "passwordless-qr.png");
            System.out.println("✓ QR code saved: passwordless-qr.png");

            System.out.println("\n📱 On your phone:");
            System.out.println("1. Visit: " + qrData.url);
            System.out.println("2. Or scan passwordless-qr.png");
            System.out.println("3. Select account and confirm");

            System.out.println("\n✅ Passwordless login test ready");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private static void testUserRegistration(QRAuthService authService) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("REGISTER NEW USER");
        System.out.println("=".repeat(60));

        try {
            System.out.print("\nEnter email: ");
            String email = scanner.nextLine().trim();

            System.out.print("Enter display name: ");
            String displayName = scanner.nextLine().trim();

            User newUser = authService.registerUser(email, displayName);

            if (newUser != null) {
                System.out.println("\n✅ User registered!");
                System.out.println("   ID: " + newUser.userId);
                System.out.println("   Name: " + newUser.displayName);
                System.out.println("   Email: " + newUser.email);
            }

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private static void showAllUsers(QRAuthService authService) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("ALL USERS");
        System.out.println("=".repeat(60));

        List<User> users = authService.getAllUsers();

        if (users.isEmpty()) {
            System.out.println("No users registered.");
        } else {
            System.out.println("\nTotal: " + users.size() + " users");
            for (User user : users) {
                System.out.println("\n─ " + user.displayName + " ─");
                System.out.println("  Email: " + user.email);
                System.out.println("  ID: " + user.userId);
            }
        }
    }

    private static void cleanupSessions(QRAuthService authService) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("CLEANUP SESSIONS");
        System.out.println("=".repeat(60));

        authService.cleanupExpiredSessions();
        System.out.println("✓ Cleanup completed");
    }

    private static void runAllTests(QRAuthService authService,
                                    QRCodeGenerator generator,
                                    QRCodeScanner scanner) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("RUNNING ALL TESTS");
        System.out.println("=".repeat(60));

        testQRCodeGeneration(generator);
        testQRCodeScanning(scanner);
        testAuthenticationFlow(authService);
        System.out.println("❌ Skipping device pairing test (not implemented)");
        testCompleteAuthFlow(authService, generator, scanner);
        testPasswordlessLogin(authService, generator, scanner);
        System.out.println("\n✅ ALL TESTS COMPLETED");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("✅ ALL TESTS COMPLETED");
        System.out.println("=".repeat(60));
    }

    private static void startConfirmationServer(QRAuthService authService) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔐 QR CODE CONFIRMATION SERVER");
        System.out.println("=".repeat(60));

        try {
            QRCodeConfirmationServer server = new QRCodeConfirmationServer(8082, authService);
            server.start();

            System.out.println("\n✅ Server started on port 8082!");
            System.out.println("\n📱 ON YOUR PHONE:");
            System.out.println("   Visit: http://" + localIp + ":8082");
            System.out.println("\n⏳ Press Enter to return to menu...");
            scanner.nextLine();

            server.stop();

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }
}