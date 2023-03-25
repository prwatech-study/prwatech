package com.prwatech.common.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.dto.FastToSmsWalletDto;
import com.prwatech.common.dto.SmsSendDto;
import com.prwatech.common.dto.SmsSendResponseDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

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

  public SmsSendResponseDto sentMessage(SmsSendDto smsSendDto) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      String jsonBody = objectMapper.writeValueAsString(smsSendDto);
      LOGGER.info("Json Body to send :: ------------ {}", jsonBody);
      HttpResponse response =
          Unirest.post("https://www.fast2sms.com/dev/bulkV2")
              .header("authorization", appContext.getFastToSMSApiKey())
              .header("Content-Type", "application/json")
              .body(jsonBody)
              .asString();

      if (response.getCode() == 200) {
        JsonNode jsonObject = objectMapper.readTree(response.getBody().toString());
        return new SmsSendResponseDto(
            jsonObject.get("return").asText(),
            jsonObject.get("request_id").asText(),
            jsonObject.get("message").asText());
      } else {
        JsonNode jsonObject = objectMapper.readTree(response.getBody().toString());
        LOGGER.info(
            "Sms service failed with response status code :: {} \nand message :: {}",
            response.getCode(),
            jsonObject.get("message").asText());
      }
    } catch (Exception e) {
      LOGGER.error(
          "Something went wrong in sending sms via fast to sms due to: {}", e.getMessage());
    }
    return null;
  }
}
