package com.hybrid.vector.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.64.0)",
    comments = "Source: vector_search.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class VectorSearchServiceGrpc {

  private VectorSearchServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "VectorSearchService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.hybrid.vector.grpc.VectorSearchRequest,
      com.hybrid.vector.grpc.VectorSearchResponse> getSearchMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Search",
      requestType = com.hybrid.vector.grpc.VectorSearchRequest.class,
      responseType = com.hybrid.vector.grpc.VectorSearchResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.hybrid.vector.grpc.VectorSearchRequest,
      com.hybrid.vector.grpc.VectorSearchResponse> getSearchMethod() {
    io.grpc.MethodDescriptor<com.hybrid.vector.grpc.VectorSearchRequest, com.hybrid.vector.grpc.VectorSearchResponse> getSearchMethod;
    if ((getSearchMethod = VectorSearchServiceGrpc.getSearchMethod) == null) {
      synchronized (VectorSearchServiceGrpc.class) {
        if ((getSearchMethod = VectorSearchServiceGrpc.getSearchMethod) == null) {
          VectorSearchServiceGrpc.getSearchMethod = getSearchMethod =
              io.grpc.MethodDescriptor.<com.hybrid.vector.grpc.VectorSearchRequest, com.hybrid.vector.grpc.VectorSearchResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Search"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.hybrid.vector.grpc.VectorSearchRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.hybrid.vector.grpc.VectorSearchResponse.getDefaultInstance()))
              .setSchemaDescriptor(new VectorSearchServiceMethodDescriptorSupplier("Search"))
              .build();
        }
      }
    }
    return getSearchMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static VectorSearchServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VectorSearchServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VectorSearchServiceStub>() {
        @java.lang.Override
        public VectorSearchServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VectorSearchServiceStub(channel, callOptions);
        }
      };
    return VectorSearchServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static VectorSearchServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VectorSearchServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VectorSearchServiceBlockingStub>() {
        @java.lang.Override
        public VectorSearchServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VectorSearchServiceBlockingStub(channel, callOptions);
        }
      };
    return VectorSearchServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static VectorSearchServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VectorSearchServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VectorSearchServiceFutureStub>() {
        @java.lang.Override
        public VectorSearchServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VectorSearchServiceFutureStub(channel, callOptions);
        }
      };
    return VectorSearchServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void search(com.hybrid.vector.grpc.VectorSearchRequest request,
        io.grpc.stub.StreamObserver<com.hybrid.vector.grpc.VectorSearchResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSearchMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service VectorSearchService.
   */
  public static abstract class VectorSearchServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return VectorSearchServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service VectorSearchService.
   */
  public static final class VectorSearchServiceStub
      extends io.grpc.stub.AbstractAsyncStub<VectorSearchServiceStub> {
    private VectorSearchServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VectorSearchServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VectorSearchServiceStub(channel, callOptions);
    }

    /**
     */
    public void search(com.hybrid.vector.grpc.VectorSearchRequest request,
        io.grpc.stub.StreamObserver<com.hybrid.vector.grpc.VectorSearchResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSearchMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service VectorSearchService.
   */
  public static final class VectorSearchServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<VectorSearchServiceBlockingStub> {
    private VectorSearchServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VectorSearchServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VectorSearchServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.hybrid.vector.grpc.VectorSearchResponse search(com.hybrid.vector.grpc.VectorSearchRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSearchMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service VectorSearchService.
   */
  public static final class VectorSearchServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<VectorSearchServiceFutureStub> {
    private VectorSearchServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VectorSearchServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VectorSearchServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.hybrid.vector.grpc.VectorSearchResponse> search(
        com.hybrid.vector.grpc.VectorSearchRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSearchMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SEARCH = 0;

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
        case METHODID_SEARCH:
          serviceImpl.search((com.hybrid.vector.grpc.VectorSearchRequest) request,
              (io.grpc.stub.StreamObserver<com.hybrid.vector.grpc.VectorSearchResponse>) responseObserver);
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
          getSearchMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.hybrid.vector.grpc.VectorSearchRequest,
              com.hybrid.vector.grpc.VectorSearchResponse>(
                service, METHODID_SEARCH)))
        .build();
  }

  private static abstract class VectorSearchServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    VectorSearchServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.hybrid.vector.grpc.VectorSearchProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("VectorSearchService");
    }
  }

  private static final class VectorSearchServiceFileDescriptorSupplier
      extends VectorSearchServiceBaseDescriptorSupplier {
    VectorSearchServiceFileDescriptorSupplier() {}
  }

  private static final class VectorSearchServiceMethodDescriptorSupplier
      extends VectorSearchServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    VectorSearchServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (VectorSearchServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new VectorSearchServiceFileDescriptorSupplier())
              .addMethod(getSearchMethod())
              .build();
        }
      }
    }
    return result;
  }
}
