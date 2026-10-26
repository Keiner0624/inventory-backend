[CmdletBinding()]
param(
    [switch]$SqlServer,
    [string]$JavaHome = $env:JAVA_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'pom.xml'))) {
    throw 'No se encontro pom.xml en la raiz del proyecto.'
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Configura JAVA_HOME con Java 21 o utiliza -JavaHome.' }
$java = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'El JavaHome indicado no contiene bin/java.exe.' }
$javaVersion = (& $java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21[\."]') { throw 'Estas pruebas requieren Java 21.' }
if ($SqlServer) {
    foreach ($name in @('DB_USERNAME', 'DB_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            throw "Falta $name en el entorno. No se iniciaran pruebas SQL."
        }
    }
}

$previousJava = $env:JAVA_HOME
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $JavaHome
    $mavenArguments = @('-B', 'test')
    $reportDirectory = 'target/security-phase129/unit'
    if ($SqlServer) {
        Write-Host 'Destino autorizado: localhost:1433 / LM_Medical_Test. Sin migraciones ni borrado de datos.'
        $mavenArguments += '-Psecurity-sqlserver'
        $reportDirectory = 'target/security-phase129/sqlserver'
    }
    $mavenArguments += "-Dsurefire.reportsDirectory=$reportDirectory"
    & .\mvnw.cmd @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Maven termino con errores (codigo $LASTEXITCODE). Revisa $reportDirectory." }
    $reports = @(Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml')
    if ($reports.Count -eq 0) { throw 'Maven no genero resultados de pruebas.' }
    $total = 0
    foreach ($report in $reports) {
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
        if ([int]$xml.testsuite.failures -gt 0 -or [int]$xml.testsuite.errors -gt 0 -or [int]$xml.testsuite.skipped -gt 0) {
            throw 'Hay pruebas fallidas u omitidas; no se considera una verificacion completa.'
        }
        $total += [int]$xml.testsuite.tests
    }
    if ($total -eq 0) { throw 'No se ejecuto ninguna prueba.' }
    Write-Host "Verificacion completa: $total pruebas correctas. Informes: $reportDirectory"
} finally {
    $env:JAVA_HOME = $previousJava
    Pop-Location
}
