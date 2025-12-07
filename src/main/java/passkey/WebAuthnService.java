package passkey;

import modern.PasskeyCredential;

public class WebAuthnService {

    private final CredentialRepository repository;

    public WebAuthnService(CredentialRepository repository) {
        this.repository = repository;
    }

    public PasskeyCredential registerUser(String username) {
        PasskeyManager manager = new PasskeyManager(repository);
        return manager.createPasskey(username);
    }

    public boolean authenticate(String id, String username) {
        PasskeyManager manager = new PasskeyManager(repository);
        return manager.validate(id, username);
    }

    public PasskeyCredential resetPasskey(String username) {
        PasskeyCredential old = repository.findByUsername(username);
        if (old != null) repository.remove(username);
        return registerUser(username);
    }

    public boolean deleteUser(String username) {
        PasskeyCredential cred = repository.findByUsername(username);
        if (cred != null) {
            repository.remove(username);
            return true;
        }
        return false;
    }
}
