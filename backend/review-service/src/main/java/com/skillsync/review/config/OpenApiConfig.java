package com.skillsync.review.config;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.ArrayList;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer gatewayHeaderCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    if (operation.getParameters() == null) {
                        operation.setParameters(new ArrayList<>());
                    }
                    operation.getParameters().add(0, new Parameter()
                            .in("header").name("X-User-Id").required(true)
                            .schema(new Schema<>().type("integer").format("int64"))
                            .description("User ID injected by gateway"));
                    operation.getParameters().add(1, new Parameter()
                            .in("header").name("X-User-Roles").required(false)
                            .schema(new Schema<>().type("string"))
                            .description("Comma-separated roles (e.g. ROLE_ADMIN). Required for moderation."));
                }));
    }
}
