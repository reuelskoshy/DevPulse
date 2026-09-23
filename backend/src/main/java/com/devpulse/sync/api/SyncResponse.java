package com.devpulse.sync.api;

import java.time.Instant;

public record SyncResponse(int reposSynced, int commitsSynced, Instant syncedAt) {
}
