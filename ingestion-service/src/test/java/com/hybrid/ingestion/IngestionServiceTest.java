package com.hybrid.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hybrid.ingestion.model.DocumentRequest;
import com.hybrid.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class IngestionServiceTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        ingestionService = new IngestionService(kafkaTemplate);
    }

    @Test
    void testProcessDocumentPublishesToKafka() throws JsonProcessingException {
        DocumentRequest request = new DocumentRequest();
        request.setId("123");
        request.setTitle("Hybrid Search");
        request.setContent("Content body");
        request.setMetadata("meta");

        ingestionService.processDocument(request);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(anyString(), eq("123"), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Hybrid Search");
    }
}
