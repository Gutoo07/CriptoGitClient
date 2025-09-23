package fateczl.CriptoGitClient.model;

public class Blob {
    private String hash;
    private byte[] content;

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
    
    public String getHash() {
        return hash;
    }

    public byte[] getContent() {
        return content;
    }
}
