param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }),
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }),
    [switch]$Unsigned,
    [string]$Keystore = $env:MUF_KEYSTORE,
    [string]$KeyAlias = $(if ($env:MUF_KEY_ALIAS) { $env:MUF_KEY_ALIAS } else { 'prototype' })
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$buildRoot = Join-Path $projectRoot '.build\0.8.5'
$outputRoot = Join-Path $projectRoot 'releases\0.8.5'
$debugKey = $Keystore
if (-not $Unsigned -and (-not $debugKey -or -not (Test-Path -LiteralPath $debugKey))) { throw 'Set MUF_KEYSTORE to the original private signing key, or use -Unsigned. No key is generated.' }
if (-not $Unsigned -and -not $env:MUF_STORE_PASSWORD) { throw 'Set MUF_STORE_PASSWORD in the local environment for signing.' }
if (-not $env:MUF_KEY_PASSWORD -and $env:MUF_STORE_PASSWORD) { $env:MUF_KEY_PASSWORD = $env:MUF_STORE_PASSWORD }
$javaExe = Join-Path $Jdk 'bin\java.exe'
$javacExe = Join-Path $Jdk 'bin\javac.exe'
$jarExe = Join-Path $Jdk 'bin\jar.exe'
$keytoolExe = Join-Path $Jdk 'bin\keytool.exe'
$toolsRoot = Join-Path $Sdk 'build-tools\36.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-36\android.jar'
foreach ($required in @($javaExe, $javacExe, $jarExe, $keytoolExe, $androidJar, (Join-Path $toolsRoot 'aapt2.exe'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Alat build tidak ditemukan: $required" }
}
function Invoke-NativeChecked {
    param([string]$Exe, [string[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Perintah gagal ($LASTEXITCODE): $Exe" }
}
foreach ($folder in @($buildRoot, $outputRoot, "$buildRoot\generated", "$buildRoot\classes", "$buildRoot\dex", "$buildRoot\test-classes", "$buildRoot\temp")) {
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
}

# Run the pure-Java behaviour checks before building the APK.
$sourceRoot = Join-Path $projectRoot 'app\src\main\java'
$rules = Join-Path $sourceRoot 'id\local\mufchecker\InquiryRules.java'
$reader = Join-Path $sourceRoot 'id\local\mufchecker\BillReader.java'
$uiRules = Join-Path $sourceRoot 'id\local\mufchecker\UiRules.java'
$sessionRules = Join-Path $sourceRoot 'id\local\mufchecker\InquirySession.java'
$menuRules = Join-Path $sourceRoot 'id\local\mufchecker\TopupMatcher.java'
$traceRules = Join-Path $sourceRoot 'id\local\mufchecker\DiagnosticTrace.java'
$batchRules = Join-Path $sourceRoot 'id\local\mufchecker\BulkQueue.java'
$formatRules = Join-Path $sourceRoot 'id\local\mufchecker\ResultFormatter.java'
Invoke-NativeChecked $javacExe @('-encoding', 'UTF-8', '--release', '8', '-d', "$buildRoot\test-classes", $rules, $reader, $uiRules, $sessionRules, $menuRules, $traceRules, $batchRules, $formatRules, "$sourceRoot\id\local\mufchecker\ReadyPageGate.java", "$sourceRoot\id\local\mufchecker\FlowSupport.java", "$sourceRoot\id\local\mufchecker\SessionReport.java", "$sourceRoot\id\local\mufchecker\FlowTiming.java", "$sourceRoot\id\local\mufchecker\InputVerification.java", "$sourceRoot\id\local\mufchecker\ResultStability.java", "$sourceRoot\id\local\mufchecker\UiPresentation.java", "$projectRoot\tests\InquiryResponseTest.java", "$sourceRoot\id\local\mufchecker\InquiryResponse.java", "$projectRoot\tests\TimingPresentationTest.java", "$projectRoot\tests\InputVerificationTest.java", "$projectRoot\tests\ReadyPageTest.java", "$projectRoot\tests\Release050Test.java", "$projectRoot\tests\RulesTest.java", "$projectRoot\tests\UiFlowTest.java", "$projectRoot\tests\TopupMatcherTest.java", "$projectRoot\tests\DirectFlowTest.java", "$projectRoot\tests\BulkQueueTest.java")
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'RulesTest')
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'UiFlowTest')
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'TopupMatcherTest')
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'DirectFlowTest')
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'BulkQueueTest', "$projectRoot\tests\fixtures\v070-ready-50.properties", "$projectRoot\tests\fixtures\v071-ready-100.properties")
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'Release050Test')
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'ReadyPageTest')
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'TimingPresentationTest')
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'InputVerificationTest')
Invoke-NativeChecked $javaExe @('-cp', "$buildRoot\test-classes", 'InquiryResponseTest')

