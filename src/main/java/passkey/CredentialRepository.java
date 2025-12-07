package passkey;

import modern.PasskeyCredential;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CredentialRepository {

    private final Map<String, PasskeyCredential> credentialsById = new ConcurrentHashMap<>();

    public void save(PasskeyCredential credential) {
        credentialsById.put(credential.getId(), credential);
        System.out.println("Saved credential for " + credential.getUsername());
    }

    public PasskeyCredential findById(String id) {
        return credentialsById.get(id);
    }

    public PasskeyCredential findByUsername(String username) {
        return credentialsById.values().stream()
                .filter(c -> c.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    public void remove(String username) {
        credentialsById.values().removeIf(c -> c.getUsername().equals(username));
        System.out.println("Removed credential for " + username);
    }

    public void removeById(String id) {
        credentialsById.remove(id);
    }
}
