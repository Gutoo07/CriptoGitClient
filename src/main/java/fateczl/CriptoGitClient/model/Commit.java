package fateczl.CriptoGitClient.model;

public class Commit {
    private String hash;
    private String message;
    private String author;
    private String date;
    private Tree rootTree;
    private String parentHash;

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setRootTree(Tree rootTree) {
        this.rootTree = rootTree;
    }

    public void setParent(String parentHash) {
        this.parentHash = parentHash;
    }

    public String getHash() {
        return hash;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public String getDate() {
        return date;
    }

    public Tree getRootTree() {
        return rootTree;
    }

    public String getParentHash() {
        return parentHash;
    }
}
