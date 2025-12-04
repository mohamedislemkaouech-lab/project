package passkey;

import modern.PasskeyCredential;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CredentialRepository {

    // Stockage des passkeys : id → PasskeyCredential
    private final Map<String, PasskeyCredential> credentialsById = new ConcurrentHashMap<>();

    // 🔹 Enregistrer une passkey
    public void save(PasskeyCredential credential) {
        credentialsById.put(credential.getId(), credential);
    }

    // 🔹 Chercher par ID
    public PasskeyCredential findById(String id) {
        return credentialsById.get(id);
    }

    // 🔹 Chercher par username
    public PasskeyCredential findByUsername(String username) {
        return credentialsById.values().stream()
                .filter(c -> c.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    // 🔹 Supprimer par username
    public void remove(String username) {
        credentialsById.values().removeIf(c -> c.getUsername().equals(username));
    }

    // 🔹 Supprimer par ID (optionnel)
    public void removeById(String id) {
        credentialsById.remove(id);
    }
}
