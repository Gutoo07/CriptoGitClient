package fateczl.CriptoGitClient.service;

import java.nio.file.Files;
import java.nio.file.Path;

public class VersionService {
    
    /**
     * Encontra o próximo número de versão baseado nos arquivos existentes na pasta .criptogit/versions
     * @param versionsPath Caminho da pasta .criptogit/versions
     * @return Próximo número de versão
     * @throws Exception Se houver erro ao processar os arquivos
     */
    public int findNextVersionNumber(Path versionsPath) throws Exception {
        int maxVersion = 0;
        
        try (var stream = Files.list(versionsPath)) {
            for (Path file : stream.collect(java.util.stream.Collectors.toList())) {
                if (Files.isRegularFile(file)) {
                    String fileName = file.getFileName().toString();
                    
                    // Verifica se o nome do arquivo é um número
                    try {
                        int versionNumber = Integer.parseInt(fileName);
                        if (versionNumber > maxVersion) {
                            maxVersion = versionNumber;
                        }
                    } catch (NumberFormatException e) {
                        // Se não for um número, ignora o arquivo
                        continue;
                    }
                }
            }
        }
        
        // Se não encontrou nenhum arquivo de versão, é o primeiro commit (versão 1)
        // Caso contrário, incrementa a maior versão encontrada
        return maxVersion == 0 ? 1 : maxVersion + 1;
    }
}
