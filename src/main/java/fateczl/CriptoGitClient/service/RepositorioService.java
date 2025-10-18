package fateczl.CriptoGitClient.service;

import fateczl.CriptoGitClient.model.Arquivo;
import fateczl.CriptoGitClient.model.Tree;
import fateczl.CriptoGitClient.model.Repositorio;
import fateczl.CriptoGitClient.model.Blob;
import fateczl.CriptoGitClient.model.Commit;
import fateczl.CriptoGitClient.model.Index;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;

public class RepositorioService {
    private Repositorio repositorio;
    private Index index;

    public void init(String path) throws Exception {
        // Remove aspas duplas do início e fim do caminho se existirem
        path = path.trim();
        if (path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        
        // Verifica se o diretório existe
        Path directoryPath = Paths.get(path);
        
        if (!Files.exists(directoryPath)) {
            throw new IllegalArgumentException("Diretório não existe.");
        }
        
        if (!Files.isDirectory(directoryPath)) {
            throw new IllegalArgumentException("O caminho especificado não é um diretório.");
        }
        
        repositorio = new Repositorio();
        repositorio.setPath(path);
        
        // Obtém o nome da última pasta do caminho usando o separador correto do sistema
        String separator = System.getProperty("file.separator");
        String name = path.substring(path.lastIndexOf(separator) + 1);
        repositorio.setName(name);

        // Cria o diretório .criptogit se não existir
        Path criptogitPath = Paths.get(path, ".criptogit");
        if (!Files.exists(criptogitPath)) {
            try {
                Files.createDirectory(criptogitPath);
                           
            } catch (IOException e) {
                throw new IOException("Erro ao criar o diretório .criptogit.");
            }
        }
        // Cria o diretório objects dentro do .criptogit se não existir
        Path objectsPath = Paths.get(path, ".criptogit", "objects");            
        if (!Files.exists(objectsPath)) {
            try {
            Files.createDirectory(objectsPath);     
            } catch (IOException e) {
                throw new IOException("Erro ao criar o diretório objects.");
            }
        }

        // Cria o arquivo index se não existir
        Path indexPath = Paths.get(path, ".criptogit", "index");
        index = new Index();
        if (!Files.exists(indexPath)) {
            Files.write(indexPath, new byte[0]);
        } else {
            // Se o arquivo index existir, lê todas as linhas e cria uma lista de blobs para usar em memória
            List<String> indexBlobs = Files.readAllLines(indexPath);
            List<Blob> blobs = new ArrayList<>();
            for (String indexBlob : indexBlobs) {
                // Ignora linhas vazias
                if (indexBlob.isEmpty()) {
                    continue;
                }
                Blob blob = new Blob();
                blob.setHash(indexBlob.split(" ")[0]);
                blob.setRelativePath(indexBlob.split(" ")[1]);
                blobs.add(blob);                
            }
            index.setBlobs(blobs);
        }
        
        System.out.println("Repositório inicializado com sucesso: " + repositorio.getName());       
    }

    public void add (String filename) throws Exception {
        // Se o usuário fizer 'add .', cria o objeto de todos os arquivos e diretórios do repositório
        if (filename.equals(".")) {
            try {
                generateObjects(repositorio.getPath());
            } catch (Exception e) {
                throw new Exception("Erro ao criar os objects: " + e.getMessage());
            }
        } else {
            // Se o usuário fizer 'add <filename>', cria o blob do arquivo e as trees dos diretórios pais
            try {
                addSpecificFile(filename);
            } catch (Exception e) {
                throw new Exception("Erro ao criar o object: " + e.getMessage());
            }
        }
    }

    /**
     * Procura o arquivo no diretório e cria o blob do arquivo
     * @param filename
     * @throws Exception
     */
    
    private void addSpecificFile(String filename) throws Exception {
        // Pega o caminho do diretório objects
        Path objectsPath = Paths.get(repositorio.getPath(), ".criptogit", "objects");
        
        // Limpa o nome do arquivo, caso tenha sido inputado como caminho
        filename = filename.trim();
        if (filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
        }
        // Procura o arquivo em toda a estrutura de diretórios
        Path filePath = filename.contains(repositorio.getPath()) ? Paths.get(filename) : findFileInRepository(filename);
        
        // Se o arquivo não for encontrado, lança uma exceção
        if (filePath == null) {
            throw new IOException("Arquivo não encontrado: " + filename);
        }
        
        // Se o caminho especificado não for um arquivo, lança uma exceção
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("O caminho especificado não é um arquivo: " + filename);
        }
        
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        
        // Cria o blob do arquivo e adiciona ao index
        processFile(filePath, objectsPath, md);
    }
    /**
     * Processa um diretório e persiste sua tree no diretório objects
     * @param directory
     * @param objectsPath
     * @param md
     * @param currentTree
     * @throws IOException
     */
    
