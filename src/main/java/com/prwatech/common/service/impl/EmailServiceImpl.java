package com.prwatech.common.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.dto.EmailSendDto;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;



@Component
@AllArgsConstructor
public class EmailServiceImpl {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(EmailServiceImpl.class);
  private final AppContext appContext;

  public Boolean sendSimpleMail(EmailSendDto emailSendDto){
    try {

    }
    catch (Exception e){
      LOGGER.error("Something went wrong while sending the mail to email id: with error: "+ e.getMessage());
    }
    return Boolean.FALSE;
  }

  public void sendEmail(EmailSendDto emailSendDto){

    try
    {
      String apiUrl ="https://api.prwatech.com/support/sendEmail";
      ObjectMapper objectMapper = new ObjectMapper();
      RestTemplate restTemplate = new RestTemplate();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      String requestBody = objectMapper.writeValueAsString(emailSendDto);

      HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
      restTemplate.postForEntity(apiUrl, requestEntity, String.class);

    }
    catch (Exception e){
      LOGGER.error("Not able to connect or send email via node server :: {}", e.getMessage());
    }
  }

}
