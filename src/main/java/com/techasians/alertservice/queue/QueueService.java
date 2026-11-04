package com.techasians.alertservice.queue;

import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class QueueService {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

    public void push(Runnable task) {
        executor.submit(task);
    }

    public void pushDelayed(Runnable task, Duration delay) {
        executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
