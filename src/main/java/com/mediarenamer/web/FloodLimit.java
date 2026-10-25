package com.mediarenamer.web;

import java.util.ArrayDeque;
import java.util.Deque;

/** Request limiter: at most N requests per time window (AniDB, TMDb). */
public class FloodLimit {

    private final int permits;
    private final long windowMillis;
    private final Deque<Long> stamps = new ArrayDeque<>();

    public FloodLimit(int permits, long windowMillis) {
        this.permits = permits;
        this.windowMillis = windowMillis;
    }

    public synchronized void acquire() throws InterruptedException {
        while (true) {
            long now = System.currentTimeMillis();
            while (!stamps.isEmpty() && now - stamps.peekFirst() >= windowMillis) {
                stamps.pollFirst();
            }
            if (stamps.size() < permits) {
                stamps.addLast(now);
                return;
            }
            wait(Math.max(1, windowMillis - (now - stamps.peekFirst())));
        }
    }
}
