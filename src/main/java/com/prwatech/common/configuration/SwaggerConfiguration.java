package com.prwatech.common.configuration;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.Tag;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
@AllArgsConstructor
public class SwaggerConfiguration {

    private final AppContext appContext;

    @Bean
    public Docket iamApi(){
      return new Docket(DocumentationType.SWAGGER_2)
              .groupName("Prwatech IAM APIs")
              .tags(new Tag("Prwatech IAM API", ""))
              .enable(appContext.getIsSwaggerEnabled())
              .select()
              .apis(RequestHandlerSelectors.any())
              .paths(PathSelectors.ant("/api/iam/**"))
              .build();
    }

    @Bean
    public Docket userApi(){
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("Prwatech User APIs")
                .tags(new Tag("Prwatech User API", ""))
                .enable(appContext.getIsSwaggerEnabled())
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.ant("/api/v1/**"))
                .build();
    }

    @Bean
    public Docket publicApi(){
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("Prwatech Public APIs")
                .tags(new Tag("Prwatech Public API", ""))
                .enable(appContext.getIsSwaggerEnabled())
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.ant("/api/public/**"))
                .build();
    }
}
