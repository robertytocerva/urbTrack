-- =====================================================================
-- V1__esquema_inicial.sql
-- Monitoreo colaborativo de brigadas de emergencia (PostgreSQL 13+)
-- Ruta: src/main/resources/db/migration/V1__esquema_inicial.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- USUARIOS (ciudadanos, unidades y administradores comparten tabla)
-- ---------------------------------------------------------------------
CREATE TABLE users (
                       id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       full_name     VARCHAR(150) NOT NULL,
                       phone         VARCHAR(30),
                       role          VARCHAR(20)  NOT NULL,
                       enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       CONSTRAINT uq_users_email UNIQUE (email),
                       CONSTRAINT ck_users_role  CHECK (role IN ('CITIZEN', 'UNIT', 'ADMIN'))
);

-- ---------------------------------------------------------------------
-- UNIDADES (perfil operativo de un usuario con rol UNIT)
-- ---------------------------------------------------------------------
CREATE TABLE units (
                       id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id     UUID        NOT NULL,
                       unit_type   VARCHAR(30) NOT NULL,
                       identifier  VARCHAR(50) NOT NULL,           -- nº de patrulla, placa, etc.
                       status      VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
                       created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                       CONSTRAINT fk_units_user       FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                       CONSTRAINT uq_units_user       UNIQUE (user_id),
                       CONSTRAINT uq_units_identifier UNIQUE (identifier),
                       CONSTRAINT ck_units_type       CHECK (unit_type IN ('POLICE', 'FIRE', 'AMBULANCE', 'CIVIL_PROTECTION')),
                       CONSTRAINT ck_units_status     CHECK (status IN ('AVAILABLE', 'BUSY', 'OFFLINE'))
);

CREATE INDEX idx_units_type_status ON units (unit_type, status);

-- ---------------------------------------------------------------------
-- ÚLTIMA UBICACIÓN CONOCIDA (1 fila por unidad)
-- ---------------------------------------------------------------------
CREATE TABLE unit_locations (
                                unit_id     UUID             PRIMARY KEY,
                                latitude    DOUBLE PRECISION NOT NULL,
                                longitude   DOUBLE PRECISION NOT NULL,
                                heading     DOUBLE PRECISION,                -- rumbo en grados (0-360)
                                speed_kmh   DOUBLE PRECISION,
                                recorded_at TIMESTAMPTZ      NOT NULL,       -- hora real de captura en el móvil
                                updated_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),  -- hora de recepción en servidor
                                CONSTRAINT fk_unit_locations_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
                                CONSTRAINT ck_unit_locations_lat  CHECK (latitude  BETWEEN -90  AND 90),
                                CONSTRAINT ck_unit_locations_lon  CHECK (longitude BETWEEN -180 AND 180),
                                CONSTRAINT ck_unit_locations_head  CHECK (heading IS NULL OR heading BETWEEN 0 AND 360)
);

-- Apoya el prefiltro por bounding box en la búsqueda de unidades cercanas
CREATE INDEX idx_unit_locations_lat_lon ON unit_locations (latitude, longitude);

-- ---------------------------------------------------------------------
-- ALERTAS
-- ---------------------------------------------------------------------
CREATE TABLE alerts (
                        id                 UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
                        citizen_id         UUID             NOT NULL,
                        type               VARCHAR(20)      NOT NULL,
                        required_unit_type VARCHAR(30)      NOT NULL,
                        description        VARCHAR(500),
                        latitude           DOUBLE PRECISION NOT NULL,
                        longitude          DOUBLE PRECISION NOT NULL,
                        status             VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
                        assigned_unit_id   UUID,
                        dispatch_round     SMALLINT         NOT NULL DEFAULT 0,
                        version            BIGINT           NOT NULL DEFAULT 0,   -- bloqueo optimista JPA
                        created_at         TIMESTAMPTZ      NOT NULL DEFAULT now(),
                        accepted_at        TIMESTAMPTZ,
                        arrived_at         TIMESTAMPTZ,
                        resolved_at        TIMESTAMPTZ,
                        cancelled_at       TIMESTAMPTZ,
                        CONSTRAINT fk_alerts_citizen       FOREIGN KEY (citizen_id)       REFERENCES users (id),
                        CONSTRAINT fk_alerts_assigned_unit FOREIGN KEY (assigned_unit_id) REFERENCES units (id),
                        CONSTRAINT ck_alerts_type          CHECK (type IN ('ROBBERY', 'FIRE', 'MEDICAL', 'ACCIDENT', 'OTHER')),
                        CONSTRAINT ck_alerts_required_unit CHECK (required_unit_type IN ('POLICE', 'FIRE', 'AMBULANCE', 'CIVIL_PROTECTION')),
                        CONSTRAINT ck_alerts_status        CHECK (status IN ('PENDING', 'EN_ROUTE', 'ON_SCENE', 'RESOLVED', 'CANCELLED', 'EXPIRED')),
                        CONSTRAINT ck_alerts_lat           CHECK (latitude  BETWEEN -90  AND 90),
                        CONSTRAINT ck_alerts_lon           CHECK (longitude BETWEEN -180 AND 180),
    -- Si la alerta está en curso o resuelta, debe tener unidad asignada
                        CONSTRAINT ck_alerts_assigned      CHECK (
                            status NOT IN ('EN_ROUTE', 'ON_SCENE', 'RESOLVED') OR assigned_unit_id IS NOT NULL
                            )
);

