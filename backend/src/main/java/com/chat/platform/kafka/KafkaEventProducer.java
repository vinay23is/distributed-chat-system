package com.chat.platform.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventProducer {

    private final KafkaTemplate<String, ChatEvent> kafkaTemplate;

    @Value("${app.kafka.topics.chat-messages}")
    private String chatMessagesTopic;

    @Value("${app.kafka.topics.notifications}")
    private String notificationsTopic;

    public void publishChatEvent(ChatEvent event) {
        kafkaTemplate.send(chatMessagesTopic, event.roomId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish chat event for room {}: {}", event.roomId(), ex.getMessage());
                    } else {
                        log.debug("Published chat event to partition {}", result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishNotificationEvent(ChatEvent event) {
        kafkaTemplate.send(notificationsTopic, event.senderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish notification event: {}", ex.getMessage());
                    }
                });
    }
}
