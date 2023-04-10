package com.prwatech.common.service.impl;

import static com.prwatech.common.Constants.FTSMS_OTP_ROUT;

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
import lombok.AllArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

  public SmsSendResponseDto sentMessage(SmsSendDto smsSendDto) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();

      String senderId = "&senderId=" + "FSTSMS";
      String message = "&message=" + smsSendDto.getMessage();
      String route = "&route=" + Constants.FTSMS_ROUTE;
      String numbers = "&numbers=" + smsSendDto.getNumbers();
      String language = "&language" + Constants.DEFAULT_LANGUAGE;
      String myUrl =
          "https://www.fast2sms.com/dev/bulkV2?authorization="
              + appContext.getFastToSMSApiKey()
              + message
              + language
              + route
              + numbers;
      HttpResponse response = Unirest.get(myUrl).asString();
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

  public SmsSendResponseDto sendNormalOtpMessage(SmsSendDto smsSendDto) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      String variables_values = "variables_values=" + smsSendDto.getMessage();
      String route = "&route=" + FTSMS_OTP_ROUT;
      String numbers = "&numbers=" + smsSendDto.getNumbers();
      String myUrl = "https://www.fast2sms.com/dev/bulkV2";
      HttpResponse response =
          Unirest.post(myUrl)
              .header("authorization", appContext.getFastToSMSApiKey())
              .header("Content-Type", "application/x-www-form-urlencoded")
              .body(variables_values + route + numbers)
              .asString();
      Unirest.clearDefaultHeaders();
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

  public SmsSendResponseDto sendOtpMessage(SmsSendDto smsSendDto) throws IOException {

    final String uri = "https://www.fast2sms.com/dev/bulkV2";
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = new HttpHeaders();
      headers.add("authorization", appContext.getFastToSMSApiKey());

      Map<String, String> requestBody = new HashMap<>();
      requestBody.put("variables_values", smsSendDto.getMessage());
      requestBody.put("route", FTSMS_OTP_ROUT);
      requestBody.put("numbers", smsSendDto.getNumbers());

      HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
      ResponseEntity<String> response =
          restTemplate.exchange(uri, HttpMethod.POST, requestEntity, String.class);

      if (response.getStatusCode().equals(HttpStatus.OK)) {
        JsonNode jsonObject = objectMapper.readTree(response.getBody().toString());
        return new SmsSendResponseDto(
            jsonObject.get("return").asText(),
            jsonObject.get("request_id").asText(),
            jsonObject.get("message").asText());
      }
      LOGGER.error("Unable to send Otp to user! please try again. {}", response.getStatusCode());

    } catch (Exception e) {
      LOGGER.error("Something went wring in sms service! {}", e.getMessage());
      throw new UnProcessableEntityException("Unable to send sms to user ");
    }
    return null;
  }
}