    private void processDirectory(Path directory, Path objectsPath, MessageDigest md, Tree currentTree) throws IOException {
        StringBuilder blobContent = new StringBuilder();
            for (Arquivo arquivo : currentTree.getArquivos()) {
                blobContent.append("blob ").append(arquivo.getName()).append(" ").append(arquivo.getBlob().getHash()).append("\n");
            }
            for (Tree tree : currentTree.getTrees()) {
                blobContent.append("tree ").append(tree.getName()).append(" ").append(tree.getHash()).append("\n");
            }
            
            // Faz a hash SHA-1 do conteúdo do blob da tree
            byte[] sha1bytes = md.digest(blobContent.toString().getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : sha1bytes) {
                sb.append(String.format("%02x", b));
            }
            currentTree.setHash(sb.toString());
            
            // Define o conteúdo e a hash do blob da tree
            Blob treeBlob = new Blob();
            treeBlob.setContent(blobContent.toString().getBytes());
            treeBlob.setHash(currentTree.getHash());
            
            // Pega os 2 primeiros caracteres da hash para o diretório e os 38 restantes para o arquivo
            String hash = currentTree.getHash();
            String dirName = hash.substring(0, 2);        // 2 primeiros caracteres
            String fileName = hash.substring(2);         // 38 caracteres restantes

            // Confere se a pasta existe
            // Se não existir, cria a pasta
            Path objectDir = Paths.get(objectsPath.toString(), dirName);
            createDirectory(objectDir);
            // Confere se o blob existe
            // Se não existir, cria o blob
            Path objectFile = Paths.get(objectDir.toString(), fileName);
            createFile(objectFile, treeBlob);        
            //this.index.addBlob(treeBlob, repositorio.getPath());
    }
    
    private Path findFileInRepository(String filename) throws IOException {
        Path repoRoot = Paths.get(repositorio.getPath());
        return findFileRecursively(repoRoot, filename);
    }
    
    private Path findFileRecursively(Path currentPath, String filename) throws IOException {
        // Lista todos os itens do diretório atual
        try (var stream = Files.list(currentPath)) {
            for (Path item : stream.collect(java.util.stream.Collectors.toList())) {
                // Ignora o diretório .criptogit
                if (item.toString().contains(".criptogit")) {
                    continue;
                }
                
                if (Files.isDirectory(item)) {
                    // Se é um diretório, busca recursivamente
                    Path found = findFileRecursively(item, filename);
                    if (found != null) {
                        return found;
                    }
                } else if (Files.isRegularFile(item)) {
                    // Se é um arquivo, verifica se o nome corresponde
                    if (item.getFileName().toString().equals(filename)) {
                        return item;
                    }
                }
            }
        }
        return null; // Arquivo não encontrado neste diretório
    }

    /*
     * Cria as trees necessarias para o commit, incluindo a tree raiz
     * Cria o object do blob do commit em si, que guardará os metadados
     * @param message
     * @throws Exception
     */
    
    public void commit(String message) throws Exception {
        // Verifica se o diretório .criptogit existe
        if (!Files.exists(Paths.get(repositorio.getPath(), ".criptogit"))) {
            throw new IOException("Diretório .criptogit não existe. Execute o comando init para criar um repositório CriptoGit.");
        }
        // Verifica se o diretório objects existe
        Path objectsPath = Paths.get(repositorio.getPath(), ".criptogit", "objects");
        if (!Files.exists(objectsPath)) {            
            throw new IOException("Diretório objects não existe. Execute o comando init para criar um repositório CriptoGit.");
        }
        // Cria o objeto de commit com os metadados
        Commit commit = new Commit();
        commit.setMessage(message);
        commit.setDate(new Date().toString());
        commit.setAuthor(System.getProperty("user.name"));
        // Procura se existe um commit anterior através do HEAD
        Path headPath = Paths.get(repositorio.getPath(), ".criptogit", "HEAD");
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
        Tree rootTree = processTreesRecursively(Paths.get(repositorio.getPath()), objectsPath, md);
        // Define o apontamento para a tree raiz
        commit.setRootTree(rootTree);
        // Salva o objeto de commit no diretório objects
        processCommit(commit, objectsPath, md);

        // Cria/Atualiza o arquivo HEAD, apontando pra esse commit        
        Files.write(headPath, commit.getHash().getBytes());

        // Limpa o index
        Path indexPath = Paths.get(repositorio.getPath(), ".criptogit", "index");
        Files.write(indexPath, new byte[0]);

        // Criptografa o commit
        CriptografiaService criptografiaService = new CriptografiaService();
        criptografiaService.encryptBlobs(repositorio.getPath(), commit.getHash());
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
        
        System.out.println("Index reconstruído com " + index.getBlobs().size() + " arquivos do commit " + parentHash);
    }
    
