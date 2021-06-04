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

    jmhImplementation("com.hedera.hashgraph:sdk:2.0.5")
    jmhImplementation("com.swirlds:swirlds-common:0.9.4")
    jmhRuntimeOnly("io.grpc:grpc-okhttp:1.35.0")
    jmhRuntimeOnly("org.slf4j:slf4j-simple:1.7.29")
    jmhRuntimeOnly("org.eclipse.collections:eclipse-collections-api:10.4.0")
    jmhRuntimeOnly("org.eclipse.collections:eclipse-collections:10.4.0")
}

jmh {
    jvmArgs.set(listOf(
        "-Djna.library.path=/opt/homebrew/Cellar/libsodium/1.0.18_1/lib",
        "-XX:MaxInlineSize=128",
        "-XX:InlineSmallCode=1024").asIterable());
}
