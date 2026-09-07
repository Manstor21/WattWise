package com.wattwise.android.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around a shared IO thread pool and the main looper so the rest of
 * the app doesn't need to carry a {@link ExecutorService} or {@link Handler}.
 * Kept intentionally simple — a production build would inject this.
 */
public final class AppExecutors {

    private static final int IO_POOL = Math.max(4, Runtime.getRuntime().availableProcessors());

    private static final ExecutorService IO = new ThreadPoolExecutor(
            IO_POOL / 2, IO_POOL,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(128));

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    /** Post work to the IO thread pool. */
    public static void io(Runnable runnable) {
        IO.execute(runnable);
    }

    /** Post work to the main (UI) thread. */
    public static void main(Runnable runnable) {
        MAIN.post(runnable);
    }

    public static ExecutorService ioExecutor() {
        return IO;
    }
}