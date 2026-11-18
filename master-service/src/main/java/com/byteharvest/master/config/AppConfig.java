package com.byteharvest.master.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.client.RestTemplate;

// this annotation is used in java for Annotation ConfigApplication context
@Configuration
@EnableConfigurationProperties({StorageProperties.class, NodeAuthProperties.class})
public class AppConfig {

    /**
     * The master only ever calls storage nodes with this RestTemplate, so every outgoing request
     * carries the shared node token. The nodes reject calls without it.
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

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Content-Disposition", "Content-Type");
            }
        };
    }
}
//rest templete is return type just provide the tempete
// webconfiguree is an interface
// we are overiding the method named addcorsmapping so we can configure the cors.
