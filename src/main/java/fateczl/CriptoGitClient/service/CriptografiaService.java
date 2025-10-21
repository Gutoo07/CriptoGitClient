package fateczl.CriptoGitClient.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;


public class CriptografiaService {
    
    private Set<String> usedNames = new HashSet<>();
    private Random random = new SecureRandom();
    private List<PublicKey> publicKeys = new ArrayList<>();
    
    /**
     * Criptografa todos os blobs referenciados em um commit
     * @param repositorioPath Caminho do repositório
     * @param commitHash Hash do commit a ser criptografado
     */
    public void encryptBlobs(String repositorioPath, String commitHash) throws Exception {
        // Limpa o conjunto de nomes usados para cada nova operação
        usedNames.clear();
        
        // Carrega as chaves públicas
        loadPublicKeys(repositorioPath);
        
        // Procura a pasta .criptogit/objects do repositorio
        Path objectsPath = Paths.get(repositorioPath, ".criptogit", "objects");
        if (!Files.exists(objectsPath)) {
            throw new Exception("Erro ao criptografar objects: pasta .criptogit/objects não encontrada.");
        }
        
        // Cria a pasta locked se não existir
        Path lockedPath = Paths.get(repositorioPath, ".criptogit", "locked");
        if (!Files.exists(lockedPath)) {
            Files.createDirectories(lockedPath);
            System.out.println("Pasta locked criada em: " + lockedPath);
        }
        
        // Procura o blob do commit
        Path commitBlobPath = Paths.get(objectsPath.toString(), commitHash.substring(0, 2), commitHash.substring(2));
        if (!Files.exists(commitBlobPath)) {
            throw new Exception("Erro ao criptografar objects: blob do commit não encontrado.");
        }
        
        // Lê o conteúdo do commit
        String commitContent = new String(Files.readAllBytes(commitBlobPath));
        String[] lines = commitContent.split("\n");
        
        // A primeira linha deve conter "tree <hash>"
        if (lines.length == 0 || !lines[0].startsWith("tree ")) {
            throw new Exception("Formato de commit inválido: " + commitHash);
        }
        
        String rootTreeHash = lines[0].substring(5); // Remove "tree " do início
        
        System.out.println("Iniciando criptografia do commit: " + commitHash);
        System.out.println("Tree raiz: " + rootTreeHash);
        
        // Criptografa recursivamente a tree raiz
        String encryptedRootTreeHash = encryptTreeRecursively(rootTreeHash, objectsPath, lockedPath);
        
        // Criptografa o próprio commit
        String encryptedCommitHash = encryptCommit(commitHash, encryptedRootTreeHash, objectsPath, lockedPath);
        
        System.out.println("Criptografia concluída!");
        System.out.println("Commit original: " + commitHash);
        System.out.println("Commit criptografado: " + encryptedCommitHash);
        System.out.println("Arquivos criptografados salvos em: .criptogit/locked");
    }
    
    /**
     * Criptografa uma tree recursivamente
     * @param treeHash Hash da tree a ser criptografada
     * @param objectsPath Caminho da pasta objects
     * @param lockedPath Caminho da pasta locked
     * @return Hash da tree criptografada
     */
    private String encryptTreeRecursively(String treeHash, Path objectsPath, Path lockedPath) throws Exception {
        // Busca o blob da tree atual através da hash
        String dirName = treeHash.substring(0, 2);
        String fileName = treeHash.substring(2);
        Path treePath = Paths.get(objectsPath.toString(), dirName, fileName);
        
        // Se não encontrou, lança exceção
        if (!Files.exists(treePath)) {
            throw new IOException("Tree não encontrada: " + treeHash);
        }
        
        // Lê o conteúdo da tree
        String treeContent = new String(Files.readAllBytes(treePath));
        String[] lines = treeContent.split("\n");
        
        // Processa recursivamente todos os blobs e trees referenciados
        for (String line : lines) {
            // Se a linha estiver vazia, pula pra próxima linha
            if (line.trim().isEmpty()) {
                continue;
            }
            
            // Divide as partes da linha
            String[] parts = line.split(" ");
            // Formato padrão de tree: tree <nomeDaPasta> <hashDaTree>
            // ou blob <nomeDoArquivo> <hashDoBlob>
            // então só processa se tiver 3 partes
            if (parts.length >= 3) {
                String type = parts[0]; // "blob" ou "tree"
                String hash = parts[2]; // <hashDoBlob/Tree>
                
                // se aquela linha referenciar um blob
                if ("blob".equals(type)) {
                    // Criptografa o blob (mas não altera a referência na tree)
                    String encryptedBlobHash = encryptBlob(hash, objectsPath, lockedPath);
                    System.out.println("Blob criptografado: " + hash + " -> " + encryptedBlobHash);
                    
                } else if ("tree".equals(type)) {
                    // Se aquela linha referenciar uma tree
                    // Chama recursivamente para processar a sub-tree
                    String encryptedTreeHash = encryptTreeRecursively(hash, objectsPath, lockedPath);
                    System.out.println("Tree criptografada: " + hash + " -> " + encryptedTreeHash);
                }
            }
        }
        
        // Criptografa o conteúdo ORIGINAL da tree (sem alterar as referências)
        return encryptTreeContent(treeContent, treeHash, objectsPath, lockedPath);
    }
    
