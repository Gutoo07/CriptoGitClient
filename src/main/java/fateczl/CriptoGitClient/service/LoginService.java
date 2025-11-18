package fateczl.CriptoGitClient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LoginService {

    public void register(String nickname, String email, String senha, Settings settings) throws IOException, InterruptedException {
        String serverUrl = settings.getServerUrl();
        String url = serverUrl + "/user";
        String requestBody = "{\"nickname\":\"" + nickname + "\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody)) 
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Converte o JSON para JsonNode (objeto genérico)
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(response.body());

        // Se o cadastro foi bem sucedido
        if (response.statusCode() >= 200 && response.statusCode() < 400) {
            System.out.println("\n" + jsonNode.get("mensagem").asText());
        } else {
            System.err.println("\n" + jsonNode.get("erro").asText());
        }  
    }

    public void login(String email, String password, Settings settings) throws IOException, InterruptedException {
        String serverUrl = settings.getServerUrl();
		String url = serverUrl + "/auth/login";
		String requestBody = "{\"email\":\"" + email + "\",\"senha\":\"" + password + "\"}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Converte o JSON para JsonNode (objeto genérico)
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(response.body());

        // Se o login foi bem sucedido
        if (response.statusCode() >= 200 && response.statusCode() < 400) {
            String token = jsonNode.get("token").asText();        
            
            // Salva o token no arquivo .token
            Files.writeString(Paths.get(".token"), token);
            System.out.println("\n" + jsonNode.get("mensagem").asText());
        } else {
            System.err.println("\n" + jsonNode.get("erro").asText());
        }        
    }
}
