package fateczl.CriptoGitClient.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.net.URI;

public class LoginService {

    public void register(String nickname, String email, String senha, Settings settings) throws IOException, InterruptedException {
        String serverUrl = settings.getServerUrl();
        String url = serverUrl + "/user";
        String requestBody = "{\"nickname\":\"" + nickname + "\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody)) 
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }

    public void login(String email, String password, Settings settings) throws IOException, InterruptedException {
        String serverUrl = settings.getServerUrl();
		String url = serverUrl + "/auth/login";
		String requestBody = "{\"email\":\"" + email + "\",\"senha\":\"" + password + "\"}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }
}
