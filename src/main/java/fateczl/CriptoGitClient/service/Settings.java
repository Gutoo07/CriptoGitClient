package fateczl.CriptoGitClient.service;

public class Settings {
    private String serverUrl;

    public Settings() {
        this.serverUrl = "http://localhost:8083/webhook";
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getServerUrl() {
        return serverUrl;
    }
}
