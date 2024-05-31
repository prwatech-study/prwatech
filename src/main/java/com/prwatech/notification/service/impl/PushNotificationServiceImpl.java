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
        if (Objects.isNull(userFcmToken)) {
            userFcmToken = new UserFcmToken();
            userFcmToken.setUserId(new ObjectId(userId));
        }
        userFcmToken.setFcmToken(token);
        userFcmRepository.save(userFcmToken);

        return true;
    }

    @Override
    public String sendPushNotification(PushNotificationRequestDto requestDto) {

        List<String> tokens = userFcmRepository.findAll().stream().map(UserFcmToken::getFcmToken)
                .collect(Collectors.toList());

        if (tokens.size() == 0 || tokens.isEmpty()) {
            LOGGER.error("No token found to send notification!");
            return "No token found to send notification!";
        }

        // Batch the tokens to handle the 500 tokens limit
        List<List<String>> batches = batchTokens(tokens, 500);

        int successCount = 0;
        int failureCount = 0;
        for (List<String> batch : batches) {
            Notification notification = Notification.builder()
                    .setBody((requestDto.getMessage().length() > 120) ? requestDto.getMessage().substring(0, 120)
                            : requestDto.getMessage())
                    .setTitle((requestDto.getTitle().length() > 50) ? requestDto.getTitle().substring(0, 50)
                            : requestDto.getTitle())
                    .build();
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(batch)
                    .setNotification(notification)
                    .build();
            try {
                BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
                successCount += response.getSuccessCount();
                failureCount += response.getFailureCount();
            } catch (Exception ex) {
                LOGGER.error("Unable to sent push notification :: {}", ex.getMessage());
                throw new UnProcessableEntityException("Unable to sent notifications due to ::" + ex.getMessage());
            }
        }
        LOGGER.info("Successful hits for notification :: {}", successCount);
        LOGGER.info("Failed hits for notification :: {}", failureCount);

        return "Successfully sent " + successCount + " messages. Failed to send " + failureCount + " messages.";

    }

    private List<List<String>> batchTokens(List<String> tokens, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += batchSize) {
            int end = Math.min(tokens.size(), i + batchSize);
            batches.add(tokens.subList(i, end));
        }
        return batches;
    }
}
