package com.chat.platform.kafka;

import com.chat.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "${app.kafka.topics.notifications}", groupId = "notification-service")
    public void consume(ChatEvent event) {
        log.debug("Consuming notification event: roomId={}, senderId={}", event.roomId(), event.senderId());
        try {
            notificationService.createNotificationsForEvent(event);
        } catch (Exception e) {
            log.error("Error processing notification event: {}", e.getMessage(), e);
        }
    }
}
