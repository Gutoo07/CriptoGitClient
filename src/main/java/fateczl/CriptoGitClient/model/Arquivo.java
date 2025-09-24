package fateczl.CriptoGitClient.model;

public class Arquivo {
    private String name;
    private Blob blob;

    public void setName(String name) {
        this.name = name;
    }

    public void setBlob(Blob blob) {
        this.blob = blob;
    }
    
    public String getName() {
        return name;
    }

    public Blob getBlob() {
        return blob;
    }
}
