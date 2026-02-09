package com.hybrid.indexing.service;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SolrIndexService {

    private final SolrClient solrClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentLinkedQueue<SolrInputDocument> pendingDocs = new ConcurrentLinkedQueue<>();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final int batchSize;

    public SolrIndexService(
            @Value("${solr.url}") String solrUrl,
            @Value("${solr.batch-size:500}") int batchSize
    ) {
        this.solrClient = new HttpSolrClient.Builder(solrUrl).build();
        this.batchSize = Math.max(1, batchSize);
    }

    public void indexDocument(String message) {
        try {
            Map<String, Object> docMap = mapper.readValue(message, Map.class);
            SolrInputDocument doc = toSolrDocument(docMap);
            pendingDocs.add(doc);

            if (pendingDocs.size() >= batchSize) {
                flushBatch();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SolrInputDocument toSolrDocument(Map<String, Object> docMap) {
        SolrInputDocument doc = new SolrInputDocument();

        Object id = docMap.get("id");
        if (id != null) {
            doc.addField("id", String.valueOf(id));
        }

        String title = asNonBlankString(docMap.get("title"));
        String content = asNonBlankString(docMap.get("content"));
        String metadata = asNonBlankString(docMap.get("metadata"));

        if (title != null) {
            // Keep raw field for compatibility and index via *_t for lexical queryability.
            doc.addField("title", title);
            doc.addField("title_t", title);
        }
        if (content != null) {
            doc.addField("content", content);
            doc.addField("content_t", content);
        }
        if (metadata != null) {
            doc.addField("metadata", metadata);
            doc.addField("metadata_t", metadata);
        }

        docMap.forEach((key, value) -> {
            if (value == null || "id".equals(key) || "title".equals(key) || "content".equals(key) || "metadata".equals(key)) {
                return;
            }
            doc.addField(key, value);
        });

        return doc;
    }

    private String asNonBlankString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    @Scheduled(fixedDelayString = "${solr.commit-interval-ms:2000}")
    public void flushScheduled() {
        flushBatch();
    }

    @PreDestroy
    public void flushOnShutdown() {
        flushBatch();
    }

    private void flushBatch() {
        if (!flushLock.tryLock()) {
            return;
        }
        try {
            if (pendingDocs.isEmpty()) {
                return;
            }

            ArrayList<SolrInputDocument> docs = new ArrayList<>(batchSize);
            while (docs.size() < batchSize) {
                SolrInputDocument next = pendingDocs.poll();
                if (next == null) {
                    break;
                }
                docs.add(next);
            }

            if (!docs.isEmpty()) {
                solrClient.add(docs);
                solrClient.commit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            flushLock.unlock();
        }
    }
}
