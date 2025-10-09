package fateczl.CriptoGitClient.model;

public class Blob {
    private String hash;
    private byte[] content;
    private String relativePath;

    public void setHash(String hash) {
        this.hash = hash;
    }

    
    public void setContent(byte[] content) {
        this.content = content;
    }    
    
    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }
    
    public String getHash() {
        return hash;
    }
    
    public byte[] getContent() {
        return content;
    }
    
    public String getRelativePath() {
        return relativePath;
    }
}
