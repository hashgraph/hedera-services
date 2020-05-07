The main artifact in this image is an executable JAR with main class com.hedera.services.bdd.suites.utils.AddressBookUpdate, copied from ../target/test-clients-*.jar and built via,

$ mvn compile assembly:single@tools-jar

The container run from this image enters a shell that helps you download and update system files 0.0.101 (the "address book") and 0.0.102 (the "node details"). You update a file by editing its downloaded JSON representation in a text editor and then uploading the edited version.

Rebuid the JAR from test-clients/ if necessary, then build the image using:

$ ./build.sh

Run the container for testing against a local network via:

$ docker run -it -v $(pwd):/workspace --net=host svctools

...where the host to target will be "host.docker.internal".
