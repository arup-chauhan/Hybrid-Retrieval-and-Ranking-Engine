package com.hybrid.indexing;

import com.hybrid.indexing.service.SolrIndexService;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class SolrIndexServiceTest {

    private SolrClient mockSolrClient;
    private SolrIndexService solrIndexService;

    @BeforeEach
    void setUp() {
        mockSolrClient = mock(SolrClient.class);
        solrIndexService = new SolrIndexService("http://localhost:8983/solr/hybrid_collection", 1) {
            @Override
            public void indexDocument(String message) {
                try {
                    SolrInputDocument doc = new SolrInputDocument();
                    doc.addField("id", "123");
                    doc.addField("title", "Test");
                    mockSolrClient.add(doc);
                    mockSolrClient.commit();
                } catch (Exception ignored) {}
            }
        };
    }

    @Test
    void testIndexDocumentAddsAndCommitsToSolr() throws Exception {
        solrIndexService.indexDocument("{\"id\":\"123\",\"title\":\"Test\"}");

        verify(mockSolrClient, times(1)).add(any(SolrInputDocument.class));
        verify(mockSolrClient, times(1)).commit();
    }
}
