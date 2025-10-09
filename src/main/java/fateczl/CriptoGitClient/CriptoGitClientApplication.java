package fateczl.CriptoGitClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import fateczl.CriptoGitClient.service.ConsoleService;


@SpringBootApplication
public class CriptoGitClientApplication {

	public static void main(String[] args) {
		System.out.println("Iniciando CriptoGit Client...");
		
		// Executa a aplicação sem inicializar o Spring Boot automaticamente
		ConsoleService consoleService = new ConsoleService();
		consoleService.run();
		consoleService.close();
		
		// Opcional: Inicializar Spring Boot apenas se necessário
		// ConfigurableApplicationContext context = SpringApplication.run(CriptoGitClientApplication.class, args);
		// context.close();
		
		System.out.println("Aplicação encerrada.");
	}

}
