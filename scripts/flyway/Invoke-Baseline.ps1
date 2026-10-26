[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('LM_Medical_Test', 'LM_Medical_supplies')]
    [string]$Database,
    [switch]$BackupConfirmed,
    [switch]$AuthorizeBaseline
)

$ErrorActionPreference = 'Stop'
if (-not $BackupConfirmed -or -not $AuthorizeBaseline) {
    throw 'Operacion detenida: confirma un respaldo verificado y autoriza expresamente el baseline con -BackupConfirmed -AuthorizeBaseline.'
}
# Nombres canonicos de la lista autorizada; nunca se admite una URL arbitraria.
$Database = if ($Database -ieq 'LM_Medical_supplies') { 'LM_Medical_supplies' } else { 'LM_Medical_Test' }
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    throw 'Define DB_USERNAME y DB_PASSWORD en el entorno del proceso; las variables de IntelliJ no se comparten con PowerShell.'
}
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw 'Define JAVA_HOME apuntando a tu JDK 21 antes de ejecutar Maven.'
}
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'JAVA_HOME no contiene bin/java.exe.' }
$javaVersion = (& $java -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "21[.\"]') {
    throw 'JAVA_HOME debe apuntar a Java 21. Comprueba el resultado de java -version.'
}

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$connectionUrl = 'jdbc:sqlserver://localhost:1433;databaseName=' + $Database + ';encrypt=true;trustServerCertificate=true'
$v1 = Join-Path $projectRoot 'src/main/resources/db/migration/V1__esquema_inicial.sql'
$v1Hash = (Get-FileHash -LiteralPath $v1 -Algorithm SHA256).Hash
$expectedTables = @('almacenes', 'categorias', 'detalles_salida', 'detalles_transferencia', 'existencias', 'lotes', 'movimientos_inventario', 'operaciones_salida', 'productos', 'proveedores', 'transferencias')

# La comprobacion previa usa exactamente el servidor, base y credenciales de Maven.
# El builder mantiene la contrasena fuera de argumentos y mensajes de consola.
$builder = New-Object System.Data.SqlClient.SqlConnectionStringBuilder
$builder['Data Source'] = 'localhost,1433'
$builder['Initial Catalog'] = $Database
$builder['User ID'] = $env:DB_USERNAME
$builder['Password'] = $env:DB_PASSWORD
$builder['Encrypt'] = $true
$builder['TrustServerCertificate'] = $true
$builder['Connect Timeout'] = 5
$connection = New-Object System.Data.SqlClient.SqlConnection $builder.ConnectionString

function Read-Rows([string]$Sql) {
    $command = $connection.CreateCommand()
    $command.CommandText = $Sql
    $command.CommandTimeout = 60
    try {
        $reader = $command.ExecuteReader()
        try {
            while ($reader.Read()) {
                $row = [ordered]@{}
                for ($i = 0; $i -lt $reader.FieldCount; $i++) {
                    $row[$reader.GetName($i)] = $reader.GetValue($i)
                }
                [pscustomobject]$row
            }
        } finally { $reader.Dispose() }
    } finally { $command.Dispose() }
}

function Read-History {
    @(Read-Rows @'
IF OBJECT_ID(N'dbo.flyway_schema_history', N'U') IS NOT NULL
    SELECT installed_rank, version, type, success
    FROM dbo.flyway_schema_history ORDER BY installed_rank;
'@)
}

function Assert-Baseline($History) {
    $baseline = @($History | Where-Object { $_.version -ceq '1' -and $_.type -ceq 'BASELINE' -and $_.success })
    if ($baseline.Count -ne 1 -or @($History | Where-Object { -not $_.success }).Count -ne 0) {
        throw 'El historial no contiene un unico BASELINE 1 correcto o contiene operaciones fallidas. No se modificara el historial existente.'
    }
}

function Read-BusinessSnapshot {
    $tables = @(Read-Rows @'
SELECT t.name, t.object_id, CONVERT(varchar(33), t.create_date, 126) AS created,
       CONVERT(varchar(33), t.modify_date, 126) AS modified
FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE s.name = N'dbo' AND t.name <> N'flyway_schema_history'
ORDER BY t.name;
'@)
    if ($tables.Count -ne 11 -or (Compare-Object $expectedTables @($tables.name))) {
        throw 'Las tablas de dbo no coinciden con las 11 tablas de negocio esperadas. Revisa el esquema antes del baseline.'
    }
    foreach ($table in $tables) {
        # Todas estas tablas tienen PK id, excepto operaciones_salida (clave).
        $key = if ($table.name -eq 'operaciones_salida') { 'clave' } else { 'id' }
        $command = $connection.CreateCommand()
        $command.CommandText = 'SELECT * FROM [dbo].[' + $table.name + '] ORDER BY [' + $key + '] FOR JSON PATH, INCLUDE_NULL_VALUES;'
        $command.CommandTimeout = 60
        $json = New-Object System.Text.StringBuilder
        try {
            $reader = $command.ExecuteReader()
            try { while ($reader.Read()) { [void]$json.Append($reader.GetString(0)) } }
            finally { $reader.Dispose() }
        } finally { $command.Dispose() }
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($json.ToString()))).Replace('-', '') }
        finally { $sha.Dispose() }
        $count = @(Read-Rows ('SELECT COUNT_BIG(*) AS total FROM [dbo].[' + $table.name + '];'))[0].total
        [pscustomobject][ordered]@{ table = $table.name; objectId = $table.object_id; created = $table.created; modified = $table.modified; rows = $count; sha256 = $hash }
    }
}

