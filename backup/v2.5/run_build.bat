@echo off
rem =====================================================================
rem  Build automatico de MassTextApp - a prueba de escapes de terminal
rem  Generado por opencode. Uso: run_build.bat
rem =====================================================================

rem Nos movemos SIEMPRE al directorio del proyecto (sin depender del CWD
rem desde donde nos invocaron)
pushd C:\Users\Pc\MassTextApp

echo. > build_log.txt
call gradlew.bat :app:assembleRelease --console=plain --warning-mode=none > build_log.txt 2>&1
echo GRADLE_EXIT=%ERRORLEVEL% >> build_log.txt

popd
exit /b 0
