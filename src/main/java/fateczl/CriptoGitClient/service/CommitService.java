package fateczl.CriptoGitClient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;

import fateczl.CriptoGitClient.model.Blob;
import fateczl.CriptoGitClient.model.Commit;
import fateczl.CriptoGitClient.model.Index;
import fateczl.CriptoGitClient.model.Tree;

public class CommitService {

    private String repositorioPath;
    private Index index;
    private TreeService treeService;
    private FileService fileService;
    private VersionService versionService;
    private CriptografiaService criptografiaService;

    public void setRepositorioPath(String repositorioPath) {
        this.repositorioPath = repositorioPath;
    }
    public void setIndex(Index index) {
        this.index = index;
    }
    
    /*
     * Cria as trees necessarias para o commit, incluindo a tree raiz
     * Cria o object do blob do commit em si, que guardará os metadados
     * @param message
     * @throws Exception
     */    
     public void commit(String message) throws Exception {
        // Verifica se o diretório .criptogit existe
        if (!Files.exists(Paths.get(repositorioPath, ".criptogit"))) {
            System.err.println("\nErro: Diretório .criptogit não existe. Execute o comando init para criar um repositório CriptoGit.");
            return;
        }
        // Verifica se o diretório objects existe
        Path objectsPath = Paths.get(repositorioPath, ".criptogit", "objects");
        if (!Files.exists(objectsPath)) {            
            System.err.println("\nErro: Diretório objects não existe. Execute o comando init para criar um repositório CriptoGit.");
            return;
        }
        // Verifica se a pasta keys existe e possui chaves simétricas
        Path keysPath = Paths.get(repositorioPath, ".criptogit", "keys");
        if (!Files.exists(keysPath)) {
            System.err.println("\nErro: Pasta keys não existe. Execute o comando init para criar um repositório CriptoGit.");
            return;
        }
        if (Files.list(keysPath).count() == 0) {
            System.err.println("\nErro: Pasta keys está vazia. Crie seu par de chaves pública e privada com os comandos: openssl genrsa -out private_key.pem 2048 && openssl rsa -in private_key.pem -pubout -out public_key.pem");
            return;
        }
        // Cria o objeto de commit com os metadados
        Commit commit = new Commit();
        commit.setMessage(message);
        commit.setDate(new Date().toString());
        commit.setAuthor(System.getProperty("user.name"));
        // Procura se existe um commit anterior através do HEAD
        Path headPath = Paths.get(repositorioPath, ".criptogit", "HEAD");
        if (Files.exists(headPath)) {
            List<String> parentCommitHash = Files.readAllLines(headPath);
            if (!parentCommitHash.isEmpty()) {
                commit.setParent(parentCommitHash.getFirst());
                // Adiciona os arquivos referenciados no commit anterior ao index (apenas em memória)
                getParentCommitFiles(parentCommitHash.getFirst());
            }
        }
        
        
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        // Inicia o processamento recursivo das Trees
        treeService = new TreeService();
        treeService.setIndex(index);
        Tree rootTree = treeService.processTreesRecursively(Paths.get(repositorioPath), objectsPath, md);
        // Define o apontamento para a tree raiz
        commit.setRootTree(rootTree);
        // Salva o objeto de commit no diretório objects
        processCommit(commit, objectsPath, md);

        // Cria/Atualiza o arquivo HEAD, apontando pra esse commit        
        Files.write(headPath, commit.getHash().getBytes());

        
        // Criptografa o commit
        criptografiaService = new CriptografiaService();
        criptografiaService.encryptBlobs(repositorioPath, commit.getHash());
        
        // Salva a versão do commit
        saveCommitVersion(commit.getHash());
        
        // Limpa o index
        Path indexPath = Paths.get(repositorioPath, ".criptogit", "index");
        Files.write(indexPath, new byte[0]);
    }

    /**
     * Busca os arquivos referenciados em um commit anterior e os adiciona ao index
     * para serem referenciados no novo commit
     * @throws Exception 
     */
    public void getParentCommitFiles(String parentHash) throws Exception {        
        // Lê o commit para obter a hash da tree raiz
        String rootTreeHash = getRootTreeHashFromCommit(parentHash);
        
        // Inicia o processo recursivo a partir da tree raiz
        getLastCommitFilesRecursively(rootTreeHash, "");
    }
    
