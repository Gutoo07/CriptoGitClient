package fateczl.CriptoGitClient.service;

import fateczl.CriptoGitClient.model.Blob;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import javax.crypto.Cipher;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PushService {
    
    
    private BlobService blobService;
    private KeyService keyService;
    private AuthService authService;

    /**
     * Envia os arquivos da pasta locked para o servidor
     * @param repositorioPath Caminho do repositório
     * @param repositorioId ID do repositório
     * @param settings Configurações do cliente
     * @throws Exception Se houver erro ao enviar os arquivos
     */
    public void push(String repositorioPath, String repositorioId, Settings settings) throws Exception {
        // Confere se a pasta locked existe
        Path lockedPath = Paths.get(repositorioPath, ".criptogit", "locked");
        if (!Files.exists(lockedPath)) {
            throw new IOException("Pasta locked não existe. Execute o comando init para criar um repositório CriptoGit.");
        }

        // Passo 1: Envia requisição para obter o desafio (mensagem criptografada)
        System.out.println("\nIniciando autenticação com o servidor...");
        keyService = new KeyService();
        authService = new AuthService();
        String encryptedChallenge = authService.obterDesafio(repositorioPath, repositorioId, settings);
        System.out.println("  Desafio recebido do servidor");
        
        // Passo 2: Carrega todos os arquivos da pasta locked
        System.out.println("\nCarregando arquivos da pasta locked...");
        List<Blob> blobs = carregarBlobsDaPastaLocked(lockedPath);
        
        // Se não houver arquivos para enviar, retorna
        if (blobs.isEmpty()) {
            System.out.println("Nenhum arquivo encontrado na pasta locked para enviar.");
            return;
        }
        
        System.out.println("\nEncontrados " + blobs.size() + " arquivos para enviar.");
        
        // Passo 3: Descriptografa a mensagem com a chave privada
        System.out.println("\nDescriptografando mensagem de autenticação...");
        String decryptedMessage = authService.descriptografarDesafio(repositorioPath, encryptedChallenge);
        System.out.println("  Mensagem descriptografada");
        
        // Passo 4: Envia os arquivos junto com a mensagem descriptografada
        System.out.println("\nEnviando arquivos e mensagem de autenticação ao servidor...");
        String publicKey = keyService.getMyPublicKey(repositorioPath);
        blobService = new BlobService();
        blobService.enviarBlobsEmLote(blobs, settings.getServerUrl() + "/git/push", repositorioId, decryptedMessage, publicKey);
        System.out.println(" *** Push realizado com sucesso ***");

        System.out.println("\nApagando arquivos criptografados da pasta locked...");
        try {
            try (var files = Files.list(lockedPath)) {
                files.filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            System.err.println("Erro ao apagar arquivo " + file.getFileName() + ": " + e.getMessage());
                        }
                    });
            }
        } catch (IOException e) {
            throw new IOException("Erro ao listar arquivos da pasta locked: " + e.getMessage(), e);
        }
    }
    
    /**
     * Carrega todos os arquivos da pasta locked e os converte para Blobs
     * @param lockedPath Caminho da pasta locked
     * @return Lista de Blobs
     * @throws IOException Se houver erro ao carregar os arquivos
     */
    private List<Blob> carregarBlobsDaPastaLocked(Path lockedPath) throws IOException {
        List<Blob> blobs = new ArrayList<>();
        
        Files.list(lockedPath)
            .filter(Files::isRegularFile)
            .forEach(file -> {
                try {
                    byte[] content = Files.readAllBytes(file);
                    String hash = file.getFileName().toString();
                    
                    Blob blob = new Blob();
                    blob.setHash(hash);
                    blob.setContent(content);
                    
                    blobs.add(blob);
                } catch (IOException e) {
                    System.err.println("Erro ao carregar arquivo " + file.getFileName() + ": " + e.getMessage());
                }
            });
        
        return blobs;
    }    
    
}
