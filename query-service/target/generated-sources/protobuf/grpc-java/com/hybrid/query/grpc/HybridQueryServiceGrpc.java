package com.hybrid.query.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.64.0)",
    comments = "Source: hybrid_query.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class HybridQueryServiceGrpc {

  private HybridQueryServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "HybridQueryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.hybrid.query.grpc.HybridSearchRequest,
      com.hybrid.query.grpc.HybridSearchResponse> getHybridSearchMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "HybridSearch",
      requestType = com.hybrid.query.grpc.HybridSearchRequest.class,
      responseType = com.hybrid.query.grpc.HybridSearchResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.hybrid.query.grpc.HybridSearchRequest,
      com.hybrid.query.grpc.HybridSearchResponse> getHybridSearchMethod() {
    io.grpc.MethodDescriptor<com.hybrid.query.grpc.HybridSearchRequest, com.hybrid.query.grpc.HybridSearchResponse> getHybridSearchMethod;
    if ((getHybridSearchMethod = HybridQueryServiceGrpc.getHybridSearchMethod) == null) {
      synchronized (HybridQueryServiceGrpc.class) {
        if ((getHybridSearchMethod = HybridQueryServiceGrpc.getHybridSearchMethod) == null) {
          HybridQueryServiceGrpc.getHybridSearchMethod = getHybridSearchMethod =
              io.grpc.MethodDescriptor.<com.hybrid.query.grpc.HybridSearchRequest, com.hybrid.query.grpc.HybridSearchResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "HybridSearch"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.hybrid.query.grpc.HybridSearchRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.hybrid.query.grpc.HybridSearchResponse.getDefaultInstance()))
              .setSchemaDescriptor(new HybridQueryServiceMethodDescriptorSupplier("HybridSearch"))
              .build();
        }
      }
    }
    return getHybridSearchMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.hybrid.query.grpc.FacetsRequest,
      com.hybrid.query.grpc.FacetsResponse> getFacetsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Facets",
      requestType = com.hybrid.query.grpc.FacetsRequest.class,
      responseType = com.hybrid.query.grpc.FacetsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.hybrid.query.grpc.FacetsRequest,
      com.hybrid.query.grpc.FacetsResponse> getFacetsMethod() {
    io.grpc.MethodDescriptor<com.hybrid.query.grpc.FacetsRequest, com.hybrid.query.grpc.FacetsResponse> getFacetsMethod;
    if ((getFacetsMethod = HybridQueryServiceGrpc.getFacetsMethod) == null) {
      synchronized (HybridQueryServiceGrpc.class) {
        if ((getFacetsMethod = HybridQueryServiceGrpc.getFacetsMethod) == null) {
          HybridQueryServiceGrpc.getFacetsMethod = getFacetsMethod =
              io.grpc.MethodDescriptor.<com.hybrid.query.grpc.FacetsRequest, com.hybrid.query.grpc.FacetsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Facets"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.hybrid.query.grpc.FacetsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.hybrid.query.grpc.FacetsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new HybridQueryServiceMethodDescriptorSupplier("Facets"))
              .build();
        }
      }
    }
    return getFacetsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static HybridQueryServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HybridQueryServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HybridQueryServiceStub>() {
        @java.lang.Override
        public HybridQueryServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HybridQueryServiceStub(channel, callOptions);
        }
      };
    return HybridQueryServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static HybridQueryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HybridQueryServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HybridQueryServiceBlockingStub>() {
        @java.lang.Override
        public HybridQueryServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HybridQueryServiceBlockingStub(channel, callOptions);
        }
      };
    return HybridQueryServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static HybridQueryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HybridQueryServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HybridQueryServiceFutureStub>() {
        @java.lang.Override
        public HybridQueryServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HybridQueryServiceFutureStub(channel, callOptions);
        }
      };
    return HybridQueryServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void hybridSearch(com.hybrid.query.grpc.HybridSearchRequest request,
        io.grpc.stub.StreamObserver<com.hybrid.query.grpc.HybridSearchResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHybridSearchMethod(), responseObserver);
    }

    /**
     */
    default void facets(com.hybrid.query.grpc.FacetsRequest request,
        io.grpc.stub.StreamObserver<com.hybrid.query.grpc.FacetsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getFacetsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service HybridQueryService.
   */
  public static abstract class HybridQueryServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return HybridQueryServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service HybridQueryService.
   */
  public static final class HybridQueryServiceStub
      extends io.grpc.stub.AbstractAsyncStub<HybridQueryServiceStub> {
    private HybridQueryServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HybridQueryServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HybridQueryServiceStub(channel, callOptions);
    }

    /**
     */
    public void hybridSearch(com.hybrid.query.grpc.HybridSearchRequest request,
        io.grpc.stub.StreamObserver<com.hybrid.query.grpc.HybridSearchResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHybridSearchMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void facets(com.hybrid.query.grpc.FacetsRequest request,
        io.grpc.stub.StreamObserver<com.hybrid.query.grpc.FacetsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getFacetsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service HybridQueryService.
   */
  public static final class HybridQueryServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<HybridQueryServiceBlockingStub> {
    private HybridQueryServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HybridQueryServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HybridQueryServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.hybrid.query.grpc.HybridSearchResponse hybridSearch(com.hybrid.query.grpc.HybridSearchRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHybridSearchMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.hybrid.query.grpc.FacetsResponse facets(com.hybrid.query.grpc.FacetsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getFacetsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service HybridQueryService.
   */
  public static final class HybridQueryServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<HybridQueryServiceFutureStub> {
    private HybridQueryServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HybridQueryServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HybridQueryServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.hybrid.query.grpc.HybridSearchResponse> hybridSearch(
        com.hybrid.query.grpc.HybridSearchRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHybridSearchMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.hybrid.query.grpc.FacetsResponse> facets(
        com.hybrid.query.grpc.FacetsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getFacetsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_HYBRID_SEARCH = 0;
  private static final int METHODID_FACETS = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_HYBRID_SEARCH:
          serviceImpl.hybridSearch((com.hybrid.query.grpc.HybridSearchRequest) request,
              (io.grpc.stub.StreamObserver<com.hybrid.query.grpc.HybridSearchResponse>) responseObserver);
          break;
        case METHODID_FACETS:
          serviceImpl.facets((com.hybrid.query.grpc.FacetsRequest) request,
              (io.grpc.stub.StreamObserver<com.hybrid.query.grpc.FacetsResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getHybridSearchMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.hybrid.query.grpc.HybridSearchRequest,
              com.hybrid.query.grpc.HybridSearchResponse>(
                service, METHODID_HYBRID_SEARCH)))
        .addMethod(
          getFacetsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.hybrid.query.grpc.FacetsRequest,
              com.hybrid.query.grpc.FacetsResponse>(
                service, METHODID_FACETS)))
        .build();
  }

  private static abstract class HybridQueryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    HybridQueryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.hybrid.query.grpc.HybridQueryProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("HybridQueryService");
    }
  }

  private static final class HybridQueryServiceFileDescriptorSupplier
      extends HybridQueryServiceBaseDescriptorSupplier {
    HybridQueryServiceFileDescriptorSupplier() {}
  }

  private static final class HybridQueryServiceMethodDescriptorSupplier
      extends HybridQueryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    HybridQueryServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (HybridQueryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new HybridQueryServiceFileDescriptorSupplier())
              .addMethod(getHybridSearchMethod())
              .addMethod(getFacetsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
