package com.familier.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication(exclude = {
		net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration.class
})
@EnableReactiveMongoRepositories(basePackages = "com.familier.ai.repository")
public class FamilierAiServiceApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing()
				.load();

		dotenv.entries().forEach(entry -> {
			System.setProperty(entry.getKey(), entry.getValue());
		});
		SpringApplication.run(FamilierAiServiceApplication.class, args);
	}

}
