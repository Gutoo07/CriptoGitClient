package fateczl.CriptoGitClient.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class UnlockService {
    
    private List<SecretKey> decryptedSymmetricKeys = new ArrayList<>();
    private Set<String> processedFiles = new HashSet<>();
    
    /**
     * Descriptografa todos os arquivos na pasta locked usando a chave privada do usuário
     * @param repositorioPath Caminho do repositório
     * @throws Exception Se houver erro na descriptografia
     */
    public void unlock(String repositorioPath) throws Exception {
        System.out.println("Iniciando processo de unlock...");
        
        // Carrega a chave privada do usuário
        PrivateKey privateKey = loadPrivateKey(repositorioPath);
        System.out.println("Chave privada carregada com sucesso");
        
        // Cria a pasta locked se não existir
        Path lockedPath = Paths.get(repositorioPath, ".criptogit", "locked");
        if (!Files.exists(lockedPath)) {
            Files.createDirectories(lockedPath);
            System.out.println("Pasta locked criada em: " + lockedPath);
        }
        
        // Primeira fase: tenta descriptografar com a chave privada
        System.out.println("\n=== FASE 1: Descriptografando com chave privada ===");
        decryptWithPrivateKey(lockedPath, privateKey);
        
        // Segunda fase: tenta descriptografar com as chaves simétricas obtidas
        System.out.println("\n=== FASE 2: Descriptografando com chaves simétricas ===");
        decryptWithSymmetricKeys(lockedPath);
        
        System.out.println("\nProcesso de unlock concluído!");
        System.out.println("Chaves simétricas obtidas: " + decryptedSymmetricKeys.size());
        System.out.println("Arquivos processados: " + processedFiles.size());
    }
    
    /**
     * Carrega a chave privada da pasta keys
     * @param repositorioPath Caminho do repositório
     * @return Chave privada carregada
     * @throws Exception Se houver erro ao carregar
     */
    private PrivateKey loadPrivateKey(String repositorioPath) throws Exception {
        Path keysPath = Paths.get(repositorioPath, "keys");
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
     * Tenta descriptografar todos os arquivos na pasta locked usando a chave privada
     * @param lockedPath Caminho da pasta locked
     * @param privateKey Chave privada do usuário
     * @throws Exception Se houver erro na descriptografia
     */
    private void decryptWithPrivateKey(Path lockedPath, PrivateKey privateKey) throws Exception {
        // Lista todos os arquivos na pasta locked
        try (var stream = Files.walk(lockedPath)) {
            for (Path file : stream.collect(java.util.stream.Collectors.toList())) {
                if (Files.isRegularFile(file)) {
                    tryDecryptWithPrivateKey(file, privateKey);
                }
            }
        }
    }
    
    /**
     * Tenta descriptografar um arquivo específico com a chave privada
     * @param file Arquivo a ser descriptografado
     * @param privateKey Chave privada
     */
    private void tryDecryptWithPrivateKey(Path file, PrivateKey privateKey) {
        try {
            byte[] encryptedData = Files.readAllBytes(file);
            
            // Tenta descriptografar com RSA
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            // Se chegou até aqui, a descriptografia foi bem-sucedida
            // Verifica se o resultado parece ser uma chave simétrica (32 bytes para AES-256)
            if (decryptedData.length == 32) {
                SecretKey symmetricKey = new SecretKeySpec(decryptedData, "AES");
                decryptedSymmetricKeys.add(symmetricKey);
                processedFiles.add(file.toString());
                
                System.out.println("✓ Chave simétrica descriptografada: " + file.getFileName());
            } else {
                System.out.println("✗ Arquivo não é uma chave simétrica: " + file.getFileName());
            }
            
        } catch (Exception e) {
            // Se falhou, não é uma chave simétrica criptografada com esta chave privada
            // Não imprime erro para não poluir o output
        }
    }
    
    /**
     * Tenta descriptografar todos os arquivos na pasta locked usando as chaves simétricas obtidas
     * @param lockedPath Caminho da pasta locked
     * @throws Exception Se houver erro na descriptografia
     */
    private void decryptWithSymmetricKeys(Path lockedPath) throws Exception {
        if (decryptedSymmetricKeys.isEmpty()) {
            System.out.println("Nenhuma chave simétrica foi obtida na fase anterior");
            return;
        }
        
        // Lista todos os arquivos na pasta locked
        try (var stream = Files.walk(lockedPath)) {
            for (Path file : stream.collect(java.util.stream.Collectors.toList())) {
                if (Files.isRegularFile(file) && !processedFiles.contains(file.toString())) {
                    tryDecryptWithSymmetricKeys(file);
                }
            }
        }
    }
    
    /**
     * Tenta descriptografar um arquivo específico com todas as chaves simétricas disponíveis
     * @param file Arquivo a ser descriptografado
     */
    private void tryDecryptWithSymmetricKeys(Path file) {
        for (SecretKey symmetricKey : decryptedSymmetricKeys) {
            try {
                byte[] encryptedData = Files.readAllBytes(file);
                
                // Tenta descriptografar com AES
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, symmetricKey);
                byte[] decryptedData = cipher.doFinal(encryptedData);
                
                // Se chegou até aqui, a descriptografia foi bem-sucedida
                // Agora tenta descriptografar o nome do arquivo com a mesma chave
                String decryptedFileName = tryDecryptFileName(file.getFileName().toString(), symmetricKey);
                
                // Salva o arquivo descriptografado com o nome descriptografado
                Path decryptedFile = file.getParent().resolve(decryptedFileName);
                Files.write(decryptedFile, decryptedData);
                
                processedFiles.add(file.toString());
                
                System.out.println("✓ Arquivo descriptografado: " + file.getFileName() + " -> " + decryptedFileName);
                
                // Tenta interpretar o conteúdo descriptografado
                tryInterpretDecryptedContent(decryptedData, decryptedFile);
                
                break; // Se conseguiu descriptografar, não precisa tentar outras chaves
                
            } catch (Exception e) {
                // Se falhou, tenta a próxima chave simétrica
            }
        }
    }
    
    /**
     * Tenta descriptografar o nome do arquivo usando a chave simétrica
     * @param encryptedFileName Nome do arquivo criptografado
     * @param symmetricKey Chave simétrica para descriptografia
     * @return Nome descriptografado ou nome original com .decrypted se falhar
     */
    private String tryDecryptFileName(String encryptedFileName, SecretKey symmetricKey) {
        try {
            // Converte o nome do arquivo de hex para bytes
            byte[] encryptedFileNameBytes = hexStringToByteArray(encryptedFileName);
            
            // Tenta descriptografar o nome com AES
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, symmetricKey);
            byte[] decryptedFileNameBytes = cipher.doFinal(encryptedFileNameBytes);
            
            // Converte os bytes descriptografados para string
            String decryptedFileName = new String(decryptedFileNameBytes);
            
            System.out.println("  → Nome descriptografado: " + decryptedFileName);
            return decryptedFileName;
            
        } catch (Exception e) {
            // Se falhou, usa o nome original com .decrypted
            return encryptedFileName + ".decrypted";
        }
    }
    
    /**
     * Converte uma string hexadecimal para array de bytes
     * @param hexString String hexadecimal
     * @return Array de bytes
     */
    private byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                                 + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }
    
    /**
     * Tenta interpretar o conteúdo descriptografado para identificar o tipo de objeto
     * @param decryptedData Dados descriptografados
     * @param decryptedFile Arquivo descriptografado
     */
    private void tryInterpretDecryptedContent(byte[] decryptedData, Path decryptedFile) {
        try {
            String content = new String(decryptedData);
            
            if (content.startsWith("tree ")) {
                System.out.println("  → Tipo: Tree object");
            } else if (content.startsWith("blob ")) {
                System.out.println("  → Tipo: Blob object");
            } else if (content.contains("author ") && content.contains("date ") && content.contains("message ")) {
                System.out.println("  → Tipo: Commit object");
            } else {
                System.out.println("  → Tipo: Conteúdo de arquivo");
            }
            
        } catch (Exception e) {
            System.out.println("  → Tipo: Dados binários");
        }
    }
}