    /**
     * Lê o commit e extrai a hash da tree raiz
     */
    private String getRootTreeHashFromCommit(String commitHash) throws IOException {
        // Procura o blob do commit
        Path objectsPath = Paths.get(repositorio.getPath(), ".criptogit", "objects");
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
        Path objectsPath = Paths.get(repositorio.getPath(), ".criptogit", "objects");
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
                    
                    System.out.println("Blob referenciado: " + relativePath + " (hash: " + hash + ")");
                    
                } else if ("tree".equals(type)) {
                    // Se aquela linha referenciar uma tree
                    // Chama recursivamente para processar a sub-tree
                    String newPath = currentPath.isEmpty() ? "\\" + name : currentPath + "\\" + name;
                    getLastCommitFilesRecursively(hash, newPath);
                }
            }
        }
    }

    // Sobrecarga do método - versão sem Tree (para uso externo)
    private Tree processTreesRecursively(Path currentPath, Path objectsPath, MessageDigest md) throws IOException {
        return processTreesRecursively(currentPath, objectsPath, md, null);        
    }

    /**
     * Processa as Trees recursivamente
     * @param currentPath
     * @param objectsPath
     * @param md
     * @param parentTree
     * @throws IOException
     */
    private Tree processTreesRecursively(Path currentPath, Path objectsPath, MessageDigest md, Tree parentTree) throws IOException {
        // Lista todos os os itens do diretório atual
        try (var stream = Files.list(currentPath)) {
            // Cria uma nova Tree para o diretório atual
            Tree currentTree = new Tree();
            currentTree.setName(currentPath.getFileName().toString());
            for (Path item : stream.collect(java.util.stream.Collectors.toList())) {
                // Se o index estiver vazio, termina o processamento
                if (index.getBlobs().isEmpty()) {
                    break;
                }
                // Ignora diretórios que começam com .
                if (item.toString().contains("\\.")) {
                    continue;
                }
                if (Files.isDirectory(item)) {
                    // Se é um diretório, processa recursivamente
                    processTreesRecursively(item, objectsPath, md, currentTree);
                } else if (Files.isRegularFile(item)) {
                    // Confere se é um dos arquivos inclusos no index
                    for (Blob blob : index.getBlobs()) {
                        if (item.toString().contains(blob.getRelativePath())) {
                            // Se é um arquivo listado no index, cria o objeto Arquivo e adiciona à Tree atual
                            Arquivo arquivo = new Arquivo();
                            arquivo.setName(item.getFileName().toString());
                            arquivo.setBlob(blob);
                            currentTree.addArquivo(arquivo);
                            // Remove o arquivo do index em memória, pois ele já foi processado
                            index.removeBlob(blob);
                            break;
                        }
                    }
                }
            }
            // Depois de montada uma Tree, monta um blob e salva no diretório objects
            // Para cada arquivo e tree da Tree atual, escreve seu nome e sua hash no blob
            if (currentTree.getArquivos().size() > 0 || currentTree.getTrees().size() > 0) {
                processDirectory(currentPath, objectsPath, md, currentTree);
                // Se há um parentTree, adiciona a currentTree como filha
                if (parentTree != null) {
                    parentTree.addTree(currentTree);
                    return null;
                }
            }
            return currentTree;
        }
    }

    public void generateObjects(String path) throws Exception {
        // Cria os objects de um diretório com .criptogit
        Path criptogitPath = Paths.get(path, ".criptogit");
        // Verifica se o diretório .criptogit existe
        if (!Files.exists(criptogitPath)) {
            throw new IOException("Diretório .criptogit não existe. Execute o comando init para criar um repositório CriptoGit.");
        }
        // Verifica se o diretório objects existe
        Path objectsPath = Paths.get(path, ".criptogit", "objects");
        if (!Files.exists(objectsPath)) {
            // Cria o diretório objects
            System.out.println("Criando o diretório objects...");
            Files.createDirectory(objectsPath);
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            // Inicia o processamento recursivo
            processDirectoryRecursively(Paths.get(path), objectsPath, md);
        } catch (IOException | NoSuchAlgorithmException e) {            
            throw new Exception("Erro ao processar arquivos: " + e.getMessage());
        }
        System.out.println("Objects criados com sucesso.");
    }
    
    // Sobrecarga do método - versão sem Tree (para uso externo)
    private void processDirectoryRecursively(Path currentPath, Path objectsPath, MessageDigest md) throws IOException {
        processDirectoryRecursively(currentPath, objectsPath, md, null);
    }
    
    // Método principal com Tree
    private void processDirectoryRecursively(Path currentPath, Path objectsPath, MessageDigest md, Tree parentTree) throws IOException {
        // Lista todos os itens do diretório atual
        try (var stream = Files.list(currentPath)) {
            // Cria uma nova Tree para o diretório atual
            Tree currentTree = new Tree();               
            currentTree.setName(currentPath.getFileName().toString());
            System.out.println("Processando diretório: " + currentTree.getName());
            
            // Processa cada arquivo e diretório do diretório atual
            for (Path item : stream.collect(java.util.stream.Collectors.toList())) {
                // Ignora diretórios que começam com .
                if (item.toString().contains("\\.")) {
                    continue;
                }
                
                if (Files.isDirectory(item)) {
                    // Se é um diretório, processa recursivamente
                    System.out.println("Processando subdiretório: " + item.getFileName());
                    processDirectoryRecursively(item, objectsPath, md, currentTree);
                } else if (Files.isRegularFile(item)) {
                    // Se é um arquivo, cria o blob
                    Arquivo file = processFile(item, objectsPath, md);
                    currentTree.addArquivo(file);
                }
            }
            // Depois de montada uma Tree, monta um blob e salva no diretório objects
            // Para cada arquivo e tree da Tree atual, escreve seu nome e sua hash no blob
            processDirectory(currentPath, objectsPath, md, currentTree);
            
            // Se há um parentTree, adiciona a currentTree como filha
            if (parentTree != null) {
                parentTree.addTree(currentTree);
            }
        }
    }
    
    private Arquivo processFile(Path file, Path objectsPath, MessageDigest md) throws IOException {
        String name = file.getFileName().toString();
        System.out.println("Criando o object do arquivo: " + name);         

        // Pega o conteúdo do arquivo e cria um objeto Blob
        Blob blob = new Blob();
        blob.setContent(Files.readAllBytes(file));

        // Faz a hash SHA-1 do conteúdo do arquivo                
        byte[] sha1bytes = md.digest(blob.getContent());
        StringBuilder sb = new StringBuilder();
        for (byte b : sha1bytes) {
            sb.append(String.format("%02x", b));
        }
        blob.setHash(sb.toString());

        // Pega os 2 primeiros caracteres da hash para o diretório e os 38 restantes para o arquivo
        String hash = blob.getHash();
        String dirName = hash.substring(0, 2);        // 2 primeiros caracteres
        String fileName = hash.substring(2);         // 38 caracteres restantes

        // Confere se a pasta existe
        // Se não existir, cria a pasta
        Path objectDir = Paths.get(objectsPath.toString(), dirName);        
        createDirectory(objectDir);
        // Confere se o blob existe
        // Se não existir, cria o blob
        Path objectFile = Paths.get(objectDir.toString(), fileName);
        createFile(objectFile, blob);

        Arquivo arquivo = new Arquivo();
        arquivo.setName(name);
        arquivo.setBlob(blob);
        this.index.addBlob(arquivo.getBlob(), repositorio.getPath(), file.toString());
        return arquivo;
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
        createDirectory(objectDir);
        // Cria o blob
        Path objectFile = Paths.get(objectDir.toString(), fileName);
        createFile(objectFile, commitBlob);
    }
    /**
     * Cria uma pasta no diretório objects
     * @param objectDir
     * @throws IOException
     */
    public void createDirectory(Path objectDir) throws IOException {
        // Confere se a pasta da tree existe
        // Se não existir, cria a pasta
        if (!Files.exists(objectDir)) {
            System.out.println("Criando a pasta: objects/" + objectDir.getFileName().toString());
            Files.createDirectory(objectDir);
        }
    }
    /**
     * Cria um arquivo no diretório objects
     * @param objectFile
     * @param blob
     * @throws IOException
     */
    public void createFile(Path objectFile, Blob blob) throws IOException {
        // Confere se o blob do arquivo existe
        // Se não existir, cria o blob
        if (!Files.exists(objectFile)) {
            System.out.println("Criando o object: " + objectFile.getFileName().toString());
            Files.write(objectFile, blob.getContent());
        }
    }
    
    /**
     * Retorna o repositório atual
     * @return Repositório atual
     */
    public Repositorio getRepositorio() {
        return repositorio;
    }

}