Push-Location -LiteralPath $projectRoot
try {
    $connection.Open()
    $target = @(Read-Rows @'
SELECT DB_NAME() AS database_name,
       HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'CREATE TABLE') AS can_create_table,
       HAS_PERMS_BY_NAME(N'dbo', 'SCHEMA', 'ALTER') AS can_alter_schema,
       HAS_PERMS_BY_NAME(N'dbo', 'SCHEMA', 'SELECT') AS can_select,
       HAS_PERMS_BY_NAME(N'dbo', 'SCHEMA', 'INSERT') AS can_insert,
       HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'VIEW DEFINITION') AS can_view_definition;
'@)[0]
    if ($target.database_name -cne $Database) { throw 'Base efectiva incorrecta. Operacion detenida.' }
    if ($target.can_select -ne 1 -or $target.can_view_definition -ne 1) {
        throw 'Faltan permisos SELECT en dbo o VIEW DEFINITION para verificar el esquema y sus datos.'
    }
    Write-Host ('Destino verificado: ' + $connectionUrl)
    $history = @(Read-History)
    if ($history.Count -gt 0) { Assert-Baseline $history }
    elseif ($target.can_create_table -ne 1 -or $target.can_alter_schema -ne 1 -or $target.can_insert -ne 1) {
        throw 'Faltan permisos CREATE TABLE, ALTER en dbo o INSERT en dbo para registrar el historial. Solicita los permisos al administrador.'
    }
    $before = @(Read-BusinessSnapshot)
    $evidence = Join-Path $projectRoot ('target/flyway-baseline/' + $Database + '/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    [void](New-Item -ItemType Directory -Path $evidence -Force)
    [pscustomobject]@{ database = $target.database_name; url = $connectionUrl; backupConfirmed = [bool]$BackupConfirmed; baselineAuthorized = [bool]$AuthorizeBaseline; v1Sha256 = $v1Hash } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'target.json') -Encoding UTF8
    $before | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'before.json') -Encoding UTF8
    $arguments = @('-B', ('-Dflyway.url=' + $connectionUrl), '-Dflyway.baselineVersion=1', '-Dflyway.defaultSchema=dbo', '-Dflyway.schemas=dbo', '-Dflyway.baselineOnMigrate=false', '-Dflyway.cleanDisabled=true')
    if ($history.Count -eq 0) {
        # Solo registra BASELINE 1. No invoca migrate ni ejecuta V1.
        & (Join-Path $projectRoot 'mvnw.cmd') @arguments 'flyway:baseline'
        if ($LASTEXITCODE -ne 0) { throw 'Flyway no completo el baseline. Revisa el error; no actives migraciones automaticas.' }
    } else {
        Write-Host 'BASELINE 1 ya registrado: no se repite el baseline.'
    }
    $history = @(Read-History)
    Assert-Baseline $history
    $history | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'history.json') -Encoding UTF8
    $after = @(Read-BusinessSnapshot)
    $after | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'after.json') -Encoding UTF8
    if (($before | ConvertTo-Json -Depth 5 -Compress) -cne ($after | ConvertTo-Json -Depth 5 -Compress)) {
        throw 'La comparacion antes/despues detecto cambios en tablas o datos de negocio. Revisa las evidencias; no se ejecutaran mas operaciones.'
    }
    if ((Get-FileHash -LiteralPath $v1 -Algorithm SHA256).Hash -cne $v1Hash) { throw 'V1 cambio durante la operacion. Revisa el archivo.' }
    Write-Host ('Verificado mediante SELECT: version=1, type=BASELINE, success=1. Las 11 tablas y sus datos permanecen intactos. Evidencias: ' + $evidence)
    & (Join-Path $projectRoot 'mvnw.cmd') @arguments 'flyway:info'
    if ($LASTEXITCODE -ne 0) { throw 'BASELINE verificado en SQL, pero flyway:info fallo. Revisa el error sin repetir el baseline.' }
} finally {
    $connection.Dispose()
    $builder.Clear()
    Pop-Location
}
