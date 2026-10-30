@echo off
setlocal
set "GRADLE_PARENT=%USERPROFILE%\.gradle\wrapper\dists\gradle-8.13-bin\dicewars-bootstrap"
set "GRADLE_HOME=%GRADLE_PARENT%\gradle-8.13"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $root='%GRADLE_PARENT%'; $zip=Join-Path $env:TEMP 'gradle-8.13-bin.zip'; $url='https://services.gradle.org/distributions/gradle-8.13-bin.zip'; Invoke-WebRequest -Uri $url -OutFile $zip; $actual=(Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant(); if ($actual -ne '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78') { throw 'Gradle distribution SHA-256 mismatch' }; New-Item -ItemType Directory -Force -Path $root | Out-Null; Expand-Archive -LiteralPath $zip -DestinationPath $root -Force; Remove-Item -LiteralPath $zip -Force"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%

