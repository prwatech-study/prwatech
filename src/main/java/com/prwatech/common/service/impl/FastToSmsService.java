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

    String apiUrl = "https://www.fast2sms.com/dev/bulkV2";
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      RestTemplate restTemplate = new RestTemplate();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("authorization", "EV2XckTJcEWP3hWyO12ZXd5FDV1JsMjyeLwcrRyfPmnQ3YeIUtJ78exnyUGw");

      String requestBody = objectMapper.writeValueAsString(smsSendDto);

      HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
      ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);

      if (response.getStatusCode().equals(HttpStatus.OK)) {
        SmsSendResponseDto smsSendResponseDto = objectMapper.readValue(response.getBody(), SmsSendResponseDto.class);
        if(Objects.nonNull(smsSendResponseDto) && smsSendResponseDto.getRequest_id()!=null){
          return smsSendResponseDto;
        }
      }
      LOGGER.error("Unable to send Otp to user! please try again. {}", response.getStatusCode());

    } catch (Exception e) {
      LOGGER.error("Something went wring in sms service! {}", e.getMessage());
      throw new UnProcessableEntityException("Unable to send sms to user ");
    }
    return null;
  }
}
