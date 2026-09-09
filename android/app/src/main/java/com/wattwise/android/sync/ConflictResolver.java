package com.wattwise.android.sync;

/**
 * Resolución de conflictos last-write-wins entre la copia local en Room de un
 * aparato y el estado del servidor.
 *
 * <p>Java puro a propósito (sin imports de Android) para poder probarse en la JVM.
 *
 * <h2>Modelo de timestamps</h2>
 * El backend expone solo {@code createdAt} (inmutable) en {@code ApplianceDto};
 * todavía no hay {@code updatedAt} en el servidor. Por tanto:
 * <ul>
 *   <li>Cada mutación local sella {@code updatedAt = System.currentTimeMillis()}
 *       en la fila de Room y activa {@code isPendingSync}.</li>
 *   <li>Al recuperar el DTO del servidor, su instante {@code createdAt} se usa como
 *       base temporal de la escritura remota. Una fila local gana si su escritura
 *       local ocurrió después de la base remota; si no, gana la instantánea del
 *       servidor y se limpia el flag local de pendiente.</li>
 * </ul>
 * Cuando el backend exponga un {@code updatedAt}, solo cambian los puntos de
 * llamada a {@code resolve} — la regla de decisión permanece idéntica.
 */
public final class ConflictResolver {

    public enum Winner {
        /** Envía el estado local pendiente al servidor. */
        LOCAL,
        /** Descarta el estado local pendiente y adopta la instantánea del servidor. */
        REMOTE
    }

    private ConflictResolver() {
    }

    /**
     * @param localWriteEpochMs  epoch millis de la última mutación local.
     * @param remoteWriteEpochMs baseline de la última escritura en el servidor (por defecto el
     *                           instante created-at) — {@code -1} significa "desconocido".
     * @return {@link Winner#LOCAL} cuando la escritura local es estrictamente más reciente.
     */
    public static Winner resolve(long localWriteEpochMs, long remoteWriteEpochMs) {
        if (remoteWriteEpochMs < 0) {
            return Winner.LOCAL;
        }
        return localWriteEpochMs > remoteWriteEpochMs ? Winner.LOCAL : Winner.REMOTE;
    }

    /**
     * Indica si una fila de Room aún necesita una acción en el servidor. Los borrados también viajan
     * por el pipeline de sync (DELETE en el servidor) siempre que la fila ya tenga un
     * id de servidor; las filas nunca sincronizadas simplemente se descartan localmente.
     */
    public static boolean needsServerAction(boolean isPendingSync, boolean isDeleted, boolean hasServerId) {
        if (!isPendingSync) {
            return false;
        }
        // Un borrado de una fila nunca sincronizada no tiene nada que comunicar al servidor.
        return !(isDeleted && !hasServerId);
    }
}