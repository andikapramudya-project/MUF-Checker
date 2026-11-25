param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk", [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$build=Join-Path $root '.build\android-smoke'
New-Item -ItemType Directory -Force -Path $build,"$build\classes","$build\dex" | Out-Null
function Run([string]$exe,[string[]]$arguments) { & $exe @arguments; if($LASTEXITCODE -ne 0){throw "Failed: $exe"} }
$manifest=@'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="id.local.mufchecker.smoke">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
  <application android:label="MUF synthetic UI tests" />
  <instrumentation android:name="id.local.mufchecker.smoke.UiSmokeInstrumentation" android:targetPackage="id.local.mufchecker" />
</manifest>
'@
[System.IO.File]::WriteAllText("$build\AndroidManifest.xml",$manifest)
$tools=Join-Path $Sdk 'build-tools\36.0.0'
$android=Join-Path $Sdk 'platforms\android-36\android.jar'
Run "$tools\aapt2.exe" @('link','-I',$android,'--manifest',"$build\AndroidManifest.xml",'-o',"$build\resources.apk")
Run "$Jdk\bin\javac.exe" @('-encoding','UTF-8','--release','8','-classpath',"$android;$root\.build\0.9.1\classes.jar",'-d',"$build\classes","$root\tests\android\UiSmokeInstrumentation.java")
Run "$Jdk\bin\jar.exe" @('cf',"$build\classes.jar",'-C',"$build\classes",'.')
Run "$Jdk\bin\java.exe" @('-cp',"$tools\lib\d8.jar",'com.android.tools.r8.D8','--lib',$android,'--min-api','26','--output',"$build\dex","$build\classes.jar")
Copy-Item "$build\resources.apk" "$build\unsigned.apk" -Force
Run "$Jdk\bin\jar.exe" @('uf',"$build\unsigned.apk",'-C',"$build\dex",'classes.dex')
Run "$tools\zipalign.exe" @('-f','4',"$build\unsigned.apk","$build\aligned.apk")
Run "$Jdk\bin\java.exe" @('-jar',"$tools\lib\apksigner.jar",'sign','--ks',"$root\.build\prototype.jks",'--ks-key-alias','prototype','--ks-pass','pass:android','--key-pass','pass:android','--out',"$build\smoke.apk","$build\aligned.apk")
Write-Output "Emulator-only test APK: $build\smoke.apk"
