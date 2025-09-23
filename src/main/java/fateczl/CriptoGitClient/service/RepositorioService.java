package fateczl.CriptoGitClient.service;

import fateczl.CriptoGitClient.model.Repositorio;
import fateczl.CriptoGitClient.model.Blob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class RepositorioService {
    private Repositorio repositorio;

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
                // Cria o diretório objects dentro do .criptogit
                Path objectsPath = Paths.get(path, ".criptogit", "objects");                
                Files.createDirectory(objectsPath);                
            } catch (IOException e) {
                throw new IOException("Erro ao criar o diretório .criptogit.");
            }
        }
        
        System.out.println("Repositório inicializado com sucesso: " + repositorio.getName());
        System.out.println("Criando os objects...");
        try {
            generateObjects(path);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IOException("Erro ao criar os objects: " + e.getMessage());
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
        // Cria os objects de todos os arquivos do diretório, exceto .criptogit
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            
            // Usa Stream para coletar os arquivos em uma lista
            List<Path> files = Files.walk(Paths.get(path))
                .filter(Files::isRegularFile)
                .filter(file -> !file.toString().contains(".criptogit"))
                .collect(java.util.stream.Collectors.toList());
            
            // Loop tradicional para processar cada arquivo
            for (Path file : files) {
                String separator = System.getProperty("file.separator");
                String name = file.toString().substring(file.toString().lastIndexOf(separator) + 1);
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
                
                // Cria o diretório com os 2 primeiros caracteres
                Path objectDir = Paths.get(objectsPath.toString(), dirName);
                Files.createDirectories(objectDir);
                
                // Cria o arquivo com os 38 caracteres restantes
                Path objectFile = Paths.get(objectDir.toString(), fileName);
                Files.write(objectFile, blob.getContent());
            }
        } catch (IOException | NoSuchAlgorithmException e) {            
            throw new Exception("Erro ao processar arquivos: " + e.getMessage());
        }
        System.out.println("Objects criados com sucesso.");
    }
}
