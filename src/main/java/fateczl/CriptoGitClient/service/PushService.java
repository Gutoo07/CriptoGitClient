package fateczl.CriptoGitClient.service;

import fateczl.CriptoGitClient.model.Blob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PushService {
    
    @Autowired
    private BlobService blobService;
    
    @Autowired
    private Settings settings;
    
    public void push(String repositorioPath, String repositorioName, Settings settings) throws Exception {
        // Envia os arquivos da pasta locked para o servidor
        Path lockedPath = Paths.get(repositorioPath, ".criptogit", "locked");
        if (!Files.exists(lockedPath)) {
            throw new IOException("Pasta locked não existe. Execute o comando init para criar um repositório CriptoGit.");
        }
        
        // Carrega todos os arquivos da pasta locked
        System.out.println("\nCarregando arquivos da pasta locked...");
        List<Blob> blobs = carregarBlobsDaPastaLocked(lockedPath);
        
        if (blobs.isEmpty()) {
            System.out.println("Nenhum arquivo encontrado na pasta locked para enviar.");
            return;
        }
        
        System.out.println("\nEncontrados " + blobs.size() + " arquivos para enviar.");
        
        // Envia os blobs para o servidor, todos de uma vez
        blobService = new BlobService();
        blobService.enviarBlobsEmLote(blobs, settings.getServerUrl() + "/git/push", repositorioName);
        System.out.println(" *** Push realizado com sucesso ***");
    }
    
    /**
     * Carrega todos os arquivos da pasta locked e os converte para Blobs
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
