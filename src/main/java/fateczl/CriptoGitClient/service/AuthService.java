package fateczl.CriptoGitClient.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

import javax.crypto.Cipher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AuthService {

    private KeyService keyService;

    public AuthService() {
        this.keyService = new KeyService();
    }

    /**
     * Obtém o desafio (mensagem criptografada) do servidor
     * Envia a chave pública para o servidor e recebe uma mensagem criptografada
     * 
     * @param repositorioPath Caminho do repositório
     * @param repositorioId ID do repositório
     * @param settings Configurações do cliente
     * @return Mensagem criptografada em Base64
     * @throws Exception Se houver erro ao obter o desafio
     */
    public String obterDesafio(String repositorioPath, String repositorioId, Settings settings) throws Exception {
        try {
            String serverUrl = settings.getServerUrl();
            
            // Envia a chave pública ao servidor
            String publicKey = keyService.getMyPublicKey(repositorioPath);
            String authUrl = serverUrl + "/git/authenticate";
            
            // Prepara o body da requisição usando ObjectMapper para evitar problemas com caracteres especiais
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode requestJson = objectMapper.createObjectNode();
            requestJson.put("repo_id", repositorioId);
            requestJson.put("public_key", publicKey);
            String requestBody = objectMapper.writeValueAsString(requestJson);
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authUrl))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", "application/json")
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Verifica se a requisição foi bem-sucedida
            if (response.statusCode() != 200) {
                throw new Exception("Erro ao obter desafio: Status " + response.statusCode() + " - " + response.body());
            }
            
            // Recebe a mensagem criptografada do servidor
            JsonNode jsonNode = objectMapper.readTree(response.body());
            
            if (!jsonNode.has("encrypted_message")) {
                throw new Exception("Resposta do servidor não contém 'encrypted_message'");
            }
            
            return jsonNode.get("encrypted_message").asText();
            
        } catch (Exception e) {
            throw new Exception("Erro ao obter desafio do servidor: " + e.getMessage(), e);
        }
    }
    
    /**
     * Descriptografa o desafio recebido do servidor usando a chave privada
     * 
     * @param repositorioPath Caminho do repositório
     * @param encryptedChallenge Mensagem criptografada em Base64
     * @return Mensagem descriptografada (string UTF-8)
     * @throws Exception Se houver erro ao descriptografar
     */
    public String descriptografarDesafio(String repositorioPath, String encryptedChallenge) throws Exception {
        try {
            // Decodifica a mensagem criptografada de Base64
            byte[] encryptedMessage = Base64.getDecoder().decode(encryptedChallenge);
            
            // Descriptografa a mensagem com a chave privada
            PrivateKey privateKey = keyService.loadPrivateKey(repositorioPath);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedMessage = cipher.doFinal(encryptedMessage);
            
            // Converte os bytes descriptografados para string UTF-8 (não codifica em Base64)
            return new String(decryptedMessage, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new Exception("Erro ao descriptografar desafio: " + e.getMessage(), e);
        }
    }
      
}