CREATE INDEX idx_alerts_status     ON alerts (status);
CREATE INDEX idx_alerts_citizen    ON alerts (citizen_id, created_at DESC);
CREATE INDEX idx_alerts_assigned   ON alerts (assigned_unit_id);

-- Un ciudadano solo puede tener UNA alerta activa a la vez
CREATE UNIQUE INDEX uq_alerts_active_citizen
    ON alerts (citizen_id)
    WHERE status IN ('PENDING', 'EN_ROUTE', 'ON_SCENE');

-- Una unidad solo puede atender UNA alerta activa a la vez (respaldo contra condiciones de carrera)
CREATE UNIQUE INDEX uq_alerts_active_unit
    ON alerts (assigned_unit_id)
    WHERE status IN ('EN_ROUTE', 'ON_SCENE');

-- ---------------------------------------------------------------------
-- OFERTAS DE DESPACHO (a qué unidades se les ofreció cada alerta)
-- ---------------------------------------------------------------------
CREATE TABLE alert_dispatches (
                                  id           UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
                                  alert_id     UUID             NOT NULL,
                                  unit_id      UUID             NOT NULL,
                                  round        SMALLINT         NOT NULL DEFAULT 1,
                                  status       VARCHAR(20)      NOT NULL DEFAULT 'OFFERED',
                                  distance_m   DOUBLE PRECISION NOT NULL,
                                  offered_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
                                  expires_at   TIMESTAMPTZ      NOT NULL,
                                  responded_at TIMESTAMPTZ,
                                  CONSTRAINT fk_dispatch_alert  FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_dispatch_unit   FOREIGN KEY (unit_id)  REFERENCES units (id)  ON DELETE CASCADE,
                                  CONSTRAINT uq_dispatch_alert_unit UNIQUE (alert_id, unit_id),
                                  CONSTRAINT ck_dispatch_status CHECK (status IN ('OFFERED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX idx_dispatch_unit_status ON alert_dispatches (unit_id, status);
CREATE INDEX idx_dispatch_alert       ON alert_dispatches (alert_id);
-- Apoya el job que expira ofertas vencidas
CREATE INDEX idx_dispatch_offered_expiry ON alert_dispatches (expires_at) WHERE status = 'OFFERED';

-- ---------------------------------------------------------------------
-- HISTORIAL DE UBICACIONES (recorrido; idempotente por event_id)
-- ---------------------------------------------------------------------
CREATE TABLE unit_location_history (
                                       id          BIGSERIAL        PRIMARY KEY,
                                       event_id    UUID             NOT NULL,        -- generado por el móvil
                                       unit_id     UUID             NOT NULL,
                                       alert_id    UUID,                             -- si iba atendiendo una alerta
                                       latitude    DOUBLE PRECISION NOT NULL,
                                       longitude   DOUBLE PRECISION NOT NULL,
                                       heading     DOUBLE PRECISION,
                                       speed_kmh   DOUBLE PRECISION,
                                       recorded_at TIMESTAMPTZ      NOT NULL,
                                       received_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
                                       CONSTRAINT fk_ulh_unit  FOREIGN KEY (unit_id)  REFERENCES units (id)  ON DELETE CASCADE,
                                       CONSTRAINT fk_ulh_alert FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE SET NULL,
                                       CONSTRAINT uq_ulh_event UNIQUE (event_id),
                                       CONSTRAINT ck_ulh_lat   CHECK (latitude  BETWEEN -90  AND 90),
                                       CONSTRAINT ck_ulh_lon   CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_ulh_unit_time  ON unit_location_history (unit_id, recorded_at DESC);
CREATE INDEX idx_ulh_alert_time ON unit_location_history (alert_id, recorded_at);

-- ---------------------------------------------------------------------
-- BITÁCORA DE EVENTOS DE LA ALERTA (equivale a "ReporteEvento")
-- ---------------------------------------------------------------------
CREATE TABLE alert_events (
                              id            BIGSERIAL   PRIMARY KEY,
                              alert_id      UUID        NOT NULL,
                              event_type    VARCHAR(40) NOT NULL,           -- ALERT_CREATED, DISPATCH_OFFERED, ALERT_ACCEPTED, ...
                              actor_user_id UUID,
                              payload       JSONB,
                              created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                              CONSTRAINT fk_alert_events_alert FOREIGN KEY (alert_id)      REFERENCES alerts (id) ON DELETE CASCADE,
                              CONSTRAINT fk_alert_events_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)  ON DELETE SET NULL
);

CREATE INDEX idx_alert_events_alert ON alert_events (alert_id, created_at);