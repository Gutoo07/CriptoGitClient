package fateczl.CriptoGitClient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import fateczl.CriptoGitClient.model.Arquivo;
import fateczl.CriptoGitClient.model.Blob;
import fateczl.CriptoGitClient.model.Index;

public class FileService {

    private String repositorioPath;
    private Index index;
    private TreeService treeService;

    public void setRepositorioPath(String repositorioPath) {
        this.repositorioPath = repositorioPath;
    }

    public void setIndex(Index index) {
        this.index = index;
    }

    /**
     * Gera o object de um arquivo e o adiciona ao arquivo index do repositório
     * @param filename Nome do arquivo ou diretório
     * @throws Exception Se houver erro ao adicionar o arquivo
     */
    public void add (String filename) throws Exception {
        // Se o usuário fizer 'add .', cria o objeto de todos os arquivos e diretórios do repositório
        if (filename.equals(".")) {
            try {
                generateObjects(repositorioPath);
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
        Path objectsPath = Paths.get(repositorioPath, ".criptogit", "objects");
        
        // Limpa o nome do arquivo, caso tenha sido inputado como caminho
        filename = filename.trim();
        if (filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
        }
        // Procura o arquivo em toda a estrutura de diretórios
        Path filePath = filename.contains(repositorioPath) ? Paths.get(filename) : findFileInRepository(filename);
        
        // Se o arquivo não for encontrado, lança uma exceção
        if (filePath == null) {
            System.out.println("Arquivo não encontrado: " + filename);
            return;
        }
        
        // Se o caminho especificado não for um arquivo, lança uma exceção
        if (!Files.isRegularFile(filePath)) {
            System.out.println("O caminho especificado não é um arquivo: " + filename);
            return;
        }
        
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        
        // Cria o blob do arquivo e adiciona ao index
        processFile(filePath, objectsPath, md);
    }

    /**
     * Gera os objects de um diretório com .criptogit
     * @param path Caminho do diretório
     * @throws Exception Se houver erro ao gerar os objects
     */
    public void generateObjects(String path) throws Exception {
        // Cria os objects de um diretório com .criptogit
        Path criptogitPath = Paths.get(path, ".criptogit");
        // Verifica se o diretório .criptogit existe
        if (!Files.exists(criptogitPath)) {
            System.err.println("\nErro: Diretório .criptogit não existe. Execute o comando init para criar um repositório CriptoGit.");
            return;
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
            treeService = new TreeService();
            treeService.setIndex(index);
            treeService.processDirectoryRecursively(Paths.get(path), objectsPath, md);
        } catch (IOException | NoSuchAlgorithmException e) {            
            throw new Exception("Erro ao processar arquivos: " + e.getMessage());
        }
        System.out.println("Objects criados com sucesso.");
    }

    /**
     * Procura um arquivo no repositório
     * @param filename Nome do arquivo
     * @return Caminho do arquivo
     * @throws IOException Se houver erro ao procurar o arquivo
     */
    private Path findFileInRepository(String filename) throws IOException {
        Path repoRoot = Paths.get(repositorioPath);
        return findFileRecursively(repoRoot, filename);
    }
    
    /**
     * Procura um arquivo no repositório recursivamente
     * @param currentPath Caminho do diretório atual
     * @param filename Nome do arquivo
     * @return Caminho do arquivo
     * @throws IOException Se houver erro ao procurar o arquivo
     */
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

    /**
     * Processa um arquivo e cria o objeto Blob
     * @param file Caminho do arquivo
     * @param objectsPath Caminho do diretório objects
     * @param md MessageDigest
     * @return Arquivo
     * @throws IOException Se houver erro ao processar o arquivo
     */
    public Arquivo processFile(Path file, Path objectsPath, MessageDigest md) throws IOException {
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
        treeService = new TreeService();
        treeService.setIndex(index);
        treeService.createDirectory(objectDir);
        // Confere se o blob existe
        // Se não existir, cria o blob
        Path objectFile = Paths.get(objectDir.toString(), fileName);
        createFile(objectFile, blob);

        Arquivo arquivo = new Arquivo();
        arquivo.setName(name);
        arquivo.setBlob(blob);
        this.index.addBlob(arquivo.getBlob(), repositorioPath, file.toString());
        return arquivo;
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
