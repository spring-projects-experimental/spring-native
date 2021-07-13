package com.example.square;

import okhttp3.OkHttpClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.square.retrofit.EnableRetrofitClients;
import org.springframework.cloud.square.retrofit.core.RetrofitClient;
import org.springframework.context.annotation.Bean;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

@EnableRetrofitClients
@SpringBootApplication
public class SquareApplication {

	@Bean
	@LoadBalanced
	OkHttpClient.Builder okHttpClientBuilder() {
		return new OkHttpClient.Builder();
	}

	@Bean
	ApplicationRunner runner(GithubClient gc) {
		return event -> System.out.println("User profile: " + gc.getUserprofile("joshlong").execute().body());
	}

	public static void main(String[] args) {
		SpringApplication.run(SquareApplication.class, args);
	}
}

@RetrofitClient(url = "https://api.github.com", name = "github")
interface GithubClient {

	@GET("/users/{username}")
	Call<String> getUserprofile(@Path("username") String username);
}
