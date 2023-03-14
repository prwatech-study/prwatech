package com.prwatech.common.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class AppContext {

    @Value("${swagger.enabled}")
    private Boolean isSwaggerEnabled;
}
