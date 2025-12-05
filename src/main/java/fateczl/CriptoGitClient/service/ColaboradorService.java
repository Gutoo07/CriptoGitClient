package fateczl.CriptoGitClient.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ColaboradorService {
    
    private AuthService authService;
    private KeyService keyService;
    
    public ColaboradorService() {
        this.authService = new AuthService();
        this.keyService = new KeyService();
    }
    
    public void addCollaborator(String email, String collaboratorPublicKey, String repositorioId, String repositorioPath, Settings settings) throws Exception {
        // 1. Obter o desafio do servidor
        String encryptedChallenge = authService.obterDesafio(repositorioPath, repositorioId, settings);
        
        // 2. Descriptografar o desafio
        String decryptedChallenge = authService.descriptografarDesafio(repositorioPath, encryptedChallenge);
        
        // 3. Obter a chave pública do usuário para usar como Authorization
        String myPublicKey = keyService.getMyPublicKey(repositorioPath);
        
        // 4. Preparar o body da requisição incluindo o decryptedChallenge
        String serverUrl = settings.getServerUrl();
        String url = serverUrl + "/repos/add_colaborador";
        
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode requestJson = objectMapper.createObjectNode();
        requestJson.put("email", email);
        requestJson.put("public_key", collaboratorPublicKey);
        requestJson.put("repo_id", repositorioId);
        requestJson.put("decrypted_challenge", decryptedChallenge);
        String requestBody = objectMapper.writeValueAsString(requestJson);
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .header("Content-Type", "application/json")
            .header("Authorization", myPublicKey)
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 400) {
            System.err.println("Erro ao adicionar colaborador: " + response.body());
        } else {
            System.out.println("Colaborador adicionado com sucesso.");
        }
    }
}
