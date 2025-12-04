package modern;

import passkey.CredentialRepository;
import passkey.WebAuthnService;

public class main {
    public static void main(String[] args) {

        // Créer repository
        CredentialRepository repo = new CredentialRepository();

        // Créer service WebAuthn
        WebAuthnService service = new WebAuthnService(repo);

        // 🔹 Enregistrer un utilisateur
        PasskeyCredential cred = service.registerUser("Alice");
        System.out.println("Utilisateur enregistré : " + cred.getUsername() + ", ID : " + cred.getId());

        // 🔹 Tester authentification correcte
        boolean loginOk = service.authenticate(cred.getId(), "Alice");
        System.out.println("Authentification réussie ? " + loginOk);

        // 🔹 Tester authentification incorrecte
        boolean loginFail = service.authenticate(cred.getId(), "Bob");
        System.out.println("Authentification Bob réussie ? " + loginFail);
    }
}
