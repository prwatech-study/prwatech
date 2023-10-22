package com.prwatech.notification.service.impl;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.notification.dto.PushNotificationRequestDto;
import com.prwatech.notification.service.PushNotificationService;
import com.prwatech.user.model.UserFcmToken;
import com.prwatech.user.repository.UserFcmRepository;
import com.prwatech.user.template.UserFcmTokenTemplate;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class PushNotificationServiceImpl implements PushNotificationService {

    private final UserFcmRepository userFcmRepository;
    private final UserFcmTokenTemplate userFcmTokenTemplate;

    private final Logger LOGGER = LoggerFactory.getLogger(PushNotificationServiceImpl.class);

    @Override
    public Boolean createOrUpdateFcmToken(String userId, String token) {

        UserFcmToken userFcmToken = userFcmTokenTemplate.getByUserId(new ObjectId(userId));
        if(Objects.isNull(userFcmToken)){
            userFcmToken= new UserFcmToken();
            userFcmToken.setUserId(new ObjectId(userId));
        }
        userFcmToken.setFcmToken(token);
        userFcmRepository.save(userFcmToken);

        return true;
    }

    @Override
    public void sendPushNotification(PushNotificationRequestDto requestDto) {

        List<String> tokens= userFcmRepository.findAll().stream().map(UserFcmToken::getFcmToken).collect(Collectors.toList());

        Notification notification = Notification.builder()
                .setBody((requestDto.getMessage().length()<10)? requestDto.getMessage() : requestDto.getMessage().substring(0,10))
                .setTitle((requestDto.getTitle().length()>10)?requestDto.getTitle().substring(0,10):requestDto.getTitle())
                .build();
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(notification)
                .build();
        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
            LOGGER.info("Successful hits for notification :: {}", response.getSuccessCount());
            LOGGER.info("Failed hits for notification :: {}", response.getFailureCount());
        }
        catch (Exception ex){
             LOGGER.error("Unable to sent push notification :: {}", ex.getMessage());
             throw new UnProcessableEntityException("Unable to sent notifications due to ::"+ex.getMessage());
        }
    }
}
