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
import fateczl.CriptoGitClient.model.Commit;
import fateczl.CriptoGitClient.model.Tree;
import fateczl.CriptoGitClient.model.Blob;
import fateczl.CriptoGitClient.model.Arquivo;

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

        // Quarta fase: salva o HEAD na pasta .criptogit
        System.out.println("\n=== FASE 4: Salvando HEAD na pasta .criptogit ===");
        saveLatestHeadToCriptogit(repositorioPath);
        
        // Quinta fase: remonta a árvore de diretórios
        System.out.println("\n=== FASE 5: Remontando árvore de diretórios ===");
        remountWorkingDirectory(repositorioPath, unlockedPath);
    }
    
    /**
     * Carrega a chave privada da pasta keys
     * @param repositorioPath Caminho do repositório
     * @return Chave privada carregada
     * @throws Exception Se houver erro ao carregar
     */
    private PrivateKey loadPrivateKey(String repositorioPath) throws Exception {
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
                
                if (tryDecryptWithSingleKey(file, currentKey)) {
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
     * @return true se a descriptografia foi bem-sucedida, false caso contrário
     */
    private boolean tryDecryptWithSingleKey(Path file, SecretKey symmetricKey) {
        try {
            byte[] encryptedData = Files.readAllBytes(file);
            
            // Tenta descriptografar com AES
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, symmetricKey);
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            // Se chegou até aqui, a descriptografia foi bem-sucedida
            // Agora tenta descriptografar o nome do arquivo com a mesma chave
            String decryptedFileName = tryDecryptFileName(file.getFileName().toString(), symmetricKey);
            
            // Verifica se é o HEAD (nome é apenas um número e conteúdo é hash SHA-1)
            if (isHeadFile(decryptedFileName, decryptedData)) {
                // Salva o HEAD na pasta versions
                saveDecryptedHead(decryptedData, decryptedFileName, file, symmetricKey);
            } else {
                // Salva o blob descriptografado na pasta .criptogit/objects
                saveDecryptedBlob(decryptedData, decryptedFileName, file, symmetricKey);
            }
            
            // Apaga o arquivo criptografado original
            Files.delete(file);
            
            processedFiles.add(file.toString());
            
            System.out.println("✓ Arquivo descriptografado: " + file.getFileName() + " -> " + decryptedFileName);
            System.out.println("  → Arquivo criptografado removido");
            
            // Tenta interpretar o conteúdo descriptografado
            tryInterpretDecryptedContent(decryptedData, decryptedFileName);
            
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
     * Verifica se o arquivo descriptografado é o HEAD
     * @param decryptedFileName Nome do arquivo descriptografado
     * @param decryptedData Conteúdo descriptografado
     * @return true se for o HEAD, false caso contrário
     */
    private boolean isHeadFile(String decryptedFileName, byte[] decryptedData) {
        try {
            // Verifica se o nome é apenas um número (sem extensão)
            if (!decryptedFileName.matches("^\\d+$")) {
                return false;
            }
            
            // Converte o conteúdo para string
            String content = new String(decryptedData).trim();
            
            // Verifica se o conteúdo é uma hash SHA-1 de 40 dígitos hexadecimais
            if (content.matches("^[a-f0-9]{40}$")) {
                System.out.println("  → Detectado como HEAD: versão " + decryptedFileName + ", hash: " + content);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Salva o HEAD descriptografado na pasta .criptogit/versions
     * @param decryptedData Dados descriptografados (hash do commit)
     * @param versionNumber Número da versão (nome do arquivo)
     * @param originalFile Arquivo original para obter o caminho do repositório
     * @param symmetricKey Chave simétrica usada para descriptografar o HEAD
     * @throws Exception Se houver erro ao salvar
     */
    private void saveDecryptedHead(byte[] decryptedData, String versionNumber, Path originalFile, SecretKey symmetricKey) throws Exception {
        // Obtém o caminho do repositório a partir do arquivo original
        Path repositorioPath = originalFile.getParent().getParent().getParent();
        Path versionsPath = Paths.get(repositorioPath.toString(), ".criptogit", "versions");
        
        // Cria a pasta versions se não existir
        if (!Files.exists(versionsPath)) {
            Files.createDirectories(versionsPath);
        }
        
        // Salva o HEAD na pasta versions com o número da versão como nome
        Path headFile = Paths.get(versionsPath.toString(), versionNumber);
        if (!Files.exists(headFile)) {
            Files.write(headFile, decryptedData);
            System.out.println("  → HEAD salvo em: .criptogit/versions/" + versionNumber);
        } else {
            System.out.println("  → HEAD já existe em: .criptogit/versions/" + versionNumber);
        }
        
        // Salva a chave simétrica do HEAD na pasta versions com o nome {versao}.key
        String keyFileName = versionNumber + ".key";
        Path keyFile = Paths.get(versionsPath.toString(), keyFileName);
        if (!Files.exists(keyFile)) {
            Files.write(keyFile, symmetricKey.getEncoded());
            System.out.println("  → Chave simétrica do HEAD salva em: .criptogit/versions/" + keyFileName);
        } else {
            System.out.println("  → Chave simétrica do HEAD já existe, não foi sobrescrita: .criptogit/versions/" + keyFileName);
        }
    }
    
    /**
     * Salva o blob descriptografado na pasta .criptogit/objects seguindo o padrão do comando add
     * @param decryptedData Dados descriptografados
     * @param decryptedFileName Nome do arquivo descriptografado (que é a hash completa)
     * @param originalFile Arquivo original para obter o caminho do repositório
     * @param symmetricKey Chave simétrica usada para descriptografar o blob
     * @throws Exception Se houver erro ao salvar
     */
    private void saveDecryptedBlob(byte[] decryptedData, String decryptedFileName, Path originalFile, SecretKey symmetricKey) throws Exception {
        // Obtém o caminho do repositório a partir do arquivo original
        Path repositorioPath = originalFile.getParent().getParent().getParent();
        Path objectsPath = Paths.get(repositorioPath.toString(), ".criptogit", "objects");
        
        // A hash completa é o decryptedFileName
        String hash = decryptedFileName;
        
        // Pega os 2 primeiros caracteres da hash para o diretório e os 38 restantes para o arquivo
        String dirName = hash.substring(0, 2);        // 2 primeiros caracteres
        String fileName = hash.substring(2);         // 38 caracteres restantes
        
        // Cria a pasta se não existir
        Path objectDir = Paths.get(objectsPath.toString(), dirName);
        if (!Files.exists(objectDir)) {
            Files.createDirectories(objectDir);
        }
        
        // Cria o arquivo do blob se não existir
        Path objectFile = Paths.get(objectDir.toString(), fileName);
        if (!Files.exists(objectFile)) {
            Files.write(objectFile, decryptedData);
            System.out.println("  → Blob salvo em: .criptogit/objects/" + dirName + "/" + fileName);
        } else {
            System.out.println("  → Blob já existe, não foi sobrescrito: .criptogit/objects/" + dirName + "/" + fileName);
        }
        
        // Salva a chave simétrica na mesma pasta do blob com o nome {hash_completa}.key
        String keyFileName = hash + ".key";
        Path keyFile = Paths.get(objectDir.toString(), keyFileName);
        if (!Files.exists(keyFile)) {
            Files.write(keyFile, symmetricKey.getEncoded());
            System.out.println("  → Chave simétrica salva em: .criptogit/objects/" + dirName + "/" + keyFileName);
        } else {
            System.out.println("  → Chave simétrica já existe, não foi sobrescrita: .criptogit/objects/" + dirName + "/" + keyFileName);
        }
    }
    
    /**
     * Tenta interpretar o conteúdo descriptografado para identificar o tipo de objeto
     * @param decryptedData Dados descriptografados
     * @param decryptedFileName Nome do arquivo descriptografado
     */
    private void tryInterpretDecryptedContent(byte[] decryptedData, String decryptedFileName) {
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
    
    /**
     * Remonta a árvore de diretórios a partir do arquivo HEAD
     * @param repositorioPath Caminho do repositório
     * @param unlockedPath Caminho da pasta unlocked
     * @throws Exception Se houver erro na remontagem
     */
    private void remountWorkingDirectory(String repositorioPath, Path unlockedPath) throws Exception {
        // Cria a pasta wd (working directory) se não existir
        Path wdPath = unlockedPath.resolve("wd");
        if (!Files.exists(wdPath)) {
            Files.createDirectories(wdPath);
            System.out.println("Pasta wd criada em: " + wdPath);
        }
        
        // Lê o arquivo de maior número na pasta versions (HEAD mais recente)
        String commitHash = readLatestHeadFromVersions(repositorioPath);
        if (commitHash == null) {
            System.err.println("⚠ Nenhum HEAD encontrado na pasta versions");
            return;
        }
        
        System.out.println("Hash do commit HEAD (mais recente): " + commitHash);
        
        // Carrega e processa o commit (agora busca na pasta objects)
        Commit commit = loadCommit(repositorioPath, commitHash);
        if (commit == null) {
            System.err.println("✗ Erro ao carregar commit: " + commitHash);
            return;
        }
        
        System.out.println("Commit carregado: " + commit.getMessage());
        System.out.println("Autor: " + commit.getAuthor());
        System.out.println("Data: " + commit.getDate());
        
        // Obtém a tree raiz do commit
        Tree rootTree = commit.getRootTree();
        if (rootTree == null) {
            System.err.println("✗ Commit não possui tree raiz");
            return;
        }
        
        System.out.println("Tree raiz: " + rootTree.getHash());
        
        // Processa recursivamente a tree raiz (agora busca na pasta objects)
        processTreeRecursively(repositorioPath, wdPath, rootTree);
        
        System.out.println("✓ Árvore de diretórios remontada com sucesso em: " + wdPath);
    }
    
    /**
     * Lê o arquivo de maior número na pasta versions (HEAD mais recente)
     * @param repositorioPath Caminho do repositório
     * @return Hash do commit ou null se não encontrado
     */
    private String readLatestHeadFromVersions(String repositorioPath) {
        try {
            Path versionsPath = Paths.get(repositorioPath, ".criptogit", "versions");
            if (!Files.exists(versionsPath)) {
                System.out.println("✗ Pasta versions não encontrada");
                return null;
            }
            
            int maxVersion = 0;
            String latestHeadContent = null;
            
            // Lista todos os arquivos na pasta versions
            try (var stream = Files.list(versionsPath)) {
                for (Path file : stream.collect(java.util.stream.Collectors.toList())) {
                    if (Files.isRegularFile(file)) {
                        String fileName = file.getFileName().toString();
                        
                        // Verifica se o nome do arquivo é um número (versão)
                        try {
                            int versionNumber = Integer.parseInt(fileName);
                            if (versionNumber > maxVersion) {
                                maxVersion = versionNumber;
                                // Lê o conteúdo do arquivo (hash do commit)
                                latestHeadContent = Files.readString(file).trim();
                            }
                        } catch (NumberFormatException e) {
                            // Se não for um número, ignora o arquivo
                            continue;
                        }
                    }
                }
            }
            
            if (latestHeadContent != null) {
                System.out.println("HEAD mais recente encontrado: versão " + maxVersion + ", hash: " + latestHeadContent);
                return latestHeadContent;
            } else {
                System.out.println("✗ Nenhum arquivo de versão encontrado na pasta versions");
                return null;
            }
            
        } catch (Exception e) {
            System.out.println("✗ Erro ao ler pasta versions: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Lê o arquivo HEAD para obter o hash do commit (método antigo - mantido para compatibilidade)
     * @param unlockedPath Caminho da pasta unlocked
     * @return Hash do commit ou null se não encontrado
     */
    private String readHeadFile(Path unlockedPath) {
        try {
            Path headFile = unlockedPath.resolve("HEAD");
            if (!Files.exists(headFile)) {
                return null;
            }
            
            String headContent = Files.readString(headFile).trim();
            System.out.println("Conteúdo do HEAD: " + headContent);
            return headContent;
            
        } catch (Exception e) {
            System.out.println("✗ Erro ao ler arquivo HEAD: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Carrega um commit a partir do seu hash
     * @param repositorioPath Caminho do repositório
     * @param commitHash Hash do commit
     * @return Commit carregado ou null se erro
     */
    private Commit loadCommit(String repositorioPath, String commitHash) {
        try {
            // Procura pelo arquivo do commit na pasta objects
            Path commitFile = findObjectFile(repositorioPath, commitHash);
            if (commitFile == null) {
                System.out.println("✗ Arquivo do commit não encontrado: " + commitHash);
                return null;
            }
            
            // Lê o conteúdo do arquivo
            byte[] commitData = Files.readAllBytes(commitFile);
            String commitContent = new String(commitData);
            
            // Parse do commit
            return parseCommitContent(commitContent, commitHash, repositorioPath);
            
        } catch (Exception e) {
            System.out.println("✗ Erro ao carregar commit: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Encontra o arquivo de um objeto pelo seu hash na pasta objects
     * @param repositorioPath Caminho do repositório
     * @param objectHash Hash do objeto
     * @return Path do arquivo ou null se não encontrado
     */
    private Path findObjectFile(String repositorioPath, String objectHash) {
        // Pega os 2 primeiros caracteres da hash para o diretório e os 38 restantes para o arquivo
        String dirName = objectHash.substring(0, 2);        // 2 primeiros caracteres
        String fileName = objectHash.substring(2);         // 38 caracteres restantes
        
        Path objectsPath = Paths.get(repositorioPath, ".criptogit", "objects");
        Path objectFile = objectsPath.resolve(dirName).resolve(fileName);
        
        return Files.exists(objectFile) ? objectFile : null;
    }
    
    /**
     * Faz o parse do conteúdo de um commit
     * @param commitContent Conteúdo do commit
     * @param commitHash Hash do commit
     * @param repositorioPath Caminho do repositório
     * @return Commit parseado
     */
    private Commit parseCommitContent(String commitContent, String commitHash, String repositorioPath) {
        Commit commit = new Commit();
        commit.setHash(commitHash);
        
        String[] lines = commitContent.split("\n");
        String treeHash = null;
        
        for (String line : lines) {
            if (line.startsWith("tree ")) {
                treeHash = line.substring(5).trim();
            } else if (line.startsWith("author ")) {
                commit.setAuthor(line.substring(7).trim());
            } else if (line.startsWith("date ")) {
                commit.setDate(line.substring(5).trim());
            } else if (line.startsWith("message ")) {
                commit.setMessage(line.substring(8).trim());
            } else if (line.startsWith("parent ")) {
                commit.setParent(line.substring(7).trim());
            }
        }
        
        // Carrega a tree raiz
        if (treeHash != null) {
            Tree rootTree = loadTree(repositorioPath, treeHash);
            commit.setRootTree(rootTree);
        }
        
        return commit;
    }
    
    /**
     * Carrega uma tree a partir do seu hash
     * @param repositorioPath Caminho do repositório
     * @param treeHash Hash da tree
     * @return Tree carregada ou null se erro
     */
    private Tree loadTree(String repositorioPath, String treeHash) {
        try {
            // Procura pelo arquivo da tree na pasta objects
            Path treeFile = findObjectFile(repositorioPath, treeHash);
            if (treeFile == null) {
                System.out.println("✗ Arquivo da tree não encontrado: " + treeHash);
                return null;
            }
            
            // Lê o conteúdo do arquivo
            byte[] treeData = Files.readAllBytes(treeFile);
            String treeContent = new String(treeData);
            
            // Parse da tree
            return parseTreeContent(treeContent, treeHash, repositorioPath);
            
        } catch (Exception e) {
            System.out.println("✗ Erro ao carregar tree: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Faz o parse do conteúdo de uma tree
     * @param treeContent Conteúdo da tree
     * @param treeHash Hash da tree
     * @param repositorioPath Caminho do repositório
     * @return Tree parseada
     */
    private Tree parseTreeContent(String treeContent, String treeHash, String repositorioPath) {
        Tree tree = new Tree();
        tree.setHash(treeHash);
        
        String[] lines = treeContent.split("\n");
        
        for (String line : lines) {
            if (line.startsWith("blob ")) {
                // Formato: blob <hash> <nome>
                String[] parts = line.substring(5).trim().split(" ", 2);
                if (parts.length == 2) {
                    String fileName = parts[0];
                    String blobHash = parts[1];
                    
                    Arquivo arquivo = new Arquivo();
                    arquivo.setName(fileName);
                    
                    Blob blob = new Blob();
                    blob.setHash(blobHash);
                    arquivo.setBlob(blob);
                    
                    tree.addArquivo(arquivo);
                }
            } else if (line.startsWith("tree ")) {
                // Formato: tree <hash> <nome>
                String[] parts = line.substring(5).trim().split(" ", 2);
                if (parts.length == 2) {
                    String treeName = parts[0];
                    String subTreeHash = parts[1];
                    
                    Tree subTree = loadTree(repositorioPath, subTreeHash);
                    if (subTree != null) {
                        subTree.setName(treeName);
                        tree.addTree(subTree);
                    }
                }
            }
        }
        
        return tree;
    }
    
    /**
     * Processa recursivamente uma tree, criando arquivos e pastas
     * @param repositorioPath Caminho do repositório
     * @param currentPath Caminho atual onde estamos criando os arquivos
     * @param tree Tree a ser processada
     * @throws Exception Se houver erro no processamento
     */
    private void processTreeRecursively(String repositorioPath, Path currentPath, Tree tree) throws Exception {
        System.out.println("Processando tree: " + tree.getHash() + " em: " + currentPath);
        
        // Processa todos os arquivos (blobs) desta tree
        for (Arquivo arquivo : tree.getArquivos()) {
            Blob blob = arquivo.getBlob();
            String fileName = arquivo.getName();
            
            // Carrega o conteúdo do blob
            byte[] blobContent = loadBlobContent(repositorioPath, blob.getHash());
            if (blobContent != null) {
                // Cria o arquivo
                Path filePath = currentPath.resolve(fileName);
                Files.write(filePath, blobContent);
                System.out.println("  ✓ Arquivo criado: " + fileName);
            } else {
                System.out.println("  ✗ Erro ao carregar blob: " + blob.getHash());
            }
        }
        
        // Processa todas as sub-trees recursivamente
        for (Tree subTree : tree.getTrees()) {
            String treeName = subTree.getName();
            
            // Cria a pasta
            Path subTreePath = currentPath.resolve(treeName);
            Files.createDirectories(subTreePath);
            System.out.println("  ✓ Pasta criada: " + treeName);
            
            // Processa recursivamente a sub-tree
            processTreeRecursively(repositorioPath, subTreePath, subTree);
        }
    }
    
    /**
     * Carrega o conteúdo de um blob
     * @param repositorioPath Caminho do repositório
     * @param blobHash Hash do blob
     * @return Conteúdo do blob ou null se erro
     */
    private byte[] loadBlobContent(String repositorioPath, String blobHash) {
        try {
            // Procura pelo arquivo do blob na pasta objects
            Path blobFile = findObjectFile(repositorioPath, blobHash);
            if (blobFile == null) {
                System.out.println("✗ Arquivo do blob não encontrado: " + blobHash);
                return null;
            }
            
            // Lê o conteúdo do arquivo
            byte[] blobData = Files.readAllBytes(blobFile);
            
            // Remove o prefixo "blob " se existir
            String blobContent = new String(blobData);
            if (blobContent.startsWith("blob ")) {
                // Encontra a primeira quebra de linha após "blob "
                int newlineIndex = blobContent.indexOf('\n');
                if (newlineIndex != -1) {
                    return blobContent.substring(newlineIndex + 1).getBytes();
                }
            }
            
            return blobData;
            
        } catch (Exception e) {
            System.out.println("✗ Erro ao carregar blob: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Busca o maior HEAD usando readLatestHeadFromVersions e salva o conteúdo na pasta .criptogit como HEAD
     * @param repositorioPath Caminho do repositório
     * @throws Exception Se houver erro ao salvar o HEAD
     */
    public void saveLatestHeadToCriptogit(String repositorioPath) throws Exception {
        System.out.println("Buscando o maior HEAD na pasta versions...");
        
        // Busca o maior HEAD usando a função existente
        String latestHeadContent = readLatestHeadFromVersions(repositorioPath);
        if (latestHeadContent == null) {
            throw new Exception("Nenhum HEAD encontrado na pasta versions");
        }
        
        // Cria o caminho para o arquivo HEAD na pasta .criptogit
        Path criptogitPath = Paths.get(repositorioPath, ".criptogit");
        Path headFile = criptogitPath.resolve("HEAD");
        
        // Cria a pasta .criptogit se não existir
        if (!Files.exists(criptogitPath)) {
            Files.createDirectories(criptogitPath);
            System.out.println("Pasta .criptogit criada em: " + criptogitPath);
        }
        
        // Salva o conteúdo do HEAD no arquivo
        Files.write(headFile, latestHeadContent.getBytes());
        
        System.out.println("✓ HEAD salvo com sucesso em: " + headFile);
        System.out.println("  → Conteúdo: " + latestHeadContent);
    }
}
