package fateczl.CriptoGitClient.service;

import java.util.Scanner;


public class ConsoleService {
    private final Scanner scanner = new Scanner(System.in);
    private RepositorioService repositorioService = new RepositorioService();
    private UnlockService unlockService = new UnlockService();
    private PullService pullService = new PullService();
    private PushService pushService = new PushService();
    private LoginService loginService = new LoginService();
    private Settings settings = new Settings();
    
    public void run() {
        String command;
        try {
        do {
            command = readCommand();
            String nickname = "";
            String email = "";
            String senha = "";
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
                case "pull":
                    System.out.print("Digite o nome do repositório: ");
                    String repositorio = scanner.nextLine();
                    pullService.pull(repositorio, repositorioService.getRepositorio().getPath());
                    break;
                case "push":
                    pushService.push(repositorioService.getRepositorio().getPath());
                    break;
                case "register":
                    System.out.print("Digite o nickname: ");
                    nickname = scanner.nextLine();
                    System.out.print("Digite o email: ");
                    email = scanner.nextLine();
                    System.out.print("Digite a senha: ");
                    senha = scanner.nextLine();
                    loginService.register(nickname, email, senha, settings);
                    break;
                case "login":
                    System.out.print("Digite o email: ");
                    email = scanner.nextLine();
                    System.out.print("Digite a senha: ");
                    senha = scanner.nextLine();
                    loginService.login(email, senha, settings);
                    break;
                case "unlock":
                    if (repositorioService.getRepositorio() == null) {
                        System.out.println("Erro: Repositório não inicializado. Execute 'init' primeiro.");
                        break;
                    }
                    unlockService.unlock(repositorioService.getRepositorio().getPath());
                    break;
                case "create-remote-repository":
                    System.out.print("Digite o nome do repositório: ");
                    String repositoryName = scanner.nextLine();
                    repositorioService.createRemoteRepository(repositoryName, settings);
                    break;
                case "list-remote-repositories":
                    repositorioService.listRemoteRepositories(settings);
                    break;
                case "-help":
                    System.out.println("add - Adiciona um arquivo ao repositório local");
                    System.out.println("commit - Cria um commit no repositório local");
                    System.out.println("create-remote-repository - Cria um repositório remoto");
                    System.out.println("exit - Sai do programa");
                    System.out.println("init - Inicializa um repositório local");
                    System.out.println("list-remote-repositories - Lista todos os repositórios remotos");
                    System.out.println("login - Faz login no servidor");
                    System.out.println("pull - Puxa os commits do repositório remoto para o repositório local");
                    System.out.println("push - Envia os commits do repositório local para o repositório remoto");
                    System.out.println("register - Registra um novo usuário");
                    System.out.println("test - Testa a conexão com o servidor");
                    System.out.println("unlock - Desbloqueia o repositório local");
                    break;
                case "exit":
                    System.out.println("Saindo...");
                    break;
                case "test":
                    pullService.test();
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
        System.out.println("\n-help: Lista todos os comandos disponíveis");
        System.out.print("Digite um comando: ");
        return scanner.nextLine(); 
    }

    public void close() {
        scanner.close();
    }
    
}
