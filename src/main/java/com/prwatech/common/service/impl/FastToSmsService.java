package com.prwatech.common.service.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.prwatech.common.Constants;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.dto.FastToSmsWalletDto;
import com.prwatech.common.dto.SmsSendDto;
import com.prwatech.common.dto.SmsSendResponseDto;
import com.prwatech.common.exception.UnProcessableEntityException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@AllArgsConstructor
public class FastToSmsService {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(FastToSmsService.class);
  private final AppContext appContext;

  public FastToSmsWalletDto getWalletStatement() {
    try {
      HttpResponse response =
          Unirest.post("https://www.fast2sms.com/dev/wallet")
              .header("authorization", appContext.getFastToSMSApiKey())
              .asString();

      if (response.getCode() == 200) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode object = objectMapper.readTree(response.getBody().toString());
        return new FastToSmsWalletDto(object.get("return").asText(), object.get("wallet").asText());
      }
    } catch (Exception e) {
      LOGGER.error("Something went wrong while fetching wallet statement: {}", e.getMessage());
    }
    return null;
  }

  public SmsSendResponseDto sendOtpMessage(SmsSendDto smsSendDto) throws IOException {
    try {
      RestTemplate restTemplate = new RestTemplate();

      String route = smsSendDto.getRoute();
      String authorization = appContext.getFastToSMSApiKey();
      String senderId = appContext.getSenderId();
      String messageTemplateId = appContext.getMessage();
      String variablesValues = smsSendDto.getVariables_values();
      String numbers = smsSendDto.getNumbers();

      // Construct the URL
      String apiUrl = String.format(
              "https://www.fast2sms.com/dev/bulkV2?route=%s&authorization=%s&sender_id=%s&message=%s&variables_values=%s&numbers=%s",
              route, authorization, senderId, messageTemplateId, variablesValues, numbers
      );

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<String> requestEntity = new HttpEntity<>(headers);

      ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, requestEntity, String.class);

      if (response.getStatusCode() == HttpStatus.OK) {
        ObjectMapper objectMapper = new ObjectMapper();
        SmsSendResponseDto smsSendResponseDto = objectMapper.readValue(response.getBody(), SmsSendResponseDto.class);
        if (smsSendResponseDto != null && smsSendResponseDto.getRequest_id() != null) {
          return smsSendResponseDto;
        }
      }

      LOGGER.error("Failed to send OTP. Status: {}", response.getStatusCode());
    } catch (Exception e) {
      LOGGER.error("Error in sending SMS: {}", e.getMessage());
      throw new UnProcessableEntityException("Unable to send SMS to user");
    }
    return null;
  }

}
