# Gradle Wrapper

The project is pinned to Gradle 9.4.0 in `gradle-wrapper.properties`.

In this environment the binary `gradle-wrapper.jar` could not be bundled because outbound binary downloads are blocked. The included `gradlew` / `gradlew.bat` bootstrap the exact wrapper JAR from the official Gradle GitHub repository on first use.

After the first successful run, the JAR is cached locally in this directory and subsequent builds are offline with respect to the wrapper bootstrap.
