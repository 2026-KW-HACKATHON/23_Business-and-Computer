package com.gakkum.backend.global.logging;

import java.util.Locale;

import org.slf4j.MDC;

public final class RequestLogContext {

    public static final String TID_KEY = "tid";
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private RequestLogContext() {
    }

    public static int depth() {
        Integer depth = DEPTH.get();
        return depth == null ? 0 : depth;
    }

    public static void setDepth(int depth) {
        if (depth == 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static String elapsed(long startedAt) {
        return String.format(Locale.ROOT, "%.3fms", (System.nanoTime() - startedAt) / 1_000_000.0);
    }

    public static void clear() {
        DEPTH.remove();
        MDC.remove(TID_KEY);
    }
}