    /**
     * Criptografa um blob individual
     * @param blobHash Hash do blob a ser criptografado
     * @param objectsPath Caminho da pasta objects
     * @param lockedPath Caminho da pasta locked
     * @return Hash do blob criptografado
     */
    private String encryptBlob(String blobHash, Path objectsPath, Path lockedPath) throws Exception {
        // Busca o blob através da hash
        String dirName = blobHash.substring(0, 2);
        String fileName = blobHash.substring(2);
        Path blobPath = Paths.get(objectsPath.toString(), dirName, fileName);
        
        // Se não encontrou, lança exceção
        if (!Files.exists(blobPath)) {
            throw new IOException("Blob não encontrado: " + blobHash);
        }
        
        // Lê o conteúdo do blob
        byte[] originalContent = Files.readAllBytes(blobPath);
        
        // Gera uma chave simétrica diferente para cada blob
        SecretKey secretKey = generateSymmetricKey();
        
        // Criptografa o conteúdo
        byte[] encryptedContent = encryptContent(originalContent, secretKey);
        
        // Criptografa a hash SHA-1 completa com a mesma chave simétrica
        byte[] encryptedHash = encryptContent(blobHash.getBytes(), secretKey);
        String encryptedBlobName = bytesToHex(encryptedHash);
        
        // Gera nome único para a chave
        String keyName = generateUniqueName();
        
        // Salva o blob criptografado e sua chave na mesma pasta do blob original
        String newHash = saveEncryptedBlobWithKey(encryptedContent, secretKey, encryptedBlobName, keyName, dirName, objectsPath, lockedPath);
        
        System.out.println("Blob original: " + blobHash);
        System.out.println("Blob criptografado salvo em: locked/" + encryptedBlobName.substring(2));
        
        return newHash;
    }
    
    /**
     * Criptografa o conteúdo de uma tree
     * @param treeContent Conteúdo da tree
     * @param treeHash Hash da tree original
     * @param objectsPath Caminho da pasta objects
     * @param lockedPath Caminho da pasta locked
     * @return Hash da tree criptografada
     */
    private String encryptTreeContent(String treeContent, String treeHash, Path objectsPath, Path lockedPath) throws Exception {
        // Gera uma chave simétrica para a tree
        SecretKey secretKey = generateSymmetricKey();
        
        // Criptografa o conteúdo da tree
        byte[] encryptedContent = encryptContent(treeContent.getBytes(), secretKey);
        
        // Criptografa a hash SHA-1 completa com a mesma chave simétrica
        byte[] encryptedHash = encryptContent(treeHash.getBytes(), secretKey);
        String encryptedTreeName = bytesToHex(encryptedHash);
        
        // Gera nome único para a chave
        String keyName = generateUniqueName();
        
        // Usa os primeiros 2 caracteres da hash original para determinar a pasta
        String dirName = treeHash.substring(0, 2);
        
        // Salva a tree criptografada e sua chave
        String newHash = saveEncryptedBlobWithKey(encryptedContent, secretKey, encryptedTreeName, keyName, dirName, objectsPath, lockedPath);
        
        System.out.println("Tree original: " + treeHash);
        System.out.println("Tree criptografada salva em: locked/" + encryptedTreeName.substring(2));
        System.out.println("Chave simétrica da tree salva em: locked/" + keyName.substring(2));
        
        return newHash;
    }
    
