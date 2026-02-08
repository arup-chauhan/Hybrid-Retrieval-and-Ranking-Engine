package com.hybrid.query.grpc;

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

    private final HybridQueryGrpcApi hybridQueryGrpcApi;
    private final int port;
    private Server server;

    public GrpcServerLifecycle(
            HybridQueryGrpcApi hybridQueryGrpcApi,
            @Value("${grpc.server.port:9093}") int port
    ) {
        this.hybridQueryGrpcApi = hybridQueryGrpcApi;
        this.port = port;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(hybridQueryGrpcApi)
                .build()
                .start();
        log.info("gRPC server started for query-service on port {}", port);
    }

    @Override
    public void destroy() {
        if (server != null) {
            server.shutdown();
            log.info("gRPC server stopped for query-service");
        }
    }
}
