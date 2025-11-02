package fateczl.CriptoGitClient.service;

import fateczl.CriptoGitClient.model.Repositorio;
import fateczl.CriptoGitClient.model.Blob;
import fateczl.CriptoGitClient.model.Index;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class RepositorioService {
    private Repositorio repositorio;
    private Index index;
    
    public RepositorioService() {
        this.repositorio = new Repositorio();
        this.index = new Index();
    }
    
    /**
     * Retorna o repositório atual
     * @return Repositório atual
     */
    public Repositorio getRepositorio() {
        return repositorio;
    }

    public Index getIndex() {
        return index;
    }
    
    /**
     * Inicializa o repositório local e cria os diretórios necessários
     * @param path Caminho do repositório
     * @throws Exception Se houver erro ao inicializar o repositório ou criar os diretórios
     */
    public void init(String path) throws Exception {
        // Remove aspas duplas do início e fim do caminho se existirem
        path = path.trim();
        if (path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        
        // Verifica se o diretório existe
        Path directoryPath = Paths.get(path);
        
        if (!Files.exists(directoryPath)) {
            System.err.println("\nErro: Diretório não existe.");
            return;
        }
        
        if (!Files.isDirectory(directoryPath)) {
            System.err.println("\nErro: O caminho especificado não é um diretório.");
            return;
        }
        
        repositorio = new Repositorio();
        repositorio.setPath(path);
        
        // Obtém o nome da última pasta do caminho usando o separador correto do sistema
        String separator = System.getProperty("file.separator");
        String name = path.substring(path.lastIndexOf(separator) + 1);
        repositorio.setName(name);

        // Cria o diretório .criptogit se não existir
        Path criptogitPath = Paths.get(path, ".criptogit");
        if (!Files.exists(criptogitPath)) {
            try {
                Files.createDirectory(criptogitPath);
                           
            } catch (IOException e) {
                throw new IOException("Erro ao criar o diretório .criptogit.");
            }
        }
        // Cria o diretório objects dentro do .criptogit se não existir
        Path objectsPath = Paths.get(path, ".criptogit", "objects");            
        if (!Files.exists(objectsPath)) {
            try {
            Files.createDirectory(objectsPath);     
            } catch (IOException e) {
                throw new IOException("Erro ao criar o diretório objects.");
            }
        }
        // Cria o diretório keys dentro do .criptogit se não existir
        Path keysPath = Paths.get(path, ".criptogit", "keys");
        if (!Files.exists(keysPath)) {
            try {
                Files.createDirectory(keysPath);
            } catch (IOException e) {
                throw new IOException("Erro ao criar o diretório keys.");
            }
        }

        // Cria o arquivo index se não existir
        Path indexPath = Paths.get(path, ".criptogit", "index");
        index = new Index();
        if (!Files.exists(indexPath)) {
            Files.write(indexPath, new byte[0]);
        } else {
            // Se o arquivo index existir, lê todas as linhas e cria uma lista de blobs para usar em memória
            List<String> indexBlobs = Files.readAllLines(indexPath);
            List<Blob> blobs = new ArrayList<>();
            for (String indexBlob : indexBlobs) {
                // Ignora linhas vazias
                if (indexBlob.isEmpty()) {
                    continue;
                }
                Blob blob = new Blob();
                blob.setHash(indexBlob.split(" ")[0]);
                blob.setRelativePath(indexBlob.split(" ")[1]);
                blobs.add(blob);                
            }
            index.setBlobs(blobs);
        }
        
        System.out.println("Repositório inicializado com sucesso: " + repositorio.getName());       
    }

    /**
     * Cria um repositório remoto no servidor
     * @param repositoryName Nome do repositório
     * @throws Exception Se houver erro ao criar o repositório remoto
     */
    public void createRemoteRepository(String repositoryName, Settings settings) throws Exception {
        // Carrega o token do arquivo .token
        String token = Files.readString(Paths.get(".token"));
        if (token == null || token.isEmpty()) {
            throw new Exception("Token não encontrado. Faça login para criar um repositório remoto.");
        }
        // Cria a URL da requisição
        String serverUrl = settings.getServerUrl() + "/repos";
        // Cria o corpo da requisição usando Jackson ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> requestBodyMap = new HashMap<>();
        requestBodyMap.put("nome", repositoryName);
        String requestBody = objectMapper.writeValueAsString(requestBodyMap);
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverUrl))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .header("Content-Type", "application/json")
            .header("Authorization", token)
            .build();
        // Envia a requisição
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());        
        System.out.println(response.body());
    }

    /**
     * Lista todos os repositórios remotos do servidor
     */
    public void listRemoteRepositories(Settings settings) throws Exception {
        // Carrega o token do arquivo .token
        String token = Files.readString(Paths.get(".token"));
        if (token == null || token.isEmpty()) {
            throw new Exception("Token não encontrado. Faça login para listar os repositórios remotos.");
        }
        // Cria a URL da requisição
        String serverUrl = settings.getServerUrl() + "/repos";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverUrl))
            .GET()
            .header("Authorization", token)
            .build();
        // Envia a requisição
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Converte o JSON para JsonNode (objeto genérico)
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(response.body());
        System.out.println("\n*** Repositórios remotos ***");
        for (JsonNode repo : jsonNode) {
            System.out.println("--------------------------------");
            System.out.println("#" + repo.get("id").asText() + " - " + repo.get("nome").asText());
        }
        System.out.println("--------------------------------\n");
    }
}
