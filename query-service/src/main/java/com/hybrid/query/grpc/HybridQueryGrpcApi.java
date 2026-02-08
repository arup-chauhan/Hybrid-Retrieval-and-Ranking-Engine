package com.hybrid.query.grpc;

import com.hybrid.query.model.QueryResult;
import com.hybrid.query.model.RankedResult;
import com.hybrid.query.service.QueryService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class HybridQueryGrpcApi extends HybridQueryServiceGrpc.HybridQueryServiceImplBase {

    private final QueryService queryService;

    public HybridQueryGrpcApi(QueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public void hybridSearch(HybridSearchRequest request, StreamObserver<HybridSearchResponse> responseObserver) {
        com.hybrid.query.model.QueryRequest internalRequest = new com.hybrid.query.model.QueryRequest();
        internalRequest.setQuery(request.getQuery());
        if (request.getTopK() > 0) {
            internalRequest.setTopK(request.getTopK());
        }

        QueryResult result = queryService.executeHybridSearch(internalRequest);
        HybridSearchResponse.Builder builder = HybridSearchResponse.newBuilder()
                .setMessage(safe(result.getMessage()))
                .setSolrResult(safe(result.getSolrResult()))
                .setVectorResult(safe(result.getVectorResult()));

        if (result.getRankedResults() != null) {
            for (RankedResult rankedResult : result.getRankedResults()) {
                builder.addRankedResults(
                        RankedResultMessage.newBuilder()
                                .setId(safe(rankedResult.getId()))
                                .setTitle(safe(rankedResult.getTitle()))
                                .setScore(rankedResult.getScore())
                                .setLexicalScore(rankedResult.getLexicalScore())
                                .setSemanticScore(rankedResult.getSemanticScore())
                                .build()
                );
            }
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void facets(FacetsRequest request, StreamObserver<FacetsResponse> responseObserver) {
        Integer limit = request.getLimit() > 0 ? request.getLimit() : null;
        String payload = queryService.fetchFacets(request.getField(), limit);
        responseObserver.onNext(FacetsResponse.newBuilder().setFacetsJson(safe(payload)).build());
        responseObserver.onCompleted();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
