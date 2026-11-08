-- ============================================================
-- Migración: entrega del comprobante por correo con seguimiento (08/11/2026)
--
-- ENVIOS_CORREO: cada solicitud de envío de una factura o boleta al cliente (automática al aceptarla SUNAT, o
--   reenvío manual), con su estado (PENDIENTE, EN_PROCESO, ENVIADO, FALLIDO, INCIERTO), destinatario, intentos,
--   último error, Message-ID y hora del próximo intento automático.
-- INTENTOS_CORREO: cada intento de esas solicitudes (solo se agrega: historial auditable), con los adjuntos enviados
--   (nombre, tamaño y huella SHA-256).
-- UX_ENVIO_CORREO_AUTOMATICO: un solo envío automático por comprobante (aunque SUNAT avise dos veces).
--
-- Solo crea lo que falta: no borra ni cambia datos. Se puede ejecutar varias veces.
-- Uso:
--   docker cp migracion_envio_correo.sql SGE-JD:/tmp/m.sql
--   "alter session set current_schema=JDREFRIGERACION;`n@/tmp/m.sql" | docker exec -i -e ORACLE_PDB_SID=FREEPDB1 SGE-JD sqlplus -s / as sysdba
-- ============================================================
SET SERVEROUTPUT ON
DECLARE
    v_esquema VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA');
    v_existe  NUMBER;

    PROCEDURE crear(p_tipo VARCHAR2, p_nombre VARCHAR2, p_sql VARCHAR2) IS
    BEGIN
        IF p_tipo = 'TABLA' THEN
            SELECT COUNT(*) INTO v_existe FROM all_tables WHERE owner = v_esquema AND table_name = p_nombre;
        ELSE
            SELECT COUNT(*) INTO v_existe FROM all_indexes WHERE owner = v_esquema AND index_name = p_nombre;
        END IF;
        IF v_existe = 0 THEN
            EXECUTE IMMEDIATE p_sql;
            DBMS_OUTPUT.PUT_LINE(p_nombre || ': creado');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_nombre || ': ya existía');
        END IF;
    END;
BEGIN
    crear('TABLA', 'ENVIOS_CORREO', 'CREATE TABLE ' || v_esquema || '.ENVIOS_CORREO (
        ID NUMBER(19,0) GENERATED ALWAYS AS IDENTITY NOT NULL,
        COMPROBANTE_ID NUMBER(19,0) NOT NULL,
        DESTINATARIO VARCHAR2(200 CHAR) NOT NULL,
        TIPO VARCHAR2(12 CHAR) NOT NULL,
        ESTADO VARCHAR2(12 CHAR) NOT NULL,
        SOLICITADO_POR VARCHAR2(100 CHAR),
        FECHA_CREACION TIMESTAMP(6) NOT NULL,
        FECHA_ULTIMO_INTENTO TIMESTAMP(6),
        FECHA_ACEPTACION_SMTP TIMESTAMP(6),
        INTENTOS NUMBER(10,0) DEFAULT 0 NOT NULL,
        ULTIMO_ERROR VARCHAR2(1000 CHAR),
        MESSAGE_ID VARCHAR2(250 CHAR),
        PROXIMO_INTENTO TIMESTAMP(6),
        EN_PROCESO_DESDE TIMESTAMP(6),
        CONSTRAINT PK_ENVIOS_CORREO PRIMARY KEY (ID),
        CONSTRAINT FK_ENVIO_CORREO_COMPROBANTE FOREIGN KEY (COMPROBANTE_ID) REFERENCES ' || v_esquema || '.COMPROBANTES (ID),
        CONSTRAINT CK_ENVIO_CORREO_TIPO CHECK (TIPO IN (''AUTOMATICO'', ''MANUAL'')),
        CONSTRAINT CK_ENVIO_CORREO_ESTADO CHECK (ESTADO IN (''PENDIENTE'', ''EN_PROCESO'', ''ENVIADO'', ''FALLIDO'', ''INCIERTO'')))');

    crear('TABLA', 'INTENTOS_CORREO', 'CREATE TABLE ' || v_esquema || '.INTENTOS_CORREO (
        ID NUMBER(19,0) GENERATED ALWAYS AS IDENTITY NOT NULL,
        ENVIO_CORREO_ID NUMBER(19,0) NOT NULL,
        INTENTO NUMBER(10,0) NOT NULL,
        INICIO TIMESTAMP(6) NOT NULL,
        FIN TIMESTAMP(6),
        RESULTADO VARCHAR2(12 CHAR) NOT NULL,
        MENSAJE VARCHAR2(1000 CHAR),
        MESSAGE_ID VARCHAR2(250 CHAR),
        ADJUNTOS VARCHAR2(600 CHAR),
        CONSTRAINT PK_INTENTOS_CORREO PRIMARY KEY (ID),
        CONSTRAINT FK_INTENTO_CORREO_ENVIO FOREIGN KEY (ENVIO_CORREO_ID) REFERENCES ' || v_esquema || '.ENVIOS_CORREO (ID),
        CONSTRAINT CK_INTENTO_CORREO_RESULTADO CHECK (RESULTADO IN (''ENVIADO'', ''FALLIDO'', ''INCIERTO'')))');

    -- Un solo envío automático por comprobante (los reenvíos manuales pueden ser varios)
    crear('INDICE', 'UX_ENVIO_CORREO_AUTOMATICO', 'CREATE UNIQUE INDEX ' || v_esquema || '.UX_ENVIO_CORREO_AUTOMATICO ON '
        || v_esquema || '.ENVIOS_CORREO (CASE WHEN TIPO = ''AUTOMATICO'' THEN COMPROBANTE_ID END)');
    crear('INDICE', 'IX_ENVIO_CORREO_COMPROBANTE', 'CREATE INDEX ' || v_esquema || '.IX_ENVIO_CORREO_COMPROBANTE ON '
        || v_esquema || '.ENVIOS_CORREO (COMPROBANTE_ID)');
    crear('INDICE', 'IX_ENVIO_CORREO_ESTADO', 'CREATE INDEX ' || v_esquema || '.IX_ENVIO_CORREO_ESTADO ON '
        || v_esquema || '.ENVIOS_CORREO (ESTADO, PROXIMO_INTENTO)');
    crear('INDICE', 'IX_INTENTO_CORREO_ENVIO', 'CREATE INDEX ' || v_esquema || '.IX_INTENTO_CORREO_ENVIO ON '
        || v_esquema || '.INTENTOS_CORREO (ENVIO_CORREO_ID)');
END;
/
