package fateczl.CriptoGitClient.service;

import java.util.Scanner;


public class ConsoleService {
    private final Scanner scanner = new Scanner(System.in);

    public void run() {
        String command;
        try {
        do {
            command = readCommand();
            switch (command) {
                case "init":
                    RepositorioService repositorioService = new RepositorioService();
                    System.out.println("Digite o caminho do repositório:");
                    String path = scanner.nextLine();
                    repositorioService.init(path);
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
        System.out.println("Digite um comando:");
        return scanner.nextLine(); 
    }

    public void close() {
        scanner.close();
    }
    
}