$manifest = Get-Content -LiteralPath "$projectRoot\app\src\main\AndroidManifest.xml" -Raw
$manifest = $manifest.Replace('<manifest xmlns:android=', '<manifest package="id.local.mufchecker" xmlns:android=')
[System.IO.File]::WriteAllText("$buildRoot\AndroidManifest.xml", $manifest, (New-Object System.Text.UTF8Encoding $false))
Invoke-NativeChecked (Join-Path $toolsRoot 'aapt2.exe') @('compile', '--dir', "$projectRoot\app\src\main\res", '-o', "$buildRoot\resources.zip")
Invoke-NativeChecked (Join-Path $toolsRoot 'aapt2.exe') @('link', '-I', $androidJar, '--manifest', "$buildRoot\AndroidManifest.xml", '--java', "$buildRoot\generated", '--auto-add-overlay', '--version-code', '19', '--version-name', '0.8.5', '-o', "$buildRoot\resources.apk", "$buildRoot\resources.zip")
$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$sources += @(Get-ChildItem -LiteralPath "$buildRoot\generated" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$javacArguments = @('-encoding', 'UTF-8', '--release', '8', '-classpath', $androidJar, '-d', "$buildRoot\classes") + $sources
Invoke-NativeChecked $javacExe $javacArguments
Invoke-NativeChecked $jarExe @("-J-Djava.io.tmpdir=$buildRoot\temp", 'cf', "$buildRoot\classes.jar", '-C', "$buildRoot\classes", '.')
Invoke-NativeChecked $javaExe @('-cp', "$toolsRoot\lib\d8.jar", 'com.android.tools.r8.D8', '--lib', $androidJar, '--min-api', '26', '--output', "$buildRoot\dex", "$buildRoot\classes.jar")
Copy-Item -LiteralPath "$buildRoot\resources.apk" -Destination "$buildRoot\unsigned.apk" -Force
Invoke-NativeChecked $jarExe @("-J-Djava.io.tmpdir=$buildRoot\temp", 'uf', "$buildRoot\unsigned.apk", '-C', "$buildRoot\dex", 'classes.dex')
Invoke-NativeChecked (Join-Path $toolsRoot 'zipalign.exe') @('-f', '4', "$buildRoot\unsigned.apk", "$buildRoot\aligned.apk")
$apkName = if ($Unsigned) { 'MUF-Checker-0.8.5-unsigned.apk' } else { 'MUF-Checker-0.8.5.apk' }
$apkPath = Join-Path $outputRoot $apkName
if ($Unsigned) {
    Copy-Item -LiteralPath "$buildRoot\aligned.apk" -Destination $apkPath -Force
} else {
    Invoke-NativeChecked $javaExe @('-jar', "$toolsRoot\lib\apksigner.jar", 'sign', '--ks', $debugKey, '--ks-key-alias', $KeyAlias, '--ks-pass', 'env:MUF_STORE_PASSWORD', '--key-pass', 'env:MUF_KEY_PASSWORD', '--out', $apkPath, "$buildRoot\aligned.apk")
    Invoke-NativeChecked $javaExe @('-jar', "$toolsRoot\lib\apksigner.jar", 'verify', '--verbose', $apkPath)
}
Invoke-NativeChecked (Join-Path $toolsRoot 'zipalign.exe') @('-c', '4', $apkPath)
$checksums = @(Get-ChildItem -LiteralPath $outputRoot -Filter $apkName | Sort-Object Name | ForEach-Object {
    $apkHash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    "$apkHash  $($_.Name)"
})
Set-Content -LiteralPath "$outputRoot\SHA256.txt" -Value $checksums -Encoding ascii
Write-Output "APK siap: $apkPath"
