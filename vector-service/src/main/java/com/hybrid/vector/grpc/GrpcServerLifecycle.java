package com.hybrid.vector.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GrpcServerLifecycle implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final VectorSearchGrpcApi vectorSearchGrpcApi;
    private final int port;
    private Server server;

    public GrpcServerLifecycle(
            VectorSearchGrpcApi vectorSearchGrpcApi,
            @Value("${grpc.server.port:9094}") int port
    ) {
        this.vectorSearchGrpcApi = vectorSearchGrpcApi;
        this.port = port;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(vectorSearchGrpcApi)
                .build()
                .start();
        log.info("gRPC server started for vector-service on port {}", port);
    }

    @Override
    public void destroy() {
        if (server != null) {
            server.shutdown();
            log.info("gRPC server stopped for vector-service");
        }
    }
}