    /**
     * Criptografa o commit
     * @param commitHash Hash do commit original
     * @param encryptedRootTreeHash Hash da tree raiz criptografada
     * @param objectsPath Caminho da pasta objects
     * @param lockedPath Caminho da pasta locked
     * @return Hash do commit criptografado
     */
    private String encryptCommit(String commitHash, String encryptedRootTreeHash, Path objectsPath, Path lockedPath) throws Exception {
        // Busca o commit original
        String dirName = commitHash.substring(0, 2);
        String fileName = commitHash.substring(2);
        Path commitPath = Paths.get(objectsPath.toString(), dirName, fileName);
        
        if (!Files.exists(commitPath)) {
            throw new IOException("Commit não encontrado: " + commitHash);
        }
        
        // Lê o conteúdo do commit
        String commitContent = new String(Files.readAllBytes(commitPath));
        
        // Gera uma chave simétrica para o commit
        SecretKey secretKey = generateSymmetricKey();
        
        // Criptografa o conteúdo ORIGINAL do commit (sem alterar as referências)
        byte[] encryptedContent = encryptContent(commitContent.getBytes(), secretKey);
        
        // Criptografa a hash SHA-1 completa com a mesma chave simétrica
        byte[] encryptedHash = encryptContent(commitHash.getBytes(), secretKey);
        String encryptedCommitName = bytesToHex(encryptedHash);
        
        // Gera nome único para a chave
        String keyName = generateUniqueName();
        
        // Salva o commit criptografado e sua chave na mesma pasta do commit original
        String newHash = saveEncryptedBlobWithKey(encryptedContent, secretKey, encryptedCommitName, keyName, dirName, objectsPath, lockedPath);
        
        System.out.println("Commit original: " + commitHash);
        System.out.println("Commit criptografado salvo em: locked/" + encryptedCommitName.substring(2));
        System.out.println("Chave simétrica do commit salva em: locked/" + keyName.substring(2));
        
        return newHash;
    }
    
