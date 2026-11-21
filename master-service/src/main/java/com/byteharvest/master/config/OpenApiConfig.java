package com.byteharvest.master.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html, raw spec at /v3/api-docs.
 *
 * <p>Two security schemes are declared so "Try it out" works against the protected API:
 * {@code basicAuth} (the user login, applied to every endpoint) and {@code nodeToken}
 * (the {@code X-Node-Token} header, used only by /heartbeat).</p>
 */
@Configuration
public class OpenApiConfig {

    public static final String BASIC_AUTH = "basicAuth";
    public static final String NODE_TOKEN = "nodeToken";

    @Bean
    OpenAPI byteHarvestApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ByteHarvest Master API")
                        .version("1.0")
                        .description("Distributed chunk storage: upload, download, node health and failure simulation."))
                .components(new Components()
                        .addSecuritySchemes(BASIC_AUTH,
                                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"))
                        .addSecuritySchemes(NODE_TOKEN,
                                new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER).name("X-Node-Token")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
