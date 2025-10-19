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
    private int keyCounter = 1; // Contador para nomear as chaves salvas
    
    /**
     * Descriptografa todos os arquivos na pasta locked usando a chave privada do usuário
     * @param repositorioPath Caminho do repositório
     * @throws Exception Se houver erro na descriptografia
     */
    public void unlock(String repositorioPath) throws Exception {
        System.out.println("Iniciando processo de unlock...");
        
        // Cria a pasta locked se não existir
        Path lockedPath = Paths.get(repositorioPath, ".criptogit", "locked");
        if (!Files.exists(lockedPath)) {
            Files.createDirectories(lockedPath);
            System.out.println("Pasta locked criada em: " + lockedPath);
            return;
        }
        // Cria a pasta unlocked se não existir
        Path unlockedPath = Paths.get(repositorioPath, ".criptogit", "unlocked");
        if (!Files.exists(unlockedPath)) {
            Files.createDirectories(unlockedPath);
            System.out.println("Pasta unlocked criada em: " + unlockedPath);
        }        
        // Cria a pasta locked/keys se não existir
        Path lockedKeysPath = Paths.get(repositorioPath, ".criptogit", "locked", "keys");
        if (!Files.exists(lockedKeysPath)) {
            Files.createDirectories(lockedKeysPath);
            System.out.println("Pasta locked/keys criada em: " + lockedKeysPath);
        }        

        // Carrega a chave privada do usuário
        PrivateKey privateKey = loadPrivateKey(repositorioPath);
        System.out.println("Chave privada carregada com sucesso");       
        
        // Primeira fase: tenta descriptografar com a chave privada
        System.out.println("\n=== FASE 1: Descriptografando com chave privada ===");
        decryptWithPrivateKey(lockedPath, privateKey);
        
        // Segunda fase: tenta descriptografar com as chaves simétricas obtidas
        System.out.println("\n=== FASE 2: Descriptografando com chaves simétricas ===");
        decryptWithSymmetricKeys(lockedPath, unlockedPath);
        
        // Terceira fase: limpa os arquivos temporários da pasta keys
        System.out.println("\n=== FASE 3: Limpando arquivos temporários ===");
        clearKeysFolder(lockedPath);
        
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
        // Lista apenas os arquivos diretamente dentro da pasta locked (não recursivo)
        try (var stream = Files.list(lockedPath)) {
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
                
                // Salva a chave simétrica descriptografada na pasta .criptogit/locked/keys
                saveDecryptedSymmetricKey(decryptedData, file);
                
                // Apaga o arquivo criptografado
                Files.delete(file);
                
                System.out.println("✓ Chave simétrica descriptografada: " + file.getFileName());
                System.out.println("  → Chave salva em: .criptogit/locked/keys/" + (keyCounter - 1));
                System.out.println("  → Arquivo criptografado removido");
            } else {
                System.out.println("✗ Arquivo não é uma chave simétrica: " + file.getFileName());
            }
            
        } catch (Exception e) {
            // Se falhou, não é uma chave simétrica criptografada com esta chave privada
            // Não imprime erro para não poluir o output
        }
    }
    
    /**
     * Salva uma chave simétrica descriptografada na pasta .criptogit/locked/keys
     * @param decryptedKeyData Dados da chave simétrica descriptografada
     * @param originalFile Arquivo original criptografado (para obter o caminho do repositório)
     */
    private void saveDecryptedSymmetricKey(byte[] decryptedKeyData, Path originalFile) throws Exception {
        // Obtém o caminho do repositório a partir do arquivo original
        Path repositorioPath = originalFile.getParent().getParent().getParent();
        Path keysPath = Paths.get(repositorioPath.toString(), ".criptogit", "locked", "keys");        
        
        // Nome do arquivo baseado no contador incremental
        String fileName = String.valueOf(keyCounter);
        Path keyFile = keysPath.resolve(fileName);
        
        // Salva a chave simétrica descriptografada
        Files.write(keyFile, decryptedKeyData);
        
        // Incrementa o contador para a próxima chave
        keyCounter++;
    }
    
    /**
     * Carrega todas as chaves simétricas salvas na pasta .criptogit/locked/keys
     * @param lockedPath Caminho da pasta locked
     * @return Lista de chaves simétricas carregadas
     * @throws Exception Se houver erro ao carregar as chaves
     */
    private List<SecretKey> loadSymmetricKeysFromFiles(Path lockedPath) throws Exception {
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
     * Tenta descriptografar todos os arquivos na pasta locked usando as chaves simétricas obtidas
     * @param lockedPath Caminho da pasta locked
     * @param unlockedPath Caminho da pasta unlocked
     * @throws Exception Se houver erro na descriptografia
     */
    private void decryptWithSymmetricKeys(Path lockedPath, Path unlockedPath) throws Exception {
        // Carrega as chaves simétricas dos arquivos salvos na pasta locked/keys
        List<SecretKey> loadedSymmetricKeys = loadSymmetricKeysFromFiles(lockedPath);
        
        if (loadedSymmetricKeys.isEmpty()) {
            System.out.println("Nenhuma chave simétrica foi encontrada na pasta locked/keys");
            return;
        }
        
        System.out.println("Carregadas " + loadedSymmetricKeys.size() + " chaves simétricas dos arquivos");
        
        // Lista apenas os arquivos diretamente dentro da pasta locked que ainda não foram processados
        List<Path> unprocessedFiles = new ArrayList<>();
        try (var stream = Files.list(lockedPath)) {
            for (Path file : stream.collect(java.util.stream.Collectors.toList())) {
                if (Files.isRegularFile(file)) {
                    unprocessedFiles.add(file);
                }
            }
        }
        
        // Loop externo: itera pelas chaves simétricas disponíveis
        // Loop interno: para cada chave, tenta todos os arquivos não processados
        // Remove a chave da lista quando ela descriptografa um arquivo com sucesso
        List<SecretKey> keysToProcess = new ArrayList<>(loadedSymmetricKeys);
        
        for (int i = 0; i < keysToProcess.size(); i++) {
            SecretKey currentKey = keysToProcess.get(i);
            boolean keyUsed = false;
            
            // Loop interno: tenta usar a chave atual em todos os arquivos não processados
            for (Path file : unprocessedFiles) {
                
                if (tryDecryptWithSingleKey(file, currentKey, unlockedPath)) {
                    // Chave foi usada com sucesso, remove da lista para evitar loops desnecessários
                    keyUsed = true;
                    break; // Cada chave só pode abrir um arquivo
                }
            }
            
            // Se a chave foi usada, remove ela da lista para otimizar próximas iterações
            if (keyUsed) {
                keysToProcess.remove(i);
                i--; // Ajusta o índice pois removemos um elemento
            }
        }
    }
    
    /**
     * Tenta descriptografar um arquivo específico com uma única chave simétrica
     * @param file Arquivo a ser descriptografado
     * @param symmetricKey Chave simétrica para tentar
     * @param unlockedPath Caminho da pasta unlocked
     * @return true se a descriptografia foi bem-sucedida, false caso contrário
     */
    private boolean tryDecryptWithSingleKey(Path file, SecretKey symmetricKey, Path unlockedPath) {
        try {
            byte[] encryptedData = Files.readAllBytes(file);
            
            // Tenta descriptografar com AES
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, symmetricKey);
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            // Se chegou até aqui, a descriptografia foi bem-sucedida
            // Agora tenta descriptografar o nome do arquivo com a mesma chave
            String decryptedFileName = tryDecryptFileName(file.getFileName().toString(), symmetricKey);
            
            // Salva o arquivo descriptografado na pasta unlocked com o nome descriptografado
            Path decryptedFile = unlockedPath.resolve(decryptedFileName);
            Files.write(decryptedFile, decryptedData);
            
            // Apaga o arquivo criptografado original
            Files.delete(file);
            
            processedFiles.add(file.toString());
            
            System.out.println("✓ Arquivo descriptografado: " + file.getFileName() + " -> " + decryptedFileName);
            System.out.println("  → Arquivo criptografado removido");
            
            // Tenta interpretar o conteúdo descriptografado
            tryInterpretDecryptedContent(decryptedData, decryptedFile);
            
            return true; // Descriptografia bem-sucedida
            
        } catch (Exception e) {
            // Se falhou, retorna false
            return false;
        }
    }
    
    /**
     * Tenta descriptografar o nome do arquivo usando a chave simétrica
     * @param encryptedFileName Nome do arquivo criptografado
     * @param symmetricKey Chave simétrica para descriptografia
     * @return Nome descriptografado ou nome original se falhar
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
            // Se falhou, usa o nome original sem extensão .decrypted
            return encryptedFileName;
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
    
    /**
     * Limpa todos os arquivos da pasta .criptogit/locked/keys
     * @param lockedPath Caminho da pasta locked
     * @throws Exception Se houver erro ao limpar os arquivos
     */
    private void clearKeysFolder(Path lockedPath) throws Exception {
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
}
