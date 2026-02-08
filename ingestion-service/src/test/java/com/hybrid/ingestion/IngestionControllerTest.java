package com.hybrid.ingestion;

import com.hybrid.ingestion.controller.IngestionController;
import com.hybrid.ingestion.model.DocumentRequest;
import com.hybrid.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class IngestionControllerTest {

    @Test
    void testIngestDocumentCallsService() {
        IngestionService mockService = mock(IngestionService.class);
        IngestionController controller = new IngestionController(mockService);

        DocumentRequest request = new DocumentRequest();
        request.setId("1");
        controller.ingestDocument(request);

        verify(mockService, times(1)).processDocument(request);
    }
}
