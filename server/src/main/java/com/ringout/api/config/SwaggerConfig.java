package com.ringout.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    public static final String BEARER_AUTH = "JWT Authentication";

    @Bean
    public OpenAPI ringoutOpenAPI() {
        return new OpenAPI()
            .addSecurityItem(
                    new SecurityRequirement().addList(BEARER_AUTH)
            )
            .info(new Info()
                .title("Ringout API")
                .description("Ringout 앱에서 사용하는 서버 API 명세입니다.")
                .version("v1"))
            .components(new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                ));
    }


    @Bean
    public OpenApiCustomizer apiVersionOpenApiCustomizer(
            @Value("${app.api-version:v1}") String apiVersion
    ) {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            Paths versionedPaths = new Paths();

            openApi.getPaths().forEach((path, pathItem) -> {
                String resolvedPath = path.replace("{version}", apiVersion);
                versionedPaths.addPathItem(resolvedPath, pathItem);
            });

            openApi.setPaths(versionedPaths);
        };
    }
}
