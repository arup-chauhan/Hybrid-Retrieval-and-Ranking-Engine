package com.hybrid.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.ingestion.model.DocumentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic}")
    private String topicName;

    public IngestionService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
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
