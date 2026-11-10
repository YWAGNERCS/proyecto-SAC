#!/bin/bash
# Se ejecuta DENTRO del contenedor sge-jd-bd2: importa /tmp/base.dmp (copia de los datos de desarrollo)
# en el esquema JDREFRIGERACION del PDB FREEPDB1, reemplazando las tablas que ya existan.
set -e
export ORACLE_PDB_SID=FREEPDB1
DIR=$(sqlplus -s / as sysdba <<'SQL' | tr -d '[:space:]'
set heading off feedback off pagesize 0
select directory_path from dba_directories where directory_name = 'DATA_PUMP_DIR';
SQL
)
mkdir -p "$DIR"
cp /tmp/base.dmp "$DIR/base.dmp"
cat > /tmp/imp.par <<PAR
userid="/ as sysdba"
schemas=JDREFRIGERACION
directory=DATA_PUMP_DIR
dumpfile=base.dmp
logfile=base_imp.log
table_exists_action=replace
PAR
set +e
impdp parfile=/tmp/imp.par > /dev/null 2>&1
codigo=$?
tail -n 4 "$DIR/base_imp.log"
rm -f "$DIR/base.dmp" /tmp/base.dmp /tmp/imp.par
echo "impdp termino con codigo $codigo (5 = con avisos, por ejemplo el usuario ya existia)"
