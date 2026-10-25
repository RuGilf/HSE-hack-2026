param([switch]$PrepareOnly, [switch]$Stop, [int]$Port = 0)
$ErrorActionPreference = 'Stop'
$taskRepo = (& git -C $PSScriptRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Run from the Lifety checkout' }
$taskOutput = Join-Path $taskRepo 'build/agent-playground'
$taskConnection = Join-Path $taskOutput 'connection.json'
if ($Stop) {
    if (Test-Path -LiteralPath $taskConnection) {
        $taskInfo = Get-Content -LiteralPath $taskConnection -Raw | ConvertFrom-Json
        Invoke-RestMethod -Uri ($taskInfo.url + '/api/shutdown') -Method Post -Headers @{'X-Playground-Token'=$taskInfo.token} -ContentType 'application/json' -Body '{}' | Out-Null
        Write-Output 'Playground stopped'
    }
    exit
}
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
if (!$env:JAVA_HOME) {
    $taskJava = (Get-Command java).Source
    $env:JAVA_HOME = Split-Path (Split-Path $taskJava -Parent) -Parent
}
if (!$env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (!$PrepareOnly -and (Test-Path -LiteralPath $taskConnection)) {
    try {
        $taskExisting = Get-Content -LiteralPath $taskConnection -Raw | ConvertFrom-Json
        $taskHealth = Invoke-RestMethod -Uri ($taskExisting.url + '/health') -TimeoutSec 2
        if ($taskHealth.ready) { Write-Output $taskExisting.url; exit }
    } catch { }
    Remove-Item -LiteralPath $taskConnection
}

# A local export of the existing host-test classpath; no project Gradle files change.
$taskInit = Join-Path $taskOutput 'classpath.init.gradle'
@'
gradle.projectsEvaluated {
    def sharedProject = gradle.rootProject.project(':shared')
    sharedProject.tasks.register('writeAgentPlaygroundClasspath') {
        dependsOn sharedProject.tasks.named('compileAndroidHostTest')
        doLast {
            def host = sharedProject.tasks.named('testAndroidHostTest').get()
            def output = new File(gradle.rootProject.projectDir, 'build/agent-playground/classpath.txt')
            output.parentFile.mkdirs()
            output.setText(host.classpath.asPath + File.pathSeparator + host.testClassesDirs.asPath, 'UTF-8')
        }
    }
}
'@ | Set-Content -LiteralPath $taskInit -Encoding utf8
Push-Location $taskRepo
try {
    & (Join-Path $taskRepo 'gradlew.bat') --init-script $taskInit :shared:writeAgentPlaygroundClasspath --console=plain *> (Join-Path $taskOutput 'prepare.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $taskOutput 'prepare.log') -Tail 20; throw 'Cannot compile playground' }
} finally { Pop-Location }
$taskClasspath = ([IO.File]::ReadAllText((Join-Path $taskOutput 'classpath.txt'))).Replace('\','/')
# The running JVM must not lock Gradle's output jars/classes on Windows.
$taskRuntime = Join-Path $taskOutput ('runtime/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskRuntime -Force | Out-Null
$taskEntries = $taskClasspath -split [IO.Path]::PathSeparator
for ($taskIndex = 0; $taskIndex -lt $taskEntries.Count; $taskIndex++) {
    $taskEntry = $taskEntries[$taskIndex]
    if ($taskEntry.StartsWith($taskRepo.Replace('\','/') + '/', [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $taskEntry)) {
        $taskCopy = Join-Path $taskRuntime ('entry-' + $taskIndex + [IO.Path]::GetExtension($taskEntry))
        Copy-Item -LiteralPath $taskEntry -Destination $taskCopy -Recurse
        $taskEntries[$taskIndex] = $taskCopy.Replace('\','/')
    }
}
$taskClasspath = $taskEntries -join [IO.Path]::PathSeparator
$taskArgs = Join-Path $taskOutput 'java.args'
$taskRoot = (Join-Path $taskOutput 'session').Replace('\','/')
$taskHtml = (Join-Path $PSScriptRoot 'index.html').Replace('\','/')
[IO.File]::WriteAllLines($taskArgs, @('-cp', ('"'+$taskClasspath+'"'),
    'ru.lifety.feature.assistant.agent.playground.AgentPlaygroundKt',
    ('"'+$taskRoot+'"'), ('"'+$taskHtml+'"'), ('"'+$taskConnection.Replace('\','/')+'"'), $Port.ToString()))
if ($PrepareOnly) { Write-Output "Prepared: $taskArgs"; exit }

$taskPrompted = $false
try {
    if (!$env:LIFETY_LLM_API_KEY) {
        $taskSecret = Read-Host 'LLM API key (not saved)' -AsSecureString
        $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecret)
        try { $env:LIFETY_LLM_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer) }
        $taskPrompted = $true
    }
    if (!$env:LIFETY_LLM_BASE_URL) { $env:LIFETY_LLM_BASE_URL = 'http://localhost:20128/v1' }
    if (!$env:LIFETY_LLM_MODEL) { $env:LIFETY_LLM_MODEL = 'antigravity/gemini-3.8-flash-high' }
    $taskProcess = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList ('@"'+$taskArgs+'"') -WorkingDirectory $taskRepo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskOutput 'server.log') -RedirectStandardError (Join-Path $taskOutput 'server-error.log')
    for ($taskAttempt=0; $taskAttempt -lt 100; $taskAttempt++) {
        if (Test-Path -LiteralPath $taskConnection) { $taskInfo=Get-Content $taskConnection -Raw | ConvertFrom-Json; Write-Output $taskInfo.url; exit }
        if ($taskProcess.HasExited) { Get-Content (Join-Path $taskOutput 'server-error.log') -Tail 12; throw 'Playground process exited' }
        Start-Sleep -Milliseconds 200
    }
    throw 'Playground did not become ready; see build/agent-playground/server-error.log'
} finally { if ($taskPrompted) { Remove-Item Env:LIFETY_LLM_API_KEY -ErrorAction SilentlyContinue } }
