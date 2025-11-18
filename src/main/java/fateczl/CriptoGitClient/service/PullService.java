package fateczl.CriptoGitClient.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

import java.io.ByteArrayInputStream;

public class PullService {

    private KeyService keyService;

    /**
     * Puxa um repositório remoto para o repositório local
     * No momento, o pull realiza a mesma ação de um clone.
     * @param repositorioId ID do repositório remoto
     * @param repositorioPath Caminho do repositório local
     * @param settings Configurações do cliente
     * @throws Exception Se houver erro ao puxar o repositório
     */
    public void pull(String repositorioId, String repositorioPath, Settings settings) throws Exception {
        // Carrega a chave pública do usuário
        keyService = new KeyService();
        String publicKey = keyService.getMyPublicKey(repositorioPath);
        // Carrega o token do arquivo .token
        String token = Files.readString(Paths.get(".token"));
        if (token == null || token.isEmpty()) {
            System.err.println("\nErro: Token não encontrado. Faça login para clonar um repositório remoto.");
            return;
        }
        // Coloca o repositorio no body da requisição
        String formData = "repo_id=" + URLEncoder.encode(repositorioId, StandardCharsets.UTF_8);
        // Cria a URL da requisição
        String url = settings.getServerUrl() + "/git/clone";
        // Envia a requisição
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", token)
            .build();
        // Recebe a resposta como bytes (arquivo zip)
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        // Verifica se a requisição foi bem-sucedida
        if (response.statusCode() != 200) {
            // Se não for 200, tenta ler como string para ver a mensagem de erro
            String errorMessage = new String(response.body(), StandardCharsets.UTF_8);
            throw new Exception("Erro ao fazer pull do repositório. Status: " + response.statusCode() + " - " + errorMessage);
        }
        
        // Obtém o conteúdo do zip
        byte[] zipBytes = response.body();
        
        // Extrai o zip para .criptogit/locked
        extractZipToLocked(zipBytes, repositorioPath);
        
        System.out.println("Arquivos extraídos com sucesso para .criptogit/clone");
    }

    /**
     * Testa a conexão com o servidor
     * @throws InterruptedException Se houver erro ao testar a conexão
     * @throws IOException Se houver erro ao testar a conexão
     */
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

    
    
    /**
     * Extrai um arquivo zip para o diretório .criptogit/locked
     * @param zipBytes Bytes do arquivo zip
     * @param repositorioPath Caminho do repositório
     * @throws Exception Se houver erro ao extrair o zip
     */
    private void extractZipToLocked(byte[] zipBytes, String repositorioPath) throws Exception {
        // Cria o diretório de destino .criptogit/clone
        Path lockedDir = Paths.get(repositorioPath, ".criptogit", "locked");
        Files.createDirectories(lockedDir);
        
        // Extrai o zip
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = lockedDir.resolve(entry.getName());

                if (!Files.exists(entryPath)) {                
                    // Previne zip slip attack
                    if (!entryPath.normalize().startsWith(lockedDir.normalize())) {
                        throw new Exception("Entrada inválida no zip: " + entry.getName());
                    }
                    
                    if (entry.isDirectory()) {
                        // Cria o diretório
                        Files.createDirectories(entryPath);
                    } else {
                        // Cria os diretórios pais se necessário
                        if (entryPath.getParent() != null) {
                            Files.createDirectories(entryPath.getParent());
                        }
                        
                        // Escreve o arquivo
                        Files.copy(zipInputStream, entryPath);
                    }
                    
                    zipInputStream.closeEntry();
                }
            }
        }
    }
}
