ECHO OFF
CLS
ECHO.
ECHO "Umbrella Test Script started"
cd ..
java -cp lib/hapiClient-0.0.1-SNAPSHOT.jar;lib/* -Dlog4j.configurationFile=src/main/resource/log4j2.xml UmbrellaTest
cd bin
ECHO "Umbrella Test Script completed"

