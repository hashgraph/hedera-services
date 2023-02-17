echo 'Started Umbrella Test Script... '
cd ..
java -cp lib/hapiClient-1.0-SNAPSHOT.jar:lib/* -Dlog4j.configurationFile=src/main/resource/log4j2.xml UmbrellaTest
cd bin
echo 'Started Umbrella Test Completed... '
