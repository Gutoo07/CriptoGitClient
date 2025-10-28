package fateczl.CriptoGitClient.service;

import fateczl.CriptoGitClient.model.Blob;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class BlobService {
    
    private final RestTemplate restTemplate;
    
    public BlobService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Envia um Blob para um servidor externo via requisição HTTP POST
     * 
     * @param blob O objeto Blob contendo hash SHA-1 e conteúdo em bytes
     * @param serverUrl URL do servidor externo (ex: "http://localhost:8080/api/blobs")
     * @return ResponseEntity com a resposta do servidor
     * @throws Exception se houver erro na requisição
     */
    public ResponseEntity<String> enviarBlobParaServidor(Blob blob, String serverUrl) throws Exception {
        // Validações básicas
        if (blob == null) {
            throw new IllegalArgumentException("Blob não pode ser nulo");
        }
        
        if (blob.getHash() == null || blob.getHash().trim().isEmpty()) {
            throw new IllegalArgumentException("Hash do Blob não pode ser nulo ou vazio");
        }
        
        if (blob.getContent() == null) {
            throw new IllegalArgumentException("Conteúdo do Blob não pode ser nulo");
        }
        
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("URL do servidor não pode ser nula ou vazia");
        }
        
        try {
            // Preparar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Preparar o corpo da requisição
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("hash", blob.getHash());
            requestBody.put("content", blob.getContent());
            requestBody.put("relativePath", blob.getRelativePath());
            
            // Criar a entidade HTTP
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            // Enviar a requisição POST
            ResponseEntity<String> response = restTemplate.exchange(
                serverUrl,
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            return response;
            
        } catch (HttpClientErrorException e) {
            throw new Exception("Erro do cliente HTTP (4xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            throw new Exception("Erro do servidor HTTP (5xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new Exception("Erro de conexão com o servidor: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new Exception("Erro inesperado ao enviar Blob: " + e.getMessage(), e);
        }
    }
    
    /**
     * Versão alternativa que envia o conteúdo como Base64 (mais seguro para JSON)
     * 
     * @param blob O objeto Blob contendo hash SHA-1 e conteúdo em bytes
     * @param serverUrl URL do servidor externo
     * @return ResponseEntity com a resposta do servidor
     * @throws Exception se houver erro na requisição
     */
    public ResponseEntity<String> enviarBlobComoBase64(Blob blob, String serverUrl) throws Exception {
        // Validações básicas
        if (blob == null) {
            throw new IllegalArgumentException("Blob não pode ser nulo");
        }
        
        if (blob.getHash() == null || blob.getHash().trim().isEmpty()) {
            throw new IllegalArgumentException("Hash do Blob não pode ser nulo ou vazio");
        }
        
        if (blob.getContent() == null) {
            throw new IllegalArgumentException("Conteúdo do Blob não pode ser nulo");
        }
        
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("URL do servidor não pode ser nula ou vazia");
        }
        
        try {
            // Preparar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Converter conteúdo para Base64
            String contentBase64 = java.util.Base64.getEncoder().encodeToString(blob.getContent());
            
            // Preparar o corpo da requisição
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("hash", blob.getHash());
            requestBody.put("content", contentBase64);
            requestBody.put("encoding", "base64");
            
            // Criar a entidade HTTP
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            // Enviar a requisição POST
            ResponseEntity<String> response = restTemplate.exchange(
                serverUrl,
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            return response;
            
        } catch (HttpClientErrorException e) {
            throw new Exception("Erro do cliente HTTP (4xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            throw new Exception("Erro do servidor HTTP (5xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new Exception("Erro de conexão com o servidor: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new Exception("Erro inesperado ao enviar Blob: " + e.getMessage(), e);
        }
    }
    
    /**
     * Método para testar a conectividade com o servidor
     * 
     * @param serverUrl URL do servidor
     * @return true se o servidor estiver acessível, false caso contrário
     */
    public boolean testarConectividade(String serverUrl) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(serverUrl + "/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Envia múltiplos blobs em uma única requisição (envio em lote)
     * 
     * @param blobs Lista de blobs para enviar
     * @param serverUrl URL do servidor
     * @return ResponseEntity com a resposta do servidor
     * @throws Exception se houver erro na requisição
     */
    public ResponseEntity<String> enviarBlobsEmLote(List<Blob> blobs, String serverUrl) throws Exception {
        // Validações básicas
        if (blobs == null || blobs.isEmpty()) {
            throw new IllegalArgumentException("Lista de blobs não pode ser nula ou vazia");
        }
        
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("URL do servidor não pode ser nula ou vazia");
        }
        
        // Validar cada blob
        for (int i = 0; i < blobs.size(); i++) {
            Blob blob = blobs.get(i);
            if (blob == null) {
                throw new IllegalArgumentException("Blob[" + i + "] não pode ser nulo");
            }
            if (blob.getHash() == null || blob.getHash().trim().isEmpty()) {
                throw new IllegalArgumentException("Hash do Blob[" + i + "] não pode ser nulo ou vazio");
            }
            if (blob.getContent() == null) {
                throw new IllegalArgumentException("Conteúdo do Blob[" + i + "] não pode ser nulo");
            }
        }
        
        try {
            // Preparar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Preparar o corpo da requisição
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("blobs", blobs.stream().map(blob -> {
                Map<String, Object> blobData = new HashMap<>();
                blobData.put("hash", blob.getHash());
                blobData.put("content", java.util.Base64.getEncoder().encodeToString(blob.getContent()));
                return blobData;
            }).collect(Collectors.toList()));
            requestBody.put("totalBlobs", blobs.size());
            requestBody.put("encoding", "base64");
            
            // Criar a entidade HTTP
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            // Enviar a requisição POST
            ResponseEntity<String> response = restTemplate.exchange(
                serverUrl,
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            return response;
            
        } catch (HttpClientErrorException e) {
            throw new Exception("Erro do cliente HTTP (4xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            throw new Exception("Erro do servidor HTTP (5xx): " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new Exception("Erro de conexão com o servidor: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new Exception("Erro inesperado ao enviar blobs em lote: " + e.getMessage(), e);
        }
    }
    
    /**
     * Envia múltiplos blobs individualmente (uma requisição por blob)
     * 
     * @param blobs Lista de blobs para enviar
     * @param serverUrl URL do servidor
     * @param paralelo Se true, envia os blobs em paralelo; se false, envia sequencialmente
     * @return Lista de resultados para cada blob
     */
    public List<ResultadoEnvioBlob> enviarBlobsIndividualmente(List<Blob> blobs, String serverUrl, boolean paralelo) {
        // Validações básicas
        if (blobs == null || blobs.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            return blobs.stream()
                .map(blob -> new ResultadoEnvioBlob(blob.getHash(), false, "URL do servidor não pode ser nula ou vazia"))
                .collect(Collectors.toList());
        }
        
        if (paralelo) {
            return enviarBlobsEmParalelo(blobs, serverUrl);
        } else {
            return enviarBlobsSequencialmente(blobs, serverUrl);
        }
    }
    
    /**
     * Envia blobs sequencialmente (um por vez)
     */
    private List<ResultadoEnvioBlob> enviarBlobsSequencialmente(List<Blob> blobs, String serverUrl) {
        List<ResultadoEnvioBlob> resultados = new ArrayList<>();
        
        for (Blob blob : blobs) {
            try {
                ResponseEntity<String> response = enviarBlobComoBase64(blob, serverUrl);
                resultados.add(new ResultadoEnvioBlob(blob.getHash(), true, 
                    "Sucesso - Status: " + response.getStatusCode()));
            } catch (Exception e) {
                resultados.add(new ResultadoEnvioBlob(blob.getHash(), false, e.getMessage()));
            }
        }
        
        return resultados;
    }
    
    /**
     * Envia blobs em paralelo usando ExecutorService
     */
    private List<ResultadoEnvioBlob> enviarBlobsEmParalelo(List<Blob> blobs, String serverUrl) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(blobs.size(), 10)); // Máximo 10 threads
        List<Future<ResultadoEnvioBlob>> futures = new ArrayList<>();
        
        // Submeter todas as tarefas
        for (Blob blob : blobs) {
            Future<ResultadoEnvioBlob> future = executor.submit(() -> {
                try {
                    ResponseEntity<String> response = enviarBlobComoBase64(blob, serverUrl);
                    return new ResultadoEnvioBlob(blob.getHash(), true, 
                        "Sucesso - Status: " + response.getStatusCode());
                } catch (Exception e) {
                    return new ResultadoEnvioBlob(blob.getHash(), false, e.getMessage());
                }
            });
            futures.add(future);
        }
        
        // Coletar resultados
        List<ResultadoEnvioBlob> resultados = new ArrayList<>();
        for (Future<ResultadoEnvioBlob> future : futures) {
            try {
                resultados.add(future.get(30, TimeUnit.SECONDS)); // Timeout de 30 segundos
            } catch (Exception e) {
                resultados.add(new ResultadoEnvioBlob("unknown", false, "Timeout ou erro: " + e.getMessage()));
            }
        }
        
        executor.shutdown();
        return resultados;
    }
    
    /**
     * Classe para representar o resultado do envio de um blob
     */
    public static class ResultadoEnvioBlob {
        private final String hash;
        private final boolean sucesso;
        private final String mensagem;
        
        public ResultadoEnvioBlob(String hash, boolean sucesso, String mensagem) {
            this.hash = hash;
            this.sucesso = sucesso;
            this.mensagem = mensagem;
        }
        
        public String getHash() { return hash; }
        public boolean isSucesso() { return sucesso; }
        public String getMensagem() { return mensagem; }
        
        @Override
        public String toString() {
            return String.format("Blob[hash=%s, sucesso=%s, mensagem=%s]", hash, sucesso, mensagem);
        }
    }
}
