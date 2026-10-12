[CmdletBinding()]
param(
    [switch]$ApenasTestar,
    [ValidateRange(1024, 65535)][int]$Porta = 8080
)

$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskOriginalPassword = $env:SECRETARY_PASSWORD
$taskOriginalMavenOptions = $env:MAVEN_OPTS
Push-Location -LiteralPath $taskProjectRoot

try {
    # Reaproveita o JDK portátil deste computador ou um JDK instalado.
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME/bin/java.exe")) {
        $taskPortableJava = Get-ChildItem -LiteralPath '.tools/java' -Directory -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($taskPortableJava) { $env:JAVA_HOME = $taskPortableJava.FullName }
        else { throw 'Instale o JDK 21 e configure JAVA_HOME, conforme o README.' }
    }
    $taskJavaExe = Join-Path $env:JAVA_HOME 'bin/java.exe'

    # O JDK portátil usa os certificados já confiados pelo Windows.
    if (Test-Path -LiteralPath '.tools/java') {
        $env:MAVEN_OPTS = "$taskOriginalMavenOptions -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE"
    }
    $taskMavenExe = if (Test-Path -LiteralPath '.tools/maven/apache-maven-3.9.11/bin/mvn.cmd') {
        Join-Path $taskProjectRoot '.tools/maven/apache-maven-3.9.11/bin/mvn.cmd'
    } else { Join-Path $taskProjectRoot 'mvnw.cmd' }
    $taskMavenArgs = @('--batch-mode', '--no-transfer-progress', "-Dmaven.repo.local=$taskProjectRoot/.tools/m2")

    if ($ApenasTestar) {
        & $taskMavenExe @taskMavenArgs verify
        if ($LASTEXITCODE -ne 0) { throw 'A verificação encontrou um erro.' }
        return
    }

    if (-not $env:SECRETARY_EMAIL) { $env:SECRETARY_EMAIL = Read-Host 'E-mail da secretaria' }
    if (-not $env:SECRETARY_PASSWORD) {
        $taskSecurePassword = Read-Host 'Senha da secretaria (mínimo 8 caracteres)' -AsSecureString
        $env:SECRETARY_PASSWORD = [System.Net.NetworkCredential]::new('', $taskSecurePassword).Password
    }
    if (-not $env:SECRETARY_NAME) { $env:SECRETARY_NAME = 'Secretaria' }
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:PORT = [string]$Porta

    & $taskMavenExe @taskMavenArgs package '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível compilar o sistema.' }
    Write-Host "Após a inicialização, abra http://localhost:$Porta . Para parar, pressione Ctrl+C."
    & $taskJavaExe -jar (Join-Path $taskProjectRoot 'target/tia-zoca-1.0.0.jar') '--server.address=127.0.0.1'
    if ($LASTEXITCODE -ne 0) { throw 'O sistema não iniciou. Confira a mensagem acima.' }
} finally {
    $env:SECRETARY_PASSWORD = $taskOriginalPassword
    $env:MAVEN_OPTS = $taskOriginalMavenOptions
    Pop-Location
}
