-- ============================================================
-- BD2 · S3 · Paso 2: por qué el registro de errores necesita su propia transacción.
-- Como JDREFRIGERACION:  .\bd2\herramientas\sql.ps1 bd2\S03\S03_02_hallazgo_rollback.sql
--
-- Se compara el patrón "INSERT en LOG_ERRORES y luego RAISE" (como en la guía) con SP_REGISTRAR_ERROR
-- (transacción autónoma). Cuando la excepción sale del bloque hacia el cliente, Oracle deshace TODO lo que
-- hizo esa llamada (atomicidad de la sentencia), incluido el INSERT del registro.
-- ============================================================
set echo off feedback off linesize 200 pagesize 100 serveroutput on

VARIABLE v_antes NUMBER
EXEC SELECT COUNT(*) INTO :v_antes FROM log_errores;

PROMPT === A) INSERT directo en LOG_ERRORES y luego RAISE (se deja que el error llegue al cliente) ===
BEGIN
    INSERT INTO log_errores (objeto, codigo_error, mensaje_error)
    VALUES ('PRUEBA_SIN_AUTONOMA', -20999, 'Este registro debería quedar');
    RAISE_APPLICATION_ERROR(-20999, 'Error de prueba A');
END;
/
SELECT COUNT(*) AS quedaron_sin_autonoma FROM log_errores WHERE objeto = 'PRUEBA_SIN_AUTONOMA';

PROMPT === B) Lo mismo con SP_REGISTRAR_ERROR (PRAGMA AUTONOMOUS_TRANSACTION) ===
BEGIN
    sp_registrar_error('PRUEBA_CON_AUTONOMA', -20999, 'Este registro debería quedar');
    RAISE_APPLICATION_ERROR(-20999, 'Error de prueba B');
END;
/
SELECT COUNT(*) AS quedaron_con_autonoma FROM log_errores WHERE objeto = 'PRUEBA_CON_AUTONOMA';

PROMPT === C) El caso del backend: quien llama hace ROLLBACK al recibir el error ===
BEGIN
    BEGIN
        INSERT INTO log_errores (objeto, codigo_error, mensaje_error)
        VALUES ('PRUEBA_ROLLBACK_SIN', -20999, 'Registrado sin autónoma');
        sp_registrar_error('PRUEBA_ROLLBACK_CON', -20999, 'Registrado con autónoma');
        RAISE_APPLICATION_ERROR(-20999, 'Error de prueba C');
    EXCEPTION WHEN OTHERS THEN
        ROLLBACK;   -- lo que hace Spring con una transacción que lanzó una excepción
    END;
END;
/
SELECT objeto, COUNT(*) AS filas FROM log_errores
WHERE objeto IN ('PRUEBA_ROLLBACK_SIN', 'PRUEBA_ROLLBACK_CON') GROUP BY objeto ORDER BY objeto;

-- Limpieza de las filas de prueba (las de la autónoma sí quedaron)
DELETE FROM log_errores WHERE objeto LIKE 'PRUEBA\_%' ESCAPE '\';
COMMIT;
exit
