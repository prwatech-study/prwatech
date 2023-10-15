package com.prwatech.notification.service;

import com.prwatech.notification.dto.PushNotificationRequestDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public interface PushNotificationService {


    Boolean createOrUpdateFcmToken(String userId, String token);


    void sendPushNotification(PushNotificationRequestDto requestDto);
}
