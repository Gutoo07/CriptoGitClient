package fateczl.CriptoGitClient.service;

import java.util.Scanner;


public class ConsoleService {
    private final Scanner scanner = new Scanner(System.in);
    RepositorioService repositorioService = new RepositorioService();
    FileService fileService = new FileService();
    UnlockService unlockService = new UnlockService();
    PullService pullService = new PullService();
    PushService pushService = new PushService();
    LoginService loginService = new LoginService();
    CriptografiaService criptografiaService = new CriptografiaService();
    CommitService commitService = new CommitService();
    CloneService cloneService = new CloneService();
    Settings settings = new Settings();

    public void run() {
        String command;
        try {
        do {
            command = readCommand();
            String repositorio = "";
            String nickname = "";
            String email = "";
            String senha = "";
            String repositorioId = "";
            switch (command) {
                case "init":
                    System.out.print("Digite o caminho do repositório: ");
                    String path = scanner.nextLine();
                    repositorioService.init(path);
                    break;
                case "add":
                    if (!checkRepositorioInicializado()) {
                        break;
                    }
                    System.out.print("Digite o nome do arquivo ou '.' para adicionar todos: ");
                    String filename = scanner.nextLine();
                    fileService.setRepositorioPath(repositorioService.getRepositorio().getPath());
                    fileService.setIndex(repositorioService.getIndex());
                    fileService.add(filename);
                    break;
                case "load-public-keys":
                    if (!checkRepositorioInicializado()) {
                        break;
                    }
                    System.out.print("Digite o ID do repositório remoto: ");
                    repositorioId = scanner.nextLine();
                    criptografiaService.loadPublicKeysFromServer(repositorioService.getRepositorio().getPath(), repositorioId, settings);
                    break;
                case "allow-new-collaborators":
                    if (!checkRepositorioInicializado()) {
                        break;
                    }
                    criptografiaService.encryptSymmetricKeysWithNewPublicKeys(repositorioService.getRepositorio().getPath());
                    break;
                case "clone":
                    if (!checkRepositorioInicializado()) {
                        break;
                    }
                    System.out.print("Digite o nome do repositório: ");
                    repositorio = scanner.nextLine();
                    cloneService.clone(repositorio, repositorioService.getRepositorio().getPath(), settings);
                    break;
                case "commit":
                    if (!checkRepositorioInicializado()) {
                        break;
                    }
                    System.out.print("Digite a mensagem do commit: ");
                    String message = scanner.nextLine();
                    commitService.setRepositorioPath(repositorioService.getRepositorio().getPath());
                    commitService.setIndex(repositorioService.getIndex());
                    commitService.commit(message);
                    break;
                case "pull":
                    if (!checkRepositorioInicializado()) {
                        break;
                    }
                    System.out.print("Digite o ID do repositório remoto: ");
                    repositorioId = scanner.nextLine();
                    pullService.pull(repositorioId, repositorioService.getRepositorio().getPath(), settings);
                    break;
                case "push":
                    if (!checkRepositorioInicializado()) {
                        break;
                    }
                    System.out.print("Digite o ID do repositório remoto: ");
                    repositorioId = scanner.nextLine();
                    pushService.push(repositorioService.getRepositorio().getPath(), repositorioId, settings);
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
                    if (!checkRepositorioInicializado()) {
                        break;
                    }
                    unlockService.unlock(repositorioService.getRepositorio().getPath());
                    break;
                case "create-remote-repository":
                    System.out.print("Digite o nome do repositório: ");
                    repositorio = scanner.nextLine();
                    repositorioService.createRemoteRepository(repositorio, settings);
                    break;
                case "list-remote-repositories":
                    repositorioService.listRemoteRepositories(settings);
                    break;
                case "-help":
                    System.out.println("add - Adiciona um arquivo ao repositório local");
                    System.out.println("commit - Cria um commit no repositório local");
                    System.out.println("create-remote-repository - Cria um repositório remoto");
                    System.out.println("clone - Clona um repositório remoto para o repositório local");
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
    
    private boolean checkRepositorioInicializado() {
        if (repositorioService.getRepositorio().getPath() == null || repositorioService.getRepositorio().getPath().isEmpty()) {
            System.err.println("\nErro: Repositório não inicializado. Execute 'init' primeiro.");
            return false;
        }
        return true;
    }
    
}
