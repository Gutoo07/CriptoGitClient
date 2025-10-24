package fateczl.CriptoGitClient.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.net.URI;

public class PullService {
    public void pull(String repositorio, String repositorioPath) throws Exception {
        // Carrega a chave pública do usuário
        String publicKey = getMyPublicKey(repositorioPath);

        // Carrega as configurações do servidor
        Settings settings = new Settings();
        String serverUrl = settings.getServerUrl();
        // Cria a URL da requisição
        String url = serverUrl + "/pull" + "?repositorio=" + java.net.URLEncoder.encode(repositorio, "UTF-8") + 
                              "&publickey=" + java.net.URLEncoder.encode(publicKey, "UTF-8");
        
        // Envia a requisição
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        // Recebe a resposta
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }
    public void test() throws InterruptedException, IOException {
        Settings settings = new Settings();
        String serverUrl = settings.getServerUrl();
        String url = serverUrl + "/";
        System.out.println("Testando conexão com o servidor " + url + "...");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }
    public String getMyPublicKey(String repositorioPath) throws Exception {
        // Lê o arquivo da chave pública
        byte[] publicKeyBytes = Files.readAllBytes(Paths.get(repositorioPath, ".criptogit", "keys", "public_key.pem"));
        
        // Remove headers e footers PEM se existirem
        String publicKeyContent = new String(publicKeyBytes);
        publicKeyContent = publicKeyContent.replace("-----BEGIN PUBLIC KEY-----", "")
                                          .replace("-----END PUBLIC KEY-----", "")
                                          .replaceAll("\\s", "");
        return publicKeyContent;
    }
}
