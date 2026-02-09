package com.hybrid.indexing.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.indexing.service.SolrIndexService;
import com.hybrid.indexing.service.MetadataService;
import com.hybrid.indexing.service.VectorMetadataService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentConsumer {

    private final SolrIndexService solrIndexService;
    private final MetadataService metadataService;
    private final VectorMetadataService vectorMetadataService;
    private final ObjectMapper mapper = new ObjectMapper();

    public DocumentConsumer(
            SolrIndexService solrIndexService,
            MetadataService metadataService,
            VectorMetadataService vectorMetadataService
    ) {
        this.solrIndexService = solrIndexService;
        this.metadataService = metadataService;
        this.vectorMetadataService = vectorMetadataService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic:ingestion-topic}",
            groupId = "${spring.kafka.consumer.group-id:indexing-group}"
    )
    public void consume(String message) {
        solrIndexService.indexDocument(message);
        try {
            JsonNode node = mapper.readTree(message);
            metadataService.saveMetadata(
                    node.path("id").asText(),
                    node.path("title").asText(),
                    node.path("metadata").asText()
            );
            vectorMetadataService.upsertVector(
                    node.path("id").asText(),
                    node.path("title").asText(),
                    node.path("content").asText()
            );
        } catch (Exception ignored) {}
    }
}
