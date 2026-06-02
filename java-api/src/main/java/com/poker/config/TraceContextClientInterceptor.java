package com.poker.config;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * gRPC {@link ClientInterceptor} that injects the active W3C trace context
 * ({@code traceparent} + {@code tracestate}) into every outbound gRPC call's
 * metadata.
 *
 * <p>When the Java service handles an HTTP request that Micrometer has already
 * wrapped in an OTel span, this interceptor propagates that span as the parent
 * of the Go-side gRPC span, enabling end-to-end trace correlation in Jaeger.
 *
 * <p>If no active trace context exists (e.g., in unit tests) the propagation
 * call is a no-op — no headers are added.
 */
public class TraceContextClientInterceptor implements ClientInterceptor {

    private static final TextMapSetter<Metadata> METADATA_SETTER =
        (carrier, key, value) ->
            carrier.put(
                Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER),
                value);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Inject "traceparent" (and optionally "tracestate") into gRPC
                // metadata so the Go service can continue the same trace.
                GlobalOpenTelemetry
                    .getPropagators()
                    .getTextMapPropagator()
                    .inject(Context.current(), headers, METADATA_SETTER);

                super.start(responseListener, headers);
            }
        };
    }
}
