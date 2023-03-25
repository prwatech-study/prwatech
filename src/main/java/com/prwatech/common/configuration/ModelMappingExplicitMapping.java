// package com.prwatech.common.configuration;
//
// import com.prwatech.common.modelmapping.UserMapping;
// import org.modelmapper.Conditions;
// import org.modelmapper.ModelMapper;
// import org.modelmapper.convention.MatchingStrategies;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
//
// @Configuration
// public class ModelMappingExplicitMapping {
//
//  private static final int SIZE_COUNT_ONE = 1;
//
//  @Bean
//  public ModelMapper modelMapper() {
//    ModelMapper modelMapper = new ModelMapper();
////    modelMapper
////        .getConfiguration()
////        .setImplicitMappingEnabled(true)
////        .setMatchingStrategy(MatchingStrategies.LOOSE)
////        .setPropertyCondition(Conditions.isNotNull()
////        );
//
//    modelMapper.addMappings(UserMapping.getUserToSignUpRequestDto());
//    return modelMapper;
//  }
// }
