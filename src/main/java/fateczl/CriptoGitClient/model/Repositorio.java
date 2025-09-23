package fateczl.CriptoGitClient.model;

public class Repositorio {
    private String name;
    private String path;

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }
    
    public String getName() {
        return name;
    }
    
    public String getPath() {
        return path;
    }
}
