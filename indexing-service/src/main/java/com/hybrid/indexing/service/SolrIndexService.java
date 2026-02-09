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
            SolrInputDocument doc = new SolrInputDocument();
            docMap.forEach(doc::addField);
            pendingDocs.add(doc);

            if (pendingDocs.size() >= batchSize) {
                flushBatch();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
