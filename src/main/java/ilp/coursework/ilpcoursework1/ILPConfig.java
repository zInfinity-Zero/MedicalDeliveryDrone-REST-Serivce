package ilp.coursework.ilpcoursework1;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ILPConfig {

    @Bean
    public String ilpEndpoint() {
        return System.getenv().getOrDefault(
                "ILP_ENDPOINT",
                "https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net/"
        );
    }

    @Bean
    public WebClient ilpWebClient(String ilpEndpoint) {
        return WebClient.builder()
                .baseUrl(ilpEndpoint)
                .build();
    }
}