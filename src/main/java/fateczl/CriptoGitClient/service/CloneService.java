package fateczl.CriptoGitClient.service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;

public class CloneService {


    /**
     * Clona um repositório remoto
     * @param repositoryName Nome do repositório a ser clonado
     * @param repositorioPath Caminho do repositório local
     * @param settings Configurações do cliente
     * @throws Exception Se houver erro ao clonar o repositório
     */
    public void clone(String repositoryName, String repositorioPath, Settings settings) throws Exception {
        // Carrega o token do arquivo .token
        String token = Files.readString(Paths.get(".token"));
        if (token == null || token.isEmpty()) {
            throw new Exception("Token não encontrado. Faça login para clonar um repositório remoto.");
        }
        
        // Cria a URL da requisição
        String serverUrl = settings.getServerUrl() + "/git/clone";
        
        // Prepara o body da requisição (form data)
        String formData = "repo_name=" + URLEncoder.encode(repositoryName, StandardCharsets.UTF_8);
        
        // Cria o cliente HTTP
        HttpClient client = HttpClient.newHttpClient();
        
        // Cria a requisição POST com form data
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverUrl))
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", token)
            .build();
        
        // Envia a requisição e recebe a resposta como bytes (arquivo zip)
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        // Verifica se a requisição foi bem-sucedida
        if (response.statusCode() != 200) {
            // Se não for 200, tenta ler como string para ver a mensagem de erro
            String errorMessage = new String(response.body(), StandardCharsets.UTF_8);
            throw new Exception("Erro ao clonar repositório. Status: " + response.statusCode() + " - " + errorMessage);
        }
        
        // Obtém o conteúdo do zip
        byte[] zipBytes = response.body();
        
        // Extrai o zip para o diretório atual com o nome do repositório
        extractZip(zipBytes, repositoryName, repositorioPath);
        
        System.out.println("Repositório " + repositoryName + " clonado com sucesso!");
    }
    
    /**
     * Extrai um arquivo zip para um diretório
     * @param zipBytes Bytes do arquivo zip
     * @param repositoryName Nome do repositório (usado como nome do diretório de destino)
     * @throws Exception Se houver erro ao extrair o zip
     */
    private void extractZip(byte[] zipBytes, String repositoryName, String repositorioPath) throws Exception {
        // Cria o diretório de destino
        Path destDir = Paths.get(repositorioPath, repositoryName);
        if (Files.exists(destDir)) {
            throw new Exception("O diretório " + repositoryName + " já existe.");
        }
        Files.createDirectories(destDir);
        
        // Extrai o zip
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName());
                
                // Previne zip slip attack
                if (!entryPath.normalize().startsWith(destDir.normalize())) {
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