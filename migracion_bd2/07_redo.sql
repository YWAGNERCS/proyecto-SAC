-- ============================================================
-- Migración BD2 · 7/7 · Redo logs de 2 x 10 MB a 3 x 200 MB (BD2 S7 y S9)
-- Con 10 MB, la carga de S4 dio "Checkpoint not complete": las sesiones esperaban a que se escribiera el disco.
-- Los redo son de la instancia (CDB), no de FREEPDB1. Se agregan los grupos nuevos dentro del volumen, se cambia
-- de log hasta que los viejos queden INACTIVE y recién entonces se retiran. Sin reiniciar la base.
-- Lo ejecuta migracion_bd2\migrar.ps1 como SYSDBA. Se puede repetir (si ya hay 3 de 200 MB, no hace nada).
-- ============================================================
whenever sqlerror exit failure
set serveroutput on feedback off
DECLARE
    v_dir     VARCHAR2(300);
    v_grandes NUMBER;
    v_grupo   NUMBER;
    v_estado  VARCHAR2(20);
    PROCEDURE ejecutar(p_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE('  ok: ' || SUBSTR(p_sql, 1, 110));
    END;
BEGIN
    SELECT COUNT(*) INTO v_grandes FROM v$log WHERE bytes >= 200 * 1024 * 1024;
    IF v_grandes >= 3 THEN
        DBMS_OUTPUT.PUT_LINE('  Ya hay ' || v_grandes || ' grupos de 200 MB o más: nada que hacer');
    ELSE
        SELECT SUBSTR(member, 1, INSTR(member, '/', -1)) INTO v_dir FROM v$logfile WHERE ROWNUM = 1;
        SELECT NVL(MAX(group#), 0) INTO v_grupo FROM v$log;
        FOR i IN 1 .. 3 - v_grandes LOOP
            v_grupo := v_grupo + 1;
            ejecutar('ALTER DATABASE ADD LOGFILE GROUP ' || v_grupo || ' (''' || v_dir || 'redo' || LPAD(v_grupo, 2, '0') || '.log'') SIZE 200M');
        END LOOP;
        -- Los grupos chicos se retiran cuando dejan de ser CURRENT y su checkpoint terminó (INACTIVE)
        FOR g IN (SELECT group# FROM v$log WHERE bytes < 200 * 1024 * 1024 ORDER BY group#) LOOP
            FOR intento IN 1 .. 10 LOOP
                SELECT status INTO v_estado FROM v$log WHERE group# = g.group#;
                EXIT WHEN v_estado = 'INACTIVE';
                IF v_estado = 'CURRENT' THEN
                    EXECUTE IMMEDIATE 'ALTER SYSTEM SWITCH LOGFILE';
                END IF;
                EXECUTE IMMEDIATE 'ALTER SYSTEM CHECKPOINT';
                DBMS_SESSION.SLEEP(1);
            END LOOP;
            ejecutar('ALTER DATABASE DROP LOGFILE GROUP ' || g.group#);
        END LOOP;
        DBMS_OUTPUT.PUT_LINE('  (los archivos de los grupos retirados quedan en disco; Oracle no los borra)');
    END IF;
END;
/
set feedback on
column member format a50
SELECT l.group#, ROUND(l.bytes / 1024 / 1024) AS mb, l.status, f.member FROM v$log l JOIN v$logfile f ON f.group# = l.group# ORDER BY 1;
exit
