#!/bin/sh
# FantasyScout Gradle Wrapper bootstrap script
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v9.4.0/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Gradle Wrapper JAR fehlt. Lade ihn von $WRAPPER_URL ..." >&2
  command -v curl >/dev/null 2>&1 || { echo "curl wird benötigt." >&2; exit 1; }
  curl -fL --retry 3 -o "$WRAPPER_JAR.tmp" "$WRAPPER_URL" || { rm -f "$WRAPPER_JAR.tmp"; exit 1; }
  mv "$WRAPPER_JAR.tmp" "$WRAPPER_JAR"
fi
exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
