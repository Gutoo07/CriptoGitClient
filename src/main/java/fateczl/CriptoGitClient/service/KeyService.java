package fateczl.CriptoGitClient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class KeyService {
     /**
     * Carrega a chave privada da pasta keys
     * @param repositorioPath Caminho do repositório
     * @return Chave privada carregada
     * @throws Exception Se houver erro ao carregar
     */
    public PrivateKey loadPrivateKey(String repositorioPath) throws Exception {
        Path keysPath = Paths.get(repositorioPath, ".criptogit", "keys");
        if (!Files.exists(keysPath)) {
            throw new Exception("Erro: pasta 'keys' não encontrada no repositório");
        }
        
        // Procura por arquivo de chave privada
        try (var stream = Files.list(keysPath)) {
            for (Path keyFile : stream.collect(java.util.stream.Collectors.toList())) {
                String fileName = keyFile.getFileName().toString();
                
                // Processa apenas arquivos de chave privada
                if (fileName.startsWith("private_key") && fileName.endsWith(".pem")) {
                    return loadPrivateKeyFromFile(keyFile);
                }
            }
        }
        
        throw new Exception("Erro: nenhuma chave privada encontrada na pasta keys");
    }

    /**
     * Carrega a chave pública do usuário
     * @param repositorioPath Caminho do repositório
     * @return Chave pública
     * @throws Exception Se houver erro ao carregar a chave pública
     */
    public String getMyPublicKey(String repositorioPath) throws Exception {
        // Lê o arquivo da chave pública
        byte[] publicKeyBytes = Files.readAllBytes(Paths.get(repositorioPath, ".criptogit", "keys", "public_key.pem"));
        
        // Remove headers e footers PEM se existirem
        String publicKeyContent = new String(publicKeyBytes);
        publicKeyContent = publicKeyContent.replace("-----BEGIN PUBLIC KEY-----", "")
                                          .replace("-----END PUBLIC KEY-----", "")
                                          .replaceAll("\\s", "");
        return publicKeyContent;
    }
    
    /**
     * Carrega uma chave privada de um arquivo
     * @param keyFile Arquivo da chave privada
     * @return Chave privada carregada
     * @throws Exception Se houver erro ao carregar
     */
    private PrivateKey loadPrivateKeyFromFile(Path keyFile) throws Exception {
        // Lê o arquivo da chave privada
        byte[] privateKeyBytes = Files.readAllBytes(keyFile);
        
        // Remove headers e footers PEM se existirem
        String privateKeyContent = new String(privateKeyBytes);
        privateKeyContent = privateKeyContent.replace("-----BEGIN PRIVATE KEY-----", "")
                                           .replace("-----END PRIVATE KEY-----", "")
                                           .replaceAll("\\s", "");
        
        // Converte de Base64 para bytes
        byte[] keyBytes = java.util.Base64.getDecoder().decode(privateKeyContent);
        
        // Cria a chave privada
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Carrega todas as chaves simétricas salvas na pasta .criptogit/locked/keys
     * @param lockedPath Caminho da pasta locked
     * @return Lista de chaves simétricas carregadas
     * @throws Exception Se houver erro ao carregar as chaves
     */
    public List<SecretKey> loadSymmetricKeysFromFiles(Path lockedPath) throws Exception {
        List<SecretKey> loadedKeys = new ArrayList<>();
        
        // Obtém o caminho da pasta keys
        Path keysPath = lockedPath.resolve("keys");
        
        // Verifica se a pasta keys existe
        if (!Files.exists(keysPath)) {
            System.out.println("Pasta keys não encontrada: " + keysPath);
            return loadedKeys;
        }
        
        // Lista todos os arquivos na pasta keys
        try (var stream = Files.list(keysPath)) {
            for (Path keyFile : stream.collect(java.util.stream.Collectors.toList())) {
                if (Files.isRegularFile(keyFile)) {
                    try {
                        // Lê os dados da chave do arquivo
                        byte[] keyData = Files.readAllBytes(keyFile);
                        
                        // Verifica se o tamanho é adequado para uma chave AES-256 (32 bytes)
                        if (keyData.length == 32) {
                            SecretKey symmetricKey = new SecretKeySpec(keyData, "AES");
                            loadedKeys.add(symmetricKey);
                            System.out.println("✓ Chave simétrica carregada: " + keyFile.getFileName());
                        } else {
                            System.out.println("⚠ Arquivo de chave com tamanho inválido: " + keyFile.getFileName() + " (" + keyData.length + " bytes)");
                        }
                        
                    } catch (Exception e) {
                        System.out.println("✗ Erro ao carregar chave: " + keyFile.getFileName() + " - " + e.getMessage());
                    }
                }
            }
        }
        
        return loadedKeys;
    }

    /**
     * Limpa todos os arquivos da pasta .criptogit/locked/keys
     * @param lockedPath Caminho da pasta locked
     * @throws Exception Se houver erro ao limpar os arquivos
     */
    public void clearKeysFolder(Path lockedPath) throws Exception {
        // Obtém o caminho da pasta keys
        Path keysPath = lockedPath.resolve("keys");
        
        // Verifica se a pasta keys existe
        if (!Files.exists(keysPath)) {
            System.out.println("Pasta keys não encontrada: " + keysPath);
            return;
        }
        
        // Lista todos os arquivos na pasta keys
        try (var stream = Files.list(keysPath)) {
            int deletedCount = 0;
            for (Path keyFile : stream.collect(java.util.stream.Collectors.toList())) {
                if (Files.isRegularFile(keyFile)) {
                    try {
                        Files.delete(keyFile);
                        deletedCount++;
                        System.out.println("✓ Arquivo removido: " + keyFile.getFileName());
                    } catch (Exception e) {
                        System.out.println("✗ Erro ao remover arquivo: " + keyFile.getFileName() + " - " + e.getMessage());
                    }
                }
            }
            
            if (deletedCount > 0) {
                System.out.println("Total de arquivos removidos da pasta keys: " + deletedCount);
            } else {
                System.out.println("Nenhum arquivo encontrado na pasta keys para remover");
            }
        }
    }

    public boolean exists(Path keysPath, String keyContent) {
        // Lista todos os arquivos na pasta keys
        boolean exists = false;
        // Remove as quebras de linha e espaços do conteúdo da chave
        keyContent = keyContent.replaceAll("\\s", "");
        try (var stream = Files.list(keysPath)) {
            for (Path keyFile : stream.collect(java.util.stream.Collectors.toList())) {
                if (Files.isRegularFile(keyFile)) {
                    // Lê o conteúdo do arquivo
                    byte[] keyData = Files.readAllBytes(keyFile);
                    // Converte o conteúdo do arquivo para uma string
                    String keyContentFile = new String(keyData);
                    keyContentFile = keyContentFile.replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                    // Compara o conteúdo do arquivo com o conteúdo passado como parâmetro
                    if (keyContentFile.equals(keyContent)) {
                        exists = true;
                        break;
                    }
                }
            }
        }
        catch (IOException e) {
            System.out.println("Erro ao listar arquivos na pasta keys: " + e.getMessage());
            exists = false;
        }
        return exists;
    }
}
