package com.aigooz.encyclopediaapp;

final class UpdateDownload {
    static final long STALL_TIMEOUT_MS = 30_000;
    AppRelease release;
    long id = -1;
    int source;
    boolean ready;
    boolean waitingForPermission;
    long lastBytes;
    long lastProgressAt;

    void track(long bytes, long now) {
        if (bytes > lastBytes || lastProgressAt == 0 || now < lastProgressAt) {
            lastBytes = bytes;
            lastProgressAt = now;
        }
    }

    boolean stalled(long now) { return lastProgressAt > 0 && now - lastProgressAt >= STALL_TIMEOUT_MS; }
    boolean hasNextSource() { return source + 1 < AppRelease.SOURCE_NAMES.size(); }
}
