@ECHO OFF

SET DIRNAME=%~dp0
IF "%DIRNAME%" == "" SET DIRNAME=.
SET APP_BASE_NAME=%~n0
SET APP_HOME=%DIRNAME%

IF EXIST "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" GOTO launcher

CALL gradle %*
IF "%ERRORLEVEL%" == "0" EXIT /B 0

ECHO.
ECHO ERROR: Gradle wrapper not found and 'gradle' command failed. Install Gradle or restore the wrapper JAR.
ECHO.
EXIT /B %ERRORLEVEL%

:launcher
SET DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

SET JAVA_EXE=java.exe
IF DEFINED JAVA_HOME SET JAVA_EXE=%JAVA_HOME%\bin\java.exe

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
