package fateczl.CriptoGitClient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

import fateczl.CriptoGitClient.model.Arquivo;
import fateczl.CriptoGitClient.model.Blob;
import fateczl.CriptoGitClient.model.Index;
import fateczl.CriptoGitClient.model.Tree;

public class TreeService {

    private FileService fileService;
    private Index index;
   
    public void setIndex(Index index) {
        this.index = index;
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
            fileService = new FileService();
            fileService.setIndex(index);
            fileService.createFile(objectFile, treeBlob);        
            //this.index.addBlob(treeBlob, repositorio.getPath());
    }
    
    // Sobrecarga do método - versão sem Tree (para uso externo)
    public Tree processTreesRecursively(Path currentPath, Path objectsPath, MessageDigest md) throws IOException {
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

    // Sobrecarga do método - versão sem Tree (para uso externo)
    public void processDirectoryRecursively(Path currentPath, Path objectsPath, MessageDigest md) throws IOException {
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
                    Arquivo file = fileService.processFile(item, objectsPath, md);
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
    
}
