#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
[[ ${GITHUB_ACTIONS:-} == true && ${RUNNER_ENVIRONMENT:-} == github-hosted && $(uname -s) == Linux ]] || {
  echo 'SQL CI solo se ejecuta en un runner Linux hospedado por GitHub' >&2; exit 1;
}
[[ -z ${DOCKER_HOST:-} && -z ${DOCKER_CONTEXT:-} ]] || { echo 'No se permiten motores Docker remotos' >&2; exit 1; }
command=${1:?Indica start, migrate, verify o cleanup}
private="$RUNNER_TEMP/lmmedical-ci-private"
mkdir -p "$private"
chmod 700 "$private"
umask 077
image='mcr.microsoft.com/mssql/server:2025-latest@sha256:245f6b1c8903edae52cb20fa31419cefcde1a49938bb8c3447099011fc871ca3'
sqlcmd=/opt/mssql-tools18/bin/sqlcmd

verify_target() { python3 scripts/ci/container_target.py; }
admin_sql() {
  SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" docker exec -i --env SQLCMDPASSWORD "$CI_SQLSERVER_CONTAINER" \
    "$sqlcmd" -S localhost -U sa -C -b -r1 -l 5 -t 30 -d master
}

case "$command" in
  start)
    [[ $GITHUB_RUN_ID =~ ^[0-9]+$ && $GITHUB_RUN_ATTEMPT =~ ^[0-9]+$ ]] || exit 1
    export CI_SQLSERVER_CONTAINER="lmmedical-ci-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
    export CI_SQLSERVER_MARKER
    CI_SQLSERVER_MARKER=$(openssl rand -hex 24)
    export MSSQL_SA_PASSWORD DB_PASSWORD DB_USERNAME=lm_ci_test
    MSSQL_SA_PASSWORD="Aa9!$(openssl rand -hex 32)"
    DB_PASSWORD="Bb8!$(openssl rand -hex 32)"
    printf '::add-mask::%s\n' "$MSSQL_SA_PASSWORD"
    printf '::add-mask::%s\n' "$DB_PASSWORD"
    # Persistir antes de iniciar: cleanup funciona aunque falle la espera de arranque.
    printf '%s\n' "CI_SQLSERVER_CONTAINER=$CI_SQLSERVER_CONTAINER" "CI_SQLSERVER_MARKER=$CI_SQLSERVER_MARKER" \
      "MSSQL_SA_PASSWORD=$MSSQL_SA_PASSWORD" "DB_USERNAME=$DB_USERNAME" "DB_PASSWORD=$DB_PASSWORD" >> "$GITHUB_ENV"
    docker run --detach --name "$CI_SQLSERVER_CONTAINER" --hostname lmmedical-ci \
      --label "com.lmmedical.ci.marker=$CI_SQLSERVER_MARKER" \
      --memory 3g --cpus 2 --publish 127.0.0.1::1433 \
      --env ACCEPT_EULA=Y --env MSSQL_PID=Developer --env MSSQL_MEMORY_LIMIT_MB=2048 \
      --env MSSQL_SA_PASSWORD "$image" > "$private/container-id.txt" 2> "$private/start.log"
    export CI_SQLSERVER_PORT
    CI_SQLSERVER_PORT=$(verify_target)
    printf 'CI_SQLSERVER_PORT=%s\n' "$CI_SQLSERVER_PORT" >> "$GITHUB_ENV"
    ready=false
    for ((attempt=1; attempt<=30; attempt++)); do
      if printf 'SET NOCOUNT ON; SELECT 1;\n' | admin_sql > "$private/readiness.log" 2>&1; then ready=true; break; fi
      sleep 2
    done
    [[ $ready == true ]] || { echo 'SQL Server no estuvo disponible dentro del limite' >&2; exit 1; }
    echo 'SQL Server CI disponible en un puerto aislado; no se utiliza 1433 del host.'
    ;;
  migrate)
    port=$(verify_target)
    [[ ${DB_USERNAME:-} == lm_ci_test && ${DB_PASSWORD:-} =~ ^Bb8![0-9a-f]{64}$ ]] || exit 1
    [[ ${CI_SQLSERVER_MARKER:-} =~ ^[0-9a-f]{48}$ ]] || exit 1
    # El acceso administrativo ocurre dentro del contenedor, nunca mediante un sqlcmd externo.
    admin_sql > "$private/create.log" 2>&1 <<SQL
