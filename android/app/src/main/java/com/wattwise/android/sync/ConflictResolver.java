package com.wattwise.android.sync;

/**
 * Last-write-wins conflict resolution between the local Room copy of an
 * appliance and the server state.
 *
 * <p>Pure Java on purpose (no Android imports) so it can be JVM-tested.
 *
 * <h2>Timestamp model</h2>
 * The backend exposes only {@code createdAt} (immutable) on {@code ApplianceDto};
 * there is no server {@code updatedAt} yet. Therefore:
 * <ul>
 *   <li>Every local mutation stamps {@code updatedAt = System.currentTimeMillis()}
 *       on the Room row and sets {@code isPendingSync}.</li>
 *   <li>When the server DTO is fetched, its {@code createdAt} instant is used as the
 *       remote write time baseline. A local row wins if its local write happened
 *       after the remote baseline; otherwise the server snapshot wins and the local
 *       pending flag is cleared.</li>
 * </ul>
 * Once the backend exposes an {@code updatedAt}, only the {@code resolve}
 * call sites change — the decision rule stays identical.
 */
public final class ConflictResolver {

    public enum Winner {
        /** Push the local pending state to the server. */
        LOCAL,
        /** Discard local pending state and adopt the server snapshot. */
        REMOTE
    }

    private ConflictResolver() {
    }

    /**
     * @param localWriteEpochMs  epoch millis of the last local mutation.
     * @param remoteWriteEpochMs server-side last write baseline (defaults to the
     *                           created-at instant) — {@code -1} means "unknown".
     * @return {@link Winner#LOCAL} when the local write is strictly newer.
     */
    public static Winner resolve(long localWriteEpochMs, long remoteWriteEpochMs) {
        if (remoteWriteEpochMs < 0) {
            return Winner.LOCAL;
        }
        return localWriteEpochMs > remoteWriteEpochMs ? Winner.LOCAL : Winner.REMOTE;
    }

    /**
     * Whether a Room row still needs a server action. Deletions also travel
     * through the sync pipeline (server DELETE) whenever the row already has a
     * server id; never-synced rows are simply dropped locally.
     */
    public static boolean needsServerAction(boolean isPendingSync, boolean isDeleted, boolean hasServerId) {
        if (!isPendingSync) {
            return false;
        }
        // A deletion of a never-synced row has nothing to tell the server.
        return !(isDeleted && !hasServerId);
    }
}