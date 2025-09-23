package fateczl.CriptoGitClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import fateczl.CriptoGitClient.service.ConsoleService;
import fateczl.CriptoGitClient.service.RepositorioService;

@SpringBootApplication
public class CriptoGitClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(CriptoGitClientApplication.class, args);
		ConsoleService consoleService = new ConsoleService();
		consoleService.run();
		consoleService.close();
	}

}
