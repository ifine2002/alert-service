package com.techasians.alertservice.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class RetryAbleTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RetryAbleTask.class);

    private final Runnable delegate;
    private final int maxRetries;
    private final Duration firstBackoff;
    private final QueueService queue;
    private final Predicate<Exception> retryWhen;

    // Callbacks
    private final Runnable onSuccess;
    private final Consumer<Exception> onFailure;
    private final BiConsumer<Integer, Exception> onRetry;

    
    private int currentRetry = 0;

    private RetryAbleTask(Builder builder) {
        this.delegate = builder.delegate;
        this.maxRetries = builder.maxRetries;
        this.firstBackoff = builder.firstBackoff;
        this.queue = builder.queue;
        this.retryWhen = builder.retryWhen;
        this.onSuccess = builder.onSuccess;
        this.onFailure = builder.onFailure;
        this.onRetry = builder.onRetry;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void run() {
        try {
            delegate.run();
            // Nếu không có exception nghĩa là thành công
            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (Exception ex) {
            log.warn("Task failed (retry {}/{}): {}", currentRetry, maxRetries, ex.getMessage());

            if (currentRetry < maxRetries && (retryWhen == null || retryWhen.test(ex))) {
                currentRetry++;

                if (onRetry != null) {
                    onRetry.accept(currentRetry, ex);
                }

                // Exponential backoff
                long delayMs = firstBackoff.toMillis() * (long) Math.pow(2, currentRetry - 1);
                log.info("Rescheduling task in {} ms", delayMs);
                queue.pushDelayed(this, Duration.ofMillis(delayMs));
            } else {
                log.error("Task failed completely sau {} lần retry.", currentRetry, ex);
                if (onFailure != null) {
                    onFailure.accept(ex);
                }
            }
        }
    }

    public static class Builder {
        private Runnable delegate;
        private int maxRetries = 3;
        private Duration firstBackoff = Duration.ofSeconds(5);
        private QueueService queue;
        private Predicate<Exception> retryWhen = ex -> true;

        private Runnable onSuccess;
        private Consumer<Exception> onFailure;
        private BiConsumer<Integer, Exception> onRetry;

        public Builder delegate(Runnable delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder firstBackoff(Duration firstBackoff) {
            this.firstBackoff = firstBackoff;
            return this;
        }

        public Builder queue(QueueService queue) {
            this.queue = queue;
            return this;
        }

        public Builder retryWhen(Predicate<Exception> retryWhen) {
            this.retryWhen = retryWhen;
            return this;
        }

        public Builder onSuccess(Runnable onSuccess) {
            this.onSuccess = onSuccess;
            return this;
        }

        public Builder onFailure(Consumer<Exception> onFailure) {
            this.onFailure = onFailure;
            return this;
        }

        public Builder onRetry(BiConsumer<Integer, Exception> onRetry) {
            this.onRetry = onRetry;
            return this;
        }

        public RetryAbleTask build() {
            if (delegate == null) throw new IllegalArgumentException("delegate cannot be null");
            if (queue == null) throw new IllegalArgumentException("queue cannot be null");
            return new RetryAbleTask(this);
        }
    }
}
