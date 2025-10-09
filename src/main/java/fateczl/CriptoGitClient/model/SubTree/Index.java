package fateczl.CriptoGitClient.model.SubTree;

import java.util.ArrayList;
import java.util.List;

import fateczl.CriptoGitClient.model.Blob;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

public class Index {
    private List<Blob> blobs;

    public Index() {
    this.blobs = new ArrayList<>();
    }

    public void addBlob(Blob blob, String repositorioPath, String filePath) throws IOException {
        try {
            // Remove o caminho do repositório do caminho do arquivo, mantendo apenas o caminho relativo
            filePath = filePath.replace(repositorioPath, "");
            blob.setRelativePath(filePath);
            // Adiciona o blob ao index
            this.blobs.add(blob);
            StringBuilder indexContent = new StringBuilder();
            // Monta a linha do index com a SHA-1 do blob e o caminho do arquivo
            indexContent.append(blob.getHash()).append(" ").append(filePath).append("\n");
            Path indexPath = Paths.get(repositorioPath, ".criptogit", "index");
            Files.write(indexPath, indexContent.toString().getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IOException("Erro ao adicionar o blob " + blob.getHash() + " no index do repositório " + repositorioPath + ": " + e.getMessage());
        }
    }

    public void removeBlob(Blob blob) {
        this.blobs.remove(blob);
    }

    public List<Blob> getBlobs() {
        return blobs;
    }

    public void setBlobs(List<Blob> blobs) {
        this.blobs = blobs;
    }
}