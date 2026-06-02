package telemetry

import (
	"context"
	"log/slog"
	"os"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	sdkresource "go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
)

// InitTracing initialises the OpenTelemetry SDK.
//
// If the OTEL_EXPORTER_OTLP_ENDPOINT environment variable is not set the
// global tracer provider remains the default no-op provider — useful for
// local development and unit tests where Jaeger is not running.
//
// The returned shutdown function must be called before the process exits to
// flush any buffered spans to the OTLP collector.
func InitTracing(ctx context.Context) (shutdown func(), err error) {
	endpoint := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
	if endpoint == "" {
		slog.Info("OTEL_EXPORTER_OTLP_ENDPOINT not set — tracing disabled")
		return func() {}, nil
	}

	exp, err := otlptracehttp.New(ctx,
		otlptracehttp.WithEndpoint(endpoint),
		otlptracehttp.WithInsecure(), // Jaeger all-in-one listens on plain HTTP
	)
	if err != nil {
		return func() {}, err
	}

	res, resErr := sdkresource.New(ctx,
		sdkresource.WithAttributes(attribute.String("service.name", "go-odds")),
	)
	if resErr != nil {
		// Non-fatal: fall back to an empty resource (service name just won't appear)
		slog.Warn("OTel resource creation failed, using empty resource", "err", resErr)
		res = sdkresource.Empty()
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exp),
		sdktrace.WithResource(res),
	)

	// Install as the global provider so otelgrpc interceptors pick it up.
	otel.SetTracerProvider(tp)

	// W3C TraceContext + Baggage propagation — reads/writes "traceparent" and
	// "tracestate" headers in gRPC metadata, enabling correlation with Java spans.
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	slog.Info("OTel tracing initialised", "endpoint", endpoint)

	return func() {
		if shutdownErr := tp.Shutdown(context.Background()); shutdownErr != nil {
			slog.Warn("OTel trace provider shutdown error", "err", shutdownErr)
		}
	}, nil
}
