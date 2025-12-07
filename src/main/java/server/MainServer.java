package server;

import com.sun.net.httpserver.*;
import passkey.*;
import modern.PasskeyCredential;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class MainServer {

    public static void main(String[] args) throws IOException {
        CredentialRepository repo = new CredentialRepository();
        WebAuthnService auth = new WebAuthnService(repo);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/register", exchange -> {
            String username = exchange.getRequestURI().getQuery().split("=")[1];
            PasskeyCredential cred = auth.registerUser(username);
            String response = "Registered user: " + username + ", ID: " + cred.getId();
            sendResponse(exchange, response);
        });

        server.createContext("/login", exchange -> {
            String username = exchange.getRequestURI().getQuery().split("=")[1];
            String sessionId = QRSessionManager.createSession(username);
            sendResponse(exchange, "QR session created: " + sessionId);
        });

        server.createContext("/authenticate", exchange -> {
            String[] params = exchange.getRequestURI().getQuery().split("&");
            String id = params[0].split("=")[1];
            String username = params[1].split("=")[1];
            boolean valid = auth.authenticate(id, username);
            sendResponse(exchange, "Authentication for " + username + " = " + valid);
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on http://localhost:8080");
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.sendResponseHeaders(200, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
