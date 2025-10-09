package fateczl.CriptoGitClient.service;

import fateczl.CriptoGitClient.model.Arquivo;
import fateczl.CriptoGitClient.model.Tree;
import fateczl.CriptoGitClient.model.Repositorio;
import fateczl.CriptoGitClient.model.Blob;
import fateczl.CriptoGitClient.model.SubTree.Index;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ArrayList;

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
        
        // Procura o arquivo em toda a estrutura de diretórios
        Path filePath = findFileInRepository(filename);
        
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
        
        /*        
        // 2. Reconstrói as trees dos diretórios pais
        // Busca o diretório pai do arquivo e monta a tree na memória
        Path parentPath = filePath.getParent();
        Tree tree = new Tree();
        tree.setName(parentPath.getFileName().toString());
        tree.addArquivo(arquivo);
        // Processa o diretório pai do arquivo e persiste sua tree no diretório objects
        processDirectory(parentPath, objectsPath, md, tree);

        // Se o diretório pai não for o próprio repositório, processa o diretório pai
        while (!parentPath.getFileName().toString().equals(repositorio.getName())) {
            // Busca o diretório pai do diretório atual
            parentPath = parentPath.getParent();
            Tree parentTree = new Tree();
            parentTree.setName(parentPath.getFileName().toString());
            parentTree.addTree(tree);
            // Processa o diretório pai e persiste sua tree no diretório objects
            processDirectory(parentPath, objectsPath, md, parentTree);
            tree = parentTree;
        }
        */
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
                // Ignora o diretório .criptogit
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
}
