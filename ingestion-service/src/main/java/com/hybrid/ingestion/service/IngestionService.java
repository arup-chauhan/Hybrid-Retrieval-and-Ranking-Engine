package com.hybrid.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.ingestion.model.DocumentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final String topicName;

    public IngestionService(KafkaTemplate<String, String> kafkaTemplate) {
        this(kafkaTemplate, "ingestion-topic");
    }

    @Autowired
    public IngestionService(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topic:ingestion-topic}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.topicName = topicName;
    }

    public void processDocument(DocumentRequest request) {
        try {
            String message = objectMapper.writeValueAsString(request);
            kafkaTemplate.send(topicName, request.getId(), message);
            System.out.println("Document published to Kafka topic: " + topicName);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing document", e);
        }
    }
}
