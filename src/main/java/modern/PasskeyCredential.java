package modern;

public class PasskeyCredential {
    private final String id;
    private final String username;
    private final String publicKey;

    public PasskeyCredential(String id, String username, String publicKey) {
        this.id = id;
        this.username = username;
        this.publicKey = publicKey;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPublicKey() {
        return publicKey;
    }
}
