package com.wattwise.android.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifica la lógica de decisión last-write-wins en {@link ConflictResolver}. La
 * regla es intencionadamente simple: gana el epoch millis mayor; un valor remoto de
 * -1 siempre produce LOCAL.
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
        // Una creación pendiente (sin id de servidor) necesita un POST.
        assertTrue(ConflictResolver.needsServerAction(true, false, false));
    }

    @Test
    public void needsServerAction_pending_deletion_existing_serverId() {
        // Un borrado pendiente de una fila conocida por el servidor necesita un DELETE.
        assertTrue(ConflictResolver.needsServerAction(true, true, true));
    }

    @Test
    public void doesNot_needServerAction_pending_deletion_new_local() {
        // Borrar una fila nunca enviada al servidor solo descarta la fila local.
        assertFalse(ConflictResolver.needsServerAction(true, true, false));
    }

    @Test
    public void doesNot_needServerAction_not_pending() {
        assertFalse(ConflictResolver.needsServerAction(false, false, true));
    }

    // Wrappers finos para las llamadas verbosas.
    private static void assertTrue(boolean v) { org.junit.Assert.assertTrue(v); }
    private static void assertFalse(boolean v) { org.junit.Assert.assertFalse(v); }
}