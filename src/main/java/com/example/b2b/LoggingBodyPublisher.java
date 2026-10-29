package com.example.b2b;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;

/** Observes request bytes during transmission without consuming the downstream buffers. */
@Slf4j
@RequiredArgsConstructor
final class LoggingBodyPublisher implements HttpRequest.BodyPublisher {
    private final HttpRequest.BodyPublisher delegate;

    @Override
    public long contentLength() {
        return delegate.contentLength();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(new LoggingSubscriber(subscriber));
    }

    @RequiredArgsConstructor
    private static final class LoggingSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final Flow.Subscriber<? super ByteBuffer> downstream;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            downstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(ByteBuffer buffer) {
            var bytes = new byte[buffer.remaining()];
            buffer.asReadOnlyBuffer().get(bytes);
            body.writeBytes(bytes);
            downstream.onNext(buffer);
        }

        @Override
        public void onError(Throwable error) {
            downstream.onError(error);
        }

        @Override
        public void onComplete() {
            log.debug("[HTTP request body] {}", body.toString(StandardCharsets.UTF_8));
            downstream.onComplete();
        }
    }
}
