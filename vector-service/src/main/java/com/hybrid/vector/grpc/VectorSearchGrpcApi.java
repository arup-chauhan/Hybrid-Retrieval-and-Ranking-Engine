package com.hybrid.vector.grpc;

import com.hybrid.vector.model.VectorResult;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorSearchGrpcApi extends VectorSearchServiceGrpc.VectorSearchServiceImplBase {

    private final com.hybrid.vector.service.VectorSearchService vectorSearchService;

    public VectorSearchGrpcApi(com.hybrid.vector.service.VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    public void search(VectorSearchRequest request, StreamObserver<VectorSearchResponse> responseObserver) {
        int topK = request.getTopK() > 0 ? request.getTopK() : 10;
        List<VectorResult> results = vectorSearchService.search(request.getQuery(), topK);

        VectorSearchResponse.Builder responseBuilder = VectorSearchResponse.newBuilder();
        for (VectorResult result : results) {
            responseBuilder.addHits(
                    VectorHit.newBuilder()
                            .setDocumentId(safe(result.getDocumentId()))
                            .setSimilarityScore(result.getSimilarityScore())
                            .setTitle(safe(result.getTitle()))
                            .build()
            );
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
