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
    
    public void push(String repositorioPath) throws Exception {
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
        
        // Tenta enviar usando estratégia híbrida
        enviarBlobsComEstrategiaHibrida(blobs);
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
                    String relativePath = file.getFileName().toString();
                    
                    Blob blob = new Blob();
                    blob.setHash(hash);
                    blob.setContent(content);
                    
                    blobs.add(blob);
                    System.out.println("Carregado: " + relativePath + " (hash: " + hash + ")");
                } catch (IOException e) {
                    System.err.println("Erro ao carregar arquivo " + file.getFileName() + ": " + e.getMessage());
                }
            });
        
        return blobs;
    }
    
    /**
     * Estratégia híbrida: tenta envio em lote primeiro, fallback para individual
     */
    private void enviarBlobsComEstrategiaHibrida(List<Blob> blobs) throws Exception {
        settings = new Settings();
        String serverUrl = settings.getServerUrl() + "/push-json";
        
        System.out.println("Tentando envio em lote...");
        
        try {
            // Primeira tentativa: envio em lote
            blobService = new BlobService();
            blobService.enviarBlobsEmLote(blobs, serverUrl);
            System.out.println("✅ Envio em lote realizado com sucesso!");
            
        } catch (Exception e) {
            System.out.println("❌ Envio em lote falhou: " + e.getMessage());
            System.out.println("Tentando envio individual com paralelização...");
            
            // Fallback: envio individual em paralelo
            List<BlobService.ResultadoEnvioBlob> resultados = 
                blobService.enviarBlobsIndividualmente(blobs, serverUrl, true);
            
            // Analisa resultados
            long sucessos = resultados.stream().filter(BlobService.ResultadoEnvioBlob::isSucesso).count();
            long falhas = resultados.size() - sucessos;
            
            System.out.println("📊 Resultados do envio individual:");
            System.out.println("   ✅ Sucessos: " + sucessos);
            System.out.println("   ❌ Falhas: " + falhas);
            
            // Mostra detalhes das falhas
            resultados.stream()
                .filter(resultado -> !resultado.isSucesso())
                .forEach(resultado -> 
                    System.out.println("   ❌ Falha - " + resultado.getHash() + ": " + resultado.getMensagem())
                );
            
            // Se houve falhas, tenta reenviar os que falharam
            if (falhas > 0) {
                tentarReenviarBlobsFalhados(blobs, resultados, serverUrl);
            }
        }
    }
    
    /**
     * Tenta reenviar os blobs que falharam no envio individual
     */
    private void tentarReenviarBlobsFalhados(List<Blob> blobs, 
                                           List<BlobService.ResultadoEnvioBlob> resultados, 
                                           String serverUrl) {
        System.out.println("\n🔄 Tentando reenviar blobs que falharam...");
        
        List<String> hashesFalhados = resultados.stream()
            .filter(resultado -> !resultado.isSucesso())
            .map(BlobService.ResultadoEnvioBlob::getHash)
            .collect(Collectors.toList());
        
        List<Blob> blobsParaReenviar = blobs.stream()
            .filter(blob -> hashesFalhados.contains(blob.getHash()))
            .collect(Collectors.toList());
        
        // Reenvio sequencial (mais confiável para retry)
        List<BlobService.ResultadoEnvioBlob> resultadosRetry = 
            blobService.enviarBlobsIndividualmente(blobsParaReenviar, serverUrl, false);
        
        long sucessosRetry = resultadosRetry.stream()
            .filter(BlobService.ResultadoEnvioBlob::isSucesso)
            .count();
        
        System.out.println("🔄 Retry - Sucessos: " + sucessosRetry + "/" + blobsParaReenviar.size());
        
        // Mostra falhas persistentes
        resultadosRetry.stream()
            .filter(resultado -> !resultado.isSucesso())
            .forEach(resultado -> 
                System.out.println("   ❌ Falha persistente - " + resultado.getHash() + ": " + resultado.getMensagem())
            );
    }
  
}
