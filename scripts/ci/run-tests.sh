#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
suite=${1:?Indica unit o sqlserver}
case "$suite" in unit|sqlserver) ;; *) echo 'Suite no autorizada' >&2; exit 1;; esac
python_bin=${PYTHON_BIN:-python3}
"$python_bin" scripts/ci/reports.py prepare "$suite"
private=target/ci-private
java -version > "$private/$suite-java.log" 2>&1
if ! grep -Eq 'version "21[."]' "$private/$suite-java.log"; then
  echo 'Se requiere Java 21' >&2
  exit 1
fi
args=(-B --no-transfer-progress test)
if [[ "$suite" == sqlserver ]]; then
  # Nunca recurrir a localhost:1433 si falta la preparacion del contenedor.
  bash scripts/ci/sqlserver.sh verify
  : "${DB_USERNAME:?Falta la cuenta efimera de pruebas}"
  : "${DB_PASSWORD:?Falta la clave efimera de pruebas}"
  args+=(-Psecurity-sqlserver)
  environment=(env -u MSSQL_SA_PASSWORD -u GITHUB_TOKEN)
else
  environment=(env -u DB_USERNAME -u DB_PASSWORD -u JWT_SECRET_BASE64 -u MSSQL_SA_PASSWORD -u GITHUB_TOKEN)
fi
# Los logs crudos son privados al runner; solo reports.py publica JUnit saneado.
status=0
"${environment[@]}" bash ./mvnw "${args[@]}" > "$private/$suite-maven.log" 2>&1 || status=$?
printf '%s\n' "$status" > "$private/$suite-exit-code.txt"
printf 'Maven (%s): codigo %s. Se verificaran los informes JUnit.\n' "$suite" "$status"
exit "$status"
