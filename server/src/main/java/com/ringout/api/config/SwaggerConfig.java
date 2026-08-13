package com.ringout.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI ringoutOpenAPI(
        @Value("${app.swagger.local-url}") String localUrl,
        @Value("${app.swagger.production-url}") String productionUrl
    ) {
        OpenAPI openAPI = new OpenAPI()
            .addServersItem(new Server()
                .url(localUrl)
                .description("Local server"))
            .info(new Info()
                .title("Ringout API")
                .description("Ringout 앱에서 사용하는 서버 API 명세입니다.")
                .version("v1"));

        if (StringUtils.hasText(productionUrl)) {
            openAPI.addServersItem(new Server()
                .url(productionUrl)
                .description("Production server"));
        }

        return openAPI;
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
