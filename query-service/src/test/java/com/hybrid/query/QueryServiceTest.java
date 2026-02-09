package com.hybrid.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hybrid.query.model.QueryRequest;
import com.hybrid.query.model.QueryResult;
import com.hybrid.query.service.QueryService;
import com.hybrid.query.service.SolrLexicalSearchClient;
import com.hybrid.query.service.VectorSemanticSearchClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryServiceTests {

    @Test
    void testHybridSearchRanksMergedResults() {
        SolrLexicalSearchClient solrClient = new SolrLexicalSearchClient("http://localhost:8983") {
            @Override
            public String search(String query) {
                return "{\"response\":{\"docs\":[{" +
                        "\"id\":\"doc-001\",\"title\":\"A\",\"score\":2.0},{" +
                        "\"id\":\"doc-002\",\"title\":\"B\",\"score\":1.0}]}}";
            }
        };

        VectorSemanticSearchClient vectorClient = new VectorSemanticSearchClient("http://localhost:8084") {
            @Override
            public String search(String query, Integer topK) {
                return "[{\"documentId\":\"doc-002\",\"similarityScore\":0.95},{\"documentId\":\"doc-003\",\"similarityScore\":0.80}]";
            }
        };

        QueryService queryService = new QueryService(solrClient, vectorClient, new ObjectMapper());

        QueryRequest req = new QueryRequest();
        req.setQuery("test");

        QueryResult result = queryService.executeHybridSearch(req);

        assertThat(result).isNotNull();
        assertThat(result.getRankedResults()).hasSize(3);
        assertThat(result.getRankedResults().get(0).getId()).isEqualTo("doc-002");
        assertThat(result.getRankedResults().get(0).getScore())
                .isGreaterThanOrEqualTo(result.getRankedResults().get(1).getScore());
    }

    @Test
    void testHybridSearchFallsBackWhenDownstreamFails() {
        SolrLexicalSearchClient solrClient = new SolrLexicalSearchClient("http://localhost:8983") {
            @Override
            public String search(String query) {
                throw new RuntimeException("solr down");
            }
        };

        VectorSemanticSearchClient vectorClient = new VectorSemanticSearchClient("http://localhost:8084") {
            @Override
            public String search(String query, Integer topK) {
                throw new RuntimeException("vector down");
            }
        };

        QueryService queryService = new QueryService(solrClient, vectorClient, new ObjectMapper());

        QueryRequest req = new QueryRequest();
        req.setQuery("test");

        QueryResult result = queryService.executeHybridSearch(req);

        assertThat(result).isNotNull();
        assertThat(result.getRankedResults()).isEmpty();
        assertThat(result.getSolrResult()).contains("docs");
        assertThat(result.getVectorResult()).startsWith("[");
    }

    @Test
    void testHybridSearchRespectsTopK() {
        SolrLexicalSearchClient solrClient = new SolrLexicalSearchClient("http://localhost:8983") {
            @Override
            public String search(String query) {
                return "{\"response\":{\"docs\":[{" +
                        "\"id\":\"doc-001\",\"title\":\"A\",\"score\":4.0},{" +
                        "\"id\":\"doc-002\",\"title\":\"B\",\"score\":3.0},{" +
                        "\"id\":\"doc-003\",\"title\":\"C\",\"score\":2.0}]}}";
            }
        };

        VectorSemanticSearchClient vectorClient = new VectorSemanticSearchClient("http://localhost:8084") {
            @Override
            public String search(String query, Integer topK) {
                return "[{\"documentId\":\"doc-004\",\"similarityScore\":0.95}]";
            }
        };

        QueryService queryService = new QueryService(solrClient, vectorClient, new ObjectMapper());

        QueryRequest req = new QueryRequest();
        req.setQuery("test");
        req.setTopK(2);

        QueryResult result = queryService.executeHybridSearch(req);

        assertThat(result).isNotNull();
        assertThat(result.getRankedResults()).hasSize(2);
    }

    @Test
    void testHybridSearchParsesArrayTitleAndMissingSolrScore() {
        SolrLexicalSearchClient solrClient = new SolrLexicalSearchClient("http://localhost:8983") {
            @Override
            public String search(String query) {
                return "{\"response\":{\"docs\":[{" +
                        "\"id\":\"doc-101\",\"title\":[\"Array Title A\"]},{" +
                        "\"id\":\"doc-102\",\"title\":[\"Array Title B\"]}]}}";
            }
        };

        VectorSemanticSearchClient vectorClient = new VectorSemanticSearchClient("http://localhost:8084") {
            @Override
            public String search(String query, Integer topK) {
                return "[]";
            }
        };

        QueryService queryService = new QueryService(solrClient, vectorClient, new ObjectMapper());

        QueryRequest req = new QueryRequest();
        req.setQuery("test");

        QueryResult result = queryService.executeHybridSearch(req);

        assertThat(result).isNotNull();
        assertThat(result.getRankedResults()).hasSize(2);
        assertThat(result.getRankedResults().get(0).getTitle()).isEqualTo("Array Title A");
        assertThat(result.getRankedResults().get(0).getLexicalScore()).isGreaterThan(0.0);
        assertThat(result.getRankedResults().get(1).getTitle()).isEqualTo("Array Title B");
    }

    @Test
    void testHybridSearchFallsBackToLexicalWhenVectorTimesOut() {
        SolrLexicalSearchClient solrClient = new SolrLexicalSearchClient("http://localhost:8983") {
            @Override
            public String search(String query) {
                return "{\"response\":{\"docs\":[{\"id\":\"doc-500\",\"title\":\"Only Lexical\",\"score\":5.0}]}}";
            }
        };

        VectorSemanticSearchClient vectorClient = new VectorSemanticSearchClient("http://localhost:8084") {
            @Override
            public String search(String query, Integer topK) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return "[{\"documentId\":\"doc-999\",\"similarityScore\":0.99}]";
            }
        };

        QueryService queryService = new QueryService(solrClient, vectorClient, new ObjectMapper(), 120, 60);

        QueryRequest req = new QueryRequest();
        req.setQuery("timeout");

        QueryResult result = queryService.executeHybridSearch(req, "test-trace");

        assertThat(result).isNotNull();
        assertThat(result.getVectorResult()).isEqualTo("[]");
        assertThat(result.getRankedResults()).hasSize(1);
        assertThat(result.getRankedResults().get(0).getId()).isEqualTo("doc-500");
    }
}
