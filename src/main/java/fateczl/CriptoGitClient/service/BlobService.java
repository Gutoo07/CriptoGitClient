package fateczl.CriptoGitClient.service;

import fateczl.CriptoGitClient.model.Blob;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class BlobService {
    
    private final RestTemplate restTemplate;
    
    public BlobService() {
        this.restTemplate = new RestTemplate();
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
     * Os blobs são compactados em um arquivo ZIP e enviados como multipart/form-data
     * 
     * @param blobs Lista de blobs para enviar
     * @param serverUrl URL do servidor
     * @param repositorioName Nome do repositório a ser enviado junto com o ZIP
     * @return ResponseEntity com a resposta do servidor
     * @throws Exception se houver erro na requisição
     */
    public ResponseEntity<String> enviarBlobsEmLote(List<Blob> blobs, String serverUrl, String repositorioName) throws Exception {
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
            // Criar ZIP em memória com os blobs
            ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream();
            
            try (ZipOutputStream zip = new ZipOutputStream(zipOutputStream)) {
                for (Blob blob : blobs) {
                    // Usar o hash como nome do arquivo no ZIP, ou relativePath se disponível
                    String fileName = blob.getHash();
                    
                    // Criar entrada no ZIP
                    ZipEntry entry = new ZipEntry(fileName);
                    zip.putNextEntry(entry);
                    
                    // Escrever o conteúdo do blob no ZIP
                    zip.write(blob.getContent());
                    zip.closeEntry();
                }
            } catch (IOException e) {
                throw new Exception("Erro ao criar arquivo ZIP: " + e.getMessage(), e);
            }

            // Lê o token do arquivo .token
            String token = Files.readString(Paths.get(".token"));
            if (token == null || token.isEmpty()) {
                throw new Exception("Token não encontrado. Faça login para enviar commits.");
            }
            
            // Obter bytes do ZIP
            byte[] zipBytes = zipOutputStream.toByteArray();
            
            // Criar o recurso do arquivo ZIP
            ByteArrayResource zipResource = new ByteArrayResource(zipBytes) {
                @Override
                public String getFilename() {
                    return "blobs.zip";
                }
            };
            
            // Preparar multipart form data
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("zip_file", zipResource);
            body.add("repo_name", repositorioName);
            
            // Preparar headers para multipart/form-data
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", token);
            
            // Criar a entidade HTTP com multipart form data
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
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
    
}
