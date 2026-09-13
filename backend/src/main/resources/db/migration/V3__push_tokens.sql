-- ---------------------------------------------------------------------------
-- WattWise — V3: tabla de tokens push FCM por usuario
-- La app Android entrega su token de dispositivo registrado vía POST /api/push-tokens
-- (JWT). El backend lo guarda aquí para poder enviar notificaciones push iniciadas
-- por el servidor (Firebase Admin SDK) — ver ADR-006.
-- ---------------------------------------------------------------------------

IF OBJECT_ID('push_tokens', 'U') IS NOT NULL DROP TABLE push_tokens;

-- ---------------------------------------------------------------
-- TOKENS PUSH (registros de dispositivo FCM; un usuario puede tener varios)
-- ---------------------------------------------------------------
CREATE TABLE push_tokens (
    id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      NVARCHAR(512) NOT NULL,
    platform   NVARCHAR(20)  NOT NULL DEFAULT 'ANDROID',  -- ANDROID | IOS | WEB
    created_at DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_push_tokens_token UNIQUE (token),
    -- CASCADE: al borrar un usuario se eliminan sus tokens de dispositivo.
    CONSTRAINT fk_push_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Índice para listar/envíar a los tokens de un usuario concreto.
CREATE INDEX ix_push_tokens_user ON push_tokens(user_id);