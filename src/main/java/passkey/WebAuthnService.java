package passkey;

import modern.PasskeyCredential;
import java.util.UUID;

public class WebAuthnService {

    private final CredentialRepository repository;

    public WebAuthnService(CredentialRepository repository) {
        this.repository = repository;
    }

    // 🔹 Enregistrer un utilisateur
    public PasskeyCredential registerUser(String username) {
        String id = UUID.randomUUID().toString();
        PasskeyCredential credential = new PasskeyCredential(id, username, "FAKE_PUBLIC_KEY");
        repository.save(credential);
        return credential;
    }

    // 🔹 Authentification simple
    public boolean authenticate(String id, String username) {
        PasskeyCredential credential = repository.findById(id);
        return credential != null && credential.getUsername().equals(username);
    }

    // 🔹 Réinitialiser la passkey d'un utilisateur
    public PasskeyCredential resetPasskey(String username) {
        // Supprimer l'ancienne passkey
        PasskeyCredential oldCred = repository.findByUsername(username);
        if (oldCred != null) {
            repository.remove(username);
        }
        // Créer et enregistrer une nouvelle passkey
        return registerUser(username);
    }

    // 🔹 Supprimer un utilisateur
    public boolean deleteUser(String username) {
        PasskeyCredential cred = repository.findByUsername(username);
        if (cred != null) {
            repository.remove(username);
            return true;
        }
        return false;
    }
}
