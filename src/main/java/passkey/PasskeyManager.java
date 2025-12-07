package passkey;

import modern.PasskeyCredential;
import java.util.UUID;

public class PasskeyManager {

    private final CredentialRepository repository;

    public PasskeyManager(CredentialRepository repository) {
        this.repository = repository;
    }

    public PasskeyCredential createPasskey(String username) {
        String id = UUID.randomUUID().toString();
        String fakeKey = "FAKE_PUBLIC_KEY_" + UUID.randomUUID();
        PasskeyCredential credential = new PasskeyCredential(id, username, fakeKey);
        repository.save(credential);
        System.out.println("Created passkey for " + username);
        return credential;
    }

    public boolean validate(String id, String username) {
        PasskeyCredential cred = repository.findById(id);
        boolean valid = cred != null && cred.getUsername().equals(username);
        System.out.println("Validation for " + username + " = " + valid);
        return valid;
    }
}
