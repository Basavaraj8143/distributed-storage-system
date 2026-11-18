package com.byteharvest.storage_node.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(NodeAuthProperties.class)
public class AppConfig {

    /**
     * The node only ever calls the master (its heartbeat), so every outgoing request carries the
     * shared token, which is what the master checks on {@code POST /heartbeat}.
     */
    @Bean
    public RestTemplate restTemplate(NodeAuthProperties nodeAuth) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(NodeTokenFilter.HEADER, nodeAuth.token());
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