    /**
     * Gera uma chave simétrica AES
     * @return Chave simétrica gerada
     */
    private SecretKey generateSymmetricKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256); // 256 bits
        return keyGenerator.generateKey();
    }
    
    /**
     * Criptografa conteúdo usando AES
     * @param content Conteúdo a ser criptografado
     * @param secretKey Chave para criptografia
     * @return Conteúdo criptografado
     */
    private byte[] encryptContent(byte[] content, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(content);
    }
    
    /**
     * Gera um nome único que não existe no repositório
     * @return Nome único gerado
     */
    private String generateUniqueName() {
        String name;
        do {
            // Gera um nome aleatório de 40 caracteres (similar ao SHA-1)
            StringBuilder sb = new StringBuilder();
            String chars = "0123456789abcdef";
            for (int i = 0; i < 40; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            name = sb.toString();
        } while (usedNames.contains(name));
        
        usedNames.add(name);
        return name;
    }
    
    /**
     * Converte um array de bytes para string hexadecimal
     * @param bytes Array de bytes
     * @return String hexadecimal
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Carrega todas as chaves públicas da pasta keys
     * @param repositorioPath Caminho do repositório
     * @throws Exception Se houver erro ao carregar as chaves
     */
    private void loadPublicKeys(String repositorioPath) throws Exception {
        Path keysPath = Paths.get(repositorioPath, ".criptogit", "keys");
        if (!Files.exists(keysPath)) {
            throw new Exception("Erro: pasta 'keys' não encontrada no repositório. Crie a pasta 'keys' em .criptogit e adicione suas chaves com os comandos: openssl genrsa -out private_key.pem 2048 && openssl rsa -in private_key.pem -pubout -out public_key.pem");
        }
        
        // Limpa a lista de chaves públicas
        publicKeys.clear();
        
        // Lista todos os arquivos na pasta keys
        try (var stream = Files.list(keysPath)) {
            for (Path keyFile : stream.collect(java.util.stream.Collectors.toList())) {
                String fileName = keyFile.getFileName().toString();
                
                // Processa apenas arquivos de chave pública
                if (fileName.startsWith("public_key") && fileName.endsWith(".pem")) {
                    try {
                        PublicKey publicKey = loadPublicKeyFromFile(keyFile);
                        publicKeys.add(publicKey);
                        System.out.println("Chave pública carregada: " + fileName);
                    } catch (Exception e) {
                        System.err.println("Erro ao carregar chave pública " + fileName + ": " + e.getMessage());
                    }
                }
            }
        }
        
        if (publicKeys.isEmpty()) {
            throw new Exception("Erro: nenhuma chave pública encontrada na pasta keys");
        }
        
        System.out.println("Total de chaves públicas carregadas: " + publicKeys.size());
    }
    
    /**
     * Carrega uma chave pública de um arquivo
     * @param keyFile Arquivo da chave pública
     * @return Chave pública carregada
     * @throws Exception Se houver erro ao carregar
     */
    private PublicKey loadPublicKeyFromFile(Path keyFile) throws Exception {
        // Lê o arquivo da chave pública
        byte[] publicKeyBytes = Files.readAllBytes(keyFile);
        
        // Remove headers e footers PEM se existirem
        String publicKeyContent = new String(publicKeyBytes);
        publicKeyContent = publicKeyContent.replace("-----BEGIN PUBLIC KEY-----", "")
                                          .replace("-----END PUBLIC KEY-----", "")
                                          .replaceAll("\\s", "");
        
        // Converte de Base64 para bytes
        byte[] keyBytes = java.util.Base64.getDecoder().decode(publicKeyContent);
        
        // Cria a chave pública
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
    
    /**
     * Criptografa uma chave simétrica com uma chave pública RSA
     * @param secretKey Chave simétrica a ser criptografada
     * @param publicKey Chave pública para criptografia
     * @return Chave simétrica criptografada
     * @throws Exception Se houver erro na criptografia
     */
    private byte[] encryptSymmetricKeyWithPublicKey(SecretKey secretKey, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(secretKey.getEncoded());
    }
    
    /**
     * Salva um blob criptografado e suas chaves simétricas no repositório
     * @param encryptedContent Conteúdo criptografado
     * @param secretKey Chave simétrica usada para criptografar
     * @param encryptedName Nome do blob criptografado
     * @param keyName Nome do arquivo da chave simétrica original
     * @param dirName Nome do diretório (primeiros 2 caracteres do hash original)
     * @param objectsPath Caminho da pasta objects
     * @param lockedPath Caminho da pasta locked
     * @return Hash do blob salvo
     */
    private String saveEncryptedBlobWithKey(byte[] encryptedContent, SecretKey secretKey, 
                                          String encryptedName, String keyName, String dirName, 
                                          Path objectsPath, Path lockedPath) throws Exception {
        // Cria a estrutura de diretórios
        Path dirPath = Paths.get(objectsPath.toString(), dirName);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        
        // Salva o blob criptografado na pasta locked
        Path encryptedFilePath = Paths.get(lockedPath.toString(), encryptedName);
        Files.write(encryptedFilePath, encryptedContent);
        
        // Salva a chave simétrica original descriptografada na pasta do blob
        Path originalKeyFilePath = Paths.get(objectsPath.toString(), dirName, keyName);
        Files.write(originalKeyFilePath, secretKey.getEncoded());
        System.out.println("Chave simétrica original salva em: objects/" + dirName + "/" + keyName);
        
        // Criptografa a chave simétrica com cada chave pública RSA
        for (int i = 0; i < publicKeys.size(); i++) {
            PublicKey publicKey = publicKeys.get(i);
            byte[] encryptedSymmetricKey = encryptSymmetricKeyWithPublicKey(secretKey, publicKey);
            
            // Gera nome único para cada chave simétrica criptografada
            String encryptedKeyName = generateUniqueName();
            
            // Salva a chave simétrica criptografada
            Path encryptedKeyFilePath = Paths.get(lockedPath.toString(), encryptedKeyName);
            Files.write(encryptedKeyFilePath, encryptedSymmetricKey);
            
            System.out.println("Chave simétrica criptografada " + (i + 1) + "/" + publicKeys.size() + 
                             " salva em: locked/" + encryptedKeyName);
        }
                
        return encryptedName;
    }
    
}