    /**
     * Lê o commit e extrai a hash da tree raiz
     */
    private String getRootTreeHashFromCommit(String commitHash) throws IOException {
        // Procura o blob do commit
        Path objectsPath = Paths.get(repositorioPath, ".criptogit", "objects");
        String dirName = commitHash.substring(0, 2);
        String fileName = commitHash.substring(2);
        Path commitPath = Paths.get(objectsPath.toString(), dirName, fileName);
        
        // Se não encontrar, lança exceção
        if (!Files.exists(commitPath)) {
            throw new IOException("Commit não encontrado: " + commitHash);
        }
        
        // Lê o conteúdo do commit
        String commitContent = new String(Files.readAllBytes(commitPath));
        String[] lines = commitContent.split("\n");
        
        // A primeira linha deve conter "tree <hash>"
        if (lines.length > 0 && lines[0].startsWith("tree ")) {
            return lines[0].substring(5); // Remove "tree " do início e retorna a hash
        }
        
        throw new IOException("Formato de commit inválido: " + commitHash);
    }
    
    /**
     * Processa uma tree recursivamente, montando os blobs anteriores com seus relativePaths no index
     * para serem referenciados novamente no novo Commit
     */
    private void getLastCommitFilesRecursively(String treeHash, String currentPath) throws IOException {
        // Busca o blob da tree atual através da hash
        Path objectsPath = Paths.get(repositorioPath, ".criptogit", "objects");
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
                String name = parts[1]; // <nomeDoArquivo/Pasta>
                String hash = parts[2]; // <hashDoBlob/Tree>
                
                // se aquela linha referenciar um blob
                if ("blob".equals(type)) {
                    // Cria o blob e adiciona ao index
                    Blob blob = new Blob();
                    blob.setHash(hash);
                    
                    // Monta o relativePath
                    String relativePath = currentPath.isEmpty() ? "\\" + name : currentPath + "\\" + name;
                    blob.setRelativePath(relativePath);
                    
                    // Adiciona ao index
                    index.getBlobs().add(blob);
                } else if ("tree".equals(type)) {
                    // Se aquela linha referenciar uma tree
                    // Chama recursivamente para processar a sub-tree
                    String newPath = currentPath.isEmpty() ? "\\" + name : currentPath + "\\" + name;
                    getLastCommitFilesRecursively(hash, newPath);
                }
            }
        }
    }

    /**
     * Processa o conteúdo do Commit e gera seu blob
     */
     private void processCommit(Commit commit, Path objectsPath, MessageDigest md) throws IOException {
        // Define as linhas a serem escritas no blob do commit
        StringBuilder blobContent = new StringBuilder();
        blobContent.append("tree ").append(commit.getRootTree().getHash()).append("\n");
        if (commit.getParentHash() != null) {
            blobContent.append("parent ").append(commit.getParentHash()).append("\n");
        }
        blobContent.append("author ").append(commit.getAuthor()).append("\n");
        blobContent.append("date ").append(commit.getDate()).append("\n");
        blobContent.append("message ").append(commit.getMessage()).append("\n");
        
        // Calcula a hash do conteúdo do commit
        byte[] sha1bytes = md.digest(blobContent.toString().getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : sha1bytes) {
            sb.append(String.format("%02x", b));
        }
        commit.setHash(sb.toString());
        
        // Pega os 2 primeiros caracteres da hash para o diretório e os 38 restantes para o arquivo
        String hash = commit.getHash();
        String dirName = hash.substring(0, 2);        // 2 primeiros caracteres
        String fileName = hash.substring(2);         // 38 caracteres restantes
        
        // Monta o blob do commit para ser armazenado
        Blob commitBlob = new Blob();
        commitBlob.setContent(blobContent.toString().getBytes());
        commitBlob.setHash(commit.getHash());
        
        // Cria a pasta do blob
        Path objectDir = Paths.get(objectsPath.toString(), dirName);
        treeService = new TreeService();
        treeService.setIndex(index);
        treeService.createDirectory(objectDir);
        // Cria o blob
        Path objectFile = Paths.get(objectDir.toString(), fileName);
        fileService = new FileService();
        fileService.setIndex(index);
        fileService.createFile(objectFile, commitBlob);
    }

    /**
     * Salva a versão do commit após o commit ser finalizado
     * Procura por arquivos de versão na pasta .criptogit/versions e salva o conteúdo do HEAD
     * @param commitHash Hash do commit que foi criado
     * @throws Exception Se houver erro ao salvar a versão
     */
    private void saveCommitVersion(String commitHash) throws Exception {
        Path versionsPath = Paths.get(repositorioPath, ".criptogit", "versions");
        
        // Cria a pasta versions se não existir
        if (!Files.exists(versionsPath)) {
            Files.createDirectories(versionsPath);
        }
        
        // Procura por arquivos cujos nomes são números (versões)
        versionService = new VersionService();
        int nextVersion = versionService.findNextVersionNumber(versionsPath);
        
        // Salva o conteúdo do HEAD no arquivo de versão
        Path versionFile = versionsPath.resolve(String.valueOf(nextVersion));
        Files.write(versionFile, commitHash.getBytes());
    }   

}