/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3;

//? if java: < 21 {
/*import java.io.Closeable;
import java.net.http.HttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

record LegacyExecutorCloser(ExecutorService executor) implements Closeable, Executor {
    static LegacyExecutorCloser cachedThreadPool() {
        return new LegacyExecutorCloser(Executors.newCachedThreadPool());
    }

    static void close(ExecutorService executor) {
        boolean terminated = executor.isTerminated();
        if (!terminated) {
            executor.shutdown();
            boolean interrupted = false;
            while (!terminated) {
                try {
                    terminated = executor.awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    if (!interrupted) {
                        executor.shutdownNow();
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void close(HttpClient client) {
        client.executor().ifPresent(e -> {
            if (e instanceof ExecutorService) close((ExecutorService) e);
        });
    }

    @Override
    public void close() {
        close(executor);
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }
}
*///?}
