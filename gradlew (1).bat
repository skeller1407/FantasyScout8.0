@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v9.4.0/gradle/wrapper/gradle-wrapper.jar
if exist "%WRAPPER_JAR%" goto run
where curl >nul 2>nul
if errorlevel 1 (
  echo curl wird benoetigt, um den Gradle Wrapper JAR zu laden.
  exit /b 1
)
echo Gradle Wrapper JAR fehlt. Lade ihn ...
curl -fL --retry 3 -o "%WRAPPER_JAR%.tmp" "%WRAPPER_URL%"
if errorlevel 1 (
  del /q "%WRAPPER_JAR%.tmp" 2>nul
  exit /b 1
)
move /y "%WRAPPER_JAR%.tmp" "%WRAPPER_JAR%" >nul
:run
java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