SET NOCOUNT ON;
IF DB_ID(N'LM_Medical_Test') IS NOT NULL THROW 51200, 'CI requiere una base nueva', 1;
CREATE DATABASE [LM_Medical_Test];
GO
USE [LM_Medical_Test];
IF DB_NAME() <> N'LM_Medical_Test' THROW 51201, 'Destino CI incorrecto', 1;
EXEC sys.sp_addextendedproperty @name=N'lmmedical_ci_marker', @value=N'$CI_SQLSERVER_MARKER';
GO
SQL
    # Plugin existente, credenciales efimeras solo en el entorno del proceso de migracion.
    if ! DB_USERNAME=sa DB_PASSWORD="$MSSQL_SA_PASSWORD" bash ./mvnw -B --no-transfer-progress \
      "-Dflyway.url=jdbc:sqlserver://127.0.0.1:$port;databaseName=LM_Medical_Test;encrypt=true;trustServerCertificate=true" \
      -Dflyway.target=4 -Dflyway.baselineOnMigrate=false -Dflyway.cleanDisabled=true \
      flyway:migrate flyway:validate > "$private/flyway.log" 2>&1; then
      echo 'Flyway no pudo preparar o validar la base CI; no se ejecutaran las integraciones' >&2; exit 1
    fi
    verify_target > /dev/null
    admin_sql > "$private/grants.log" 2>&1 <<SQL
SET NOCOUNT ON;
CREATE LOGIN [lm_ci_test] WITH PASSWORD=N'$DB_PASSWORD', CHECK_POLICY=ON;
GO
USE [LM_Medical_Test];
IF DB_NAME() <> N'LM_Medical_Test' THROW 51202, 'Destino CI incorrecto', 1;
IF (SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE type='SQL' AND success=1 AND version IN ('1','2','3','4')) <> 4
 OR (SELECT COUNT(*) FROM dbo.flyway_schema_history) <> 4 THROW 51203, 'Historial CI inesperado', 1;
IF (SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND name<>'flyway_schema_history') <> 16
 THROW 51204, 'Esquema CI incompleto', 1;
CREATE USER [lm_ci_test] FOR LOGIN [lm_ci_test];
ALTER ROLE [db_datareader] ADD MEMBER [lm_ci_test];
ALTER ROLE [db_datawriter] ADD MEMBER [lm_ci_test];
GRANT VIEW DEFINITION TO [lm_ci_test];
GO
SQL
    printf 'V1, V2, V3 y V4 verificadas; cuenta CI de pruebas sin permisos DDL.\n'
    ;;
  verify)
    verify_target > /dev/null
    ;;
  cleanup)
    name=${CI_SQLSERVER_CONTAINER:-}
    [[ -n $name ]] || exit 0
    [[ $name =~ ^lmmedical-ci-[0-9]+-[0-9]+$ && ${CI_SQLSERVER_MARKER:-} =~ ^[0-9a-f]{48}$ ]] || exit 1
    # Incluso un contenedor detenido se elimina, pero solo si conserva nuestra etiqueta.
    if ! docker container inspect "$name" > /dev/null 2>&1; then exit 0; fi
    label=$(docker inspect --format '{{ index .Config.Labels "com.lmmedical.ci.marker" }}' "$name")
    [[ $label == "$CI_SQLSERVER_MARKER" ]] || { echo 'Cleanup bloqueado: contenedor ajeno' >&2; exit 1; }
    docker rm --force --volumes "$name" > /dev/null
    echo 'Contenedor y base CI descartados.'
    ;;
  *) echo 'Operacion CI no autorizada' >&2; exit 1;;
esac
