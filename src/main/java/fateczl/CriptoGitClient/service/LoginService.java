package fateczl.CriptoGitClient.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.net.URI;

public class LoginService {
    public void login(String username, String password, Settings settings) throws IOException, InterruptedException {
        String serverUrl = settings.getServerUrl();
        String url = serverUrl + "/login" + "?username=" + username + "&password=" + password;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
    }
}
