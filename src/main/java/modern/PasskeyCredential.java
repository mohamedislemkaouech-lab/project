package modern;

public class PasskeyCredential {
    private String id;
    private String username;
    private String publicKey;

    // Constructeur
    public PasskeyCredential(String id, String username, String publicKey) {
        this.id = id;
        this.username = username;
        this.publicKey = publicKey;
    }

    // Getters
    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPublicKey() { return publicKey; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
}
