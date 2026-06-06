package com.sra.journal_tracking.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Journal Tracking API", version = "1.0", description = "API Documentation for Journal Tracking System"))
public class OpenApiConfig {

}
