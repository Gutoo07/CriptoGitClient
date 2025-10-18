package fateczl.CriptoGitClient.service;

import java.util.Scanner;


public class ConsoleService {
    private final Scanner scanner = new Scanner(System.in);
    private RepositorioService repositorioService = new RepositorioService();
    private UnlockService unlockService = new UnlockService();

    public void run() {
        String command;
        try {
        do {
            command = readCommand();
            switch (command) {
                case "init":
                    System.out.print("Digite o caminho do repositório: ");
                    String path = scanner.nextLine();
                    repositorioService.init(path);
                    break;
                case "add":
                    System.out.print("Digite o nome do arquivo ou '.' para adicionar todos: ");
                    String filename = scanner.nextLine();
                    repositorioService.add(filename);
                    break;
                case "commit":
                    System.out.print("Digite a mensagem do commit: ");
                    String message = scanner.nextLine();
                    repositorioService.commit(message);
                    break;
                case "unlock":
                    if (repositorioService.getRepositorio() == null) {
                        System.out.println("Erro: Repositório não inicializado. Execute 'init' primeiro.");
                        break;
                    }
                    unlockService.unlock(repositorioService.getRepositorio().getPath());
                    break;
                case "exit":
                    System.out.println("Saindo...");
                    break;
                default:
                    System.out.println("Comando não reconhecido: " + command);
                    break;
                }
            } while (!command.equals("exit"));
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }

    public String readCommand() {
        System.out.print("Digite um comando: ");
        return scanner.nextLine(); 
    }

    public void close() {
        scanner.close();
    }
    
}
