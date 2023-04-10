package com.prwatech.common.configuration;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class BeanConfiguration {

  private final AppContext appContext;
}
