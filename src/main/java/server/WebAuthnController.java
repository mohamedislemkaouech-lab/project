package server;

import passkey.CredentialRepository;
import passkey.WebAuthnService;
import modern.PasskeyCredential;
import io.javalin.Javalin;

public class WebAuthnController {

    public static void main(String[] args) {
        // Initialisation du repository et du service WebAuthn
        CredentialRepository repo = new CredentialRepository();
        WebAuthnService service = new WebAuthnService(repo);

        // Démarrage du serveur Javalin sur le port 7000
        Javalin app = Javalin.create().start(7000);

        // 🔹 Enregistrement d'un utilisateur
        app.post("/register", ctx -> {
            String username = ctx.formParam("username");
            if (username == null || username.isEmpty()) {
                ctx.status(400).result("Username requis !");
                return;
            }
            PasskeyCredential cred = service.registerUser(username);
            ctx.json(cred);
        });

        // 🔹 Authentification d'un utilisateur
        app.post("/login", ctx -> {
            String id = ctx.formParam("id");
            String username = ctx.formParam("username");
            if (service.authenticate(id, username)) {
                ctx.result("Authentification réussie !");
            } else {
                ctx.result("Échec de l'authentification !");
            }
        });

        // 🔹 Réinitialiser la passkey d'un utilisateur
        app.post("/reset", ctx -> {
            String username = ctx.formParam("username");
            if (username == null || username.isEmpty()) {
                ctx.status(400).result("Username requis !");
                return;
            }
            PasskeyCredential newCred = service.resetPasskey(username);
            ctx.json(newCred);
        });

        // 🔹 Supprimer un utilisateur
        app.post("/delete", ctx -> {
            String username = ctx.formParam("username");
            if (username == null || username.isEmpty()) {
                ctx.status(400).result("Username requis !");
                return;
            }
            boolean success = service.deleteUser(username);
            ctx.result(success ? "Utilisateur supprimé !" : "Utilisateur non trouvé !");
        });

        System.out.println("Serveur démarré sur http://localhost:7000");
    }
}
