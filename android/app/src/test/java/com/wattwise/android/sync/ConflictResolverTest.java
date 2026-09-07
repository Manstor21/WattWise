package com.wattwise.android.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the last-write-wins decision logic in {@link ConflictResolver}. The
 * rule is intentionally simple: larger epoch millis wins; a remote value of -1
 * always yields LOCAL.
 */
public class ConflictResolverTest {

    @Test
    public void localNewerThanRemote_returnsLocal() {
        assertEquals(ConflictResolver.Winner.LOCAL,
                ConflictResolver.resolve(1_700_000_000_000L, 1_600_000_000_000L));
    }

    @Test
    public void remoteNewerThanLocal_returnsRemote() {
        assertEquals(ConflictResolver.Winner.REMOTE,
                ConflictResolver.resolve(1_500_000_000_000L, 1_600_000_000_000L));
    }

    @Test
    public void sameTimestamp_returnsRemote() {
        assertEquals(ConflictResolver.Winner.REMOTE,
                ConflictResolver.resolve(1_600_000_000_000L, 1_600_000_000_000L));
    }

    @Test
    public void remoteUnknown_returnsLocal() {
        assertEquals(ConflictResolver.Winner.LOCAL,
                ConflictResolver.resolve(1_600_000_000_000L, -1));
    }

    @Test
    public void bothUnknown_returnsLocal() {
        assertEquals(ConflictResolver.Winner.LOCAL,
                ConflictResolver.resolve(-1, -1));
    }

    // ---- needsServerAction ----

    @Test
    public void needsServerAction_pending_with_serverId() {
        assertTrue(ConflictResolver.needsServerAction(true, false, true));
    }

    @Test
    public void needsServerAction_pending_new_local() {
        // Pending create (no server id yet) needs a POST.
        assertTrue(ConflictResolver.needsServerAction(true, false, false));
    }

    @Test
    public void needsServerAction_pending_deletion_existing_serverId() {
        // Pending deletion of a server-known row needs a DELETE.
        assertTrue(ConflictResolver.needsServerAction(true, true, true));
    }

    @Test
    public void doesNot_needServerAction_pending_deletion_new_local() {
        // Deleting a row that was never sent to the server just drops the local row.
        assertFalse(ConflictResolver.needsServerAction(true, true, false));
    }

    @Test
    public void doesNot_needServerAction_not_pending() {
        assertFalse(ConflictResolver.needsServerAction(false, false, true));
    }

    // Thin wrappers for the verbose calls.
    private static void assertTrue(boolean v) { org.junit.Assert.assertTrue(v); }
    private static void assertFalse(boolean v) { org.junit.Assert.assertFalse(v); }
}