@echo off
setlocal
cd /d "%~dp0"
set "JAVA_BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVA_BIN=%JAVA_HOME%\bin"
if not defined JAVA_BIN for /d %%D in ("%ProgramFiles%\Java\jdk*") do set "JAVA_BIN=%%~fD\bin"
if not defined JAVA_BIN for /f "delims=" %%J in ('where javac 2^>nul') do if not defined JAVA_BIN for %%F in ("%%J") do set "JAVA_BIN=%%~dpF"
if not defined JAVA_BIN (echo javac was not found. Install JDK 17 or newer and try again.& pause & exit /b 1)
if not exist build mkdir build
"%JAVA_BIN%\javac.exe" --add-modules jdk.httpserver -encoding UTF-8 -d build src\trustcheck\TrustCheckServer.java || (echo Compilation failed.& pause & exit /b 1)
"%JAVA_BIN%\java.exe" --add-modules jdk.httpserver -cp build trustcheck.TrustCheckServer "%~dp0"
