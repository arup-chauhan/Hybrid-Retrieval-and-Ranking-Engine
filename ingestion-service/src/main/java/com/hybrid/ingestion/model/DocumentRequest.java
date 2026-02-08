package com.hybrid.ingestion.model;

import lombok.Data;

@Data
public class DocumentRequest {
    private String id;
    private String title;
    private String content;
    private String metadata;
}
