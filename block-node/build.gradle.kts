plugins { id("com.hedera.hashgraph.conventions")
    id("com.hedera.hashgraph.java")}

description = "Hedera Block Node"

repositories {
    mavenCentral()
}
mainModuleInfo {
    runtimeOnly("com.hedera.node.config")
}
dependencies {
    // https://mvnrepository.com/artifact/io.grpc/grpc-all
//    implementation("io.grpc:grpc-all:1.54.1")
//// https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
//    implementation("org.apache.commons:commons-lang3:3.12.0")
//// https://mvnrepository.com/artifact/org.apache.logging.log4j/log4j-core
//    implementation("org.apache.logging.log4j:log4j-core:2.21.1")

}

tasks.checkModuleDirectivesScope { this.enabled = false }