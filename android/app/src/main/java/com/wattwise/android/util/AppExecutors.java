package com.wattwise.android.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Envoltorio fino alrededor de un pool de hilos IO compartido y del looper
 * principal, para que el resto de la app no tenga que manejar un {@link ExecutorService}
 * o un {@link Handler}. Se mantiene intencionadamente simple — un build de producción
 * inyectaría esto.
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

    /** Encola trabajo en el pool de hilos IO. */
    public static void io(Runnable runnable) {
        IO.execute(runnable);
    }

    /** Encola trabajo en el hilo principal (UI). */
    public static void main(Runnable runnable) {
        MAIN.post(runnable);
    }

    public static ExecutorService ioExecutor() {
        return IO;
    }
}