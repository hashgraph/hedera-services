plugins {
    id("me.champeau.jmh") version "0.6.4"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    jmhImplementation(files("../hedera-node/target/classes"));
    jmhImplementation(files("../hapi-fees/target/classes"));
    jmhImplementation(files("../hapi-utils/target/classes"));
//    jmhImplementation("com.swirlds:swirlds-common:0.9.2-rbair");
//    jmhImplementation("com.swirlds:swirlds-fcmap:0.9.2-rbair");
    jmhImplementation("com.hedera.hashgraph:hedera-protobuf-java-api:0.14.0-SNAPSHOT")
    jmhImplementation("com.hedera.hashgraph:ethereumj-core:1.12.0-v0.5.0")

    jmhImplementation("com.hedera.hashgraph:sdk:2.0.5")
    jmhRuntimeOnly("io.grpc:grpc-okhttp:1.35.0")
    jmhRuntimeOnly("org.slf4j:slf4j-simple:1.7.29")
}
