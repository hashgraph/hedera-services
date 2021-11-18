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

    jmhImplementation("org.rocksdb:rocksdbjni:6.20.3");
    jmhImplementation("org.lmdbjava:lmdbjava:0.8.1");
    jmhImplementation("net.openhft:chronicle-map:3.21ea81")
    jmhImplementation("com.boundary:high-scale-lib:1.0.6")

    jmhImplementation("com.hedera.hashgraph:sdk:2.0.5")
    jmhImplementation("com.swirlds:swirlds-common:0.21.0-vmtest.6")
    jmhImplementation("com.swirlds:swirlds-merkle:0.21.0-vmtest.6")
    jmhImplementation("com.swirlds:swirlds-virtualmap:0.21.0-vmtest.6")
    jmhImplementation("com.swirlds:swirlds-jasperdb:0.21.0-vmtest.6")
    jmhImplementation("com.swirlds:swirlds-logging:0.21.0-vmtest.6")
    jmhImplementation("com.lmax:disruptor:3.4.4")
    jmhRuntimeOnly("io.grpc:grpc-okhttp:1.35.0")
    jmhRuntimeOnly("org.slf4j:slf4j-simple:1.7.29")
    jmhImplementation("org.eclipse.collections:eclipse-collections-api:10.4.0")
    jmhImplementation("org.eclipse.collections:eclipse-collections:10.4.0")
    jmhRuntimeOnly("org.apache.logging.log4j:log4j:2.13.2")
    jmhRuntimeOnly("org.apache.logging.log4j:log4j-1.2-api:2.13.2")
    jmhRuntimeOnly("org.apache.logging.log4j:log4j-core:2.13.2")
    jmhRuntimeOnly("org.fusesource.jansi:jansi:2.3.4")
    jmhImplementation("org.agrona:agrona:1.12.0")
    /*
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j</artifactId>
      <version>${log4j.version}</version>
      <type>pom</type>
    </dependency>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-1.2-api</artifactId>
      <version>${log4j.version}</version>
    </dependency>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-core</artifactId>
      <version>${log4j.version}</version>
    </dependency>

     */
}

(tasks.getByName("jmhJar") as org.gradle.jvm.tasks.Jar).apply {
    manifest.attributes.put("Multi-Release", "true");
}

jmh {
    jvmArgs.set(listOf(
        "-Djna.library.path=/opt/homebrew/Cellar/libsodium/1.0.18_1/lib",
        "-XX:MaxInlineSize=128",
        "-XX:InlineSmallCode=1024").asIterable());
}
