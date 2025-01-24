// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.module.library")
    id("org.hiero.gradle.feature.protobuf")
    id("org.hiero.gradle.feature.test-fixtures")
    id("com.hedera.pbj.pbj-compiler") version "0.9.16"
}

description = "Hedera API"

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-exports,-deprecation,-removal")
}

sourceSets {
    main {
        pbj {
            srcDir(layout.projectDirectory.dir("hedera-protobufs/services"))
            srcDir(layout.projectDirectory.dir("hedera-protobufs/streams"))
            srcDir(layout.projectDirectory.dir("hedera-protobufs/block"))
            srcDir(layout.projectDirectory.dir("hedera-protobufs/platform"))
            srcDir(layout.projectDirectory.dir("internal-protobufs/network"))
        }
        proto {
            srcDir(layout.projectDirectory.dir("hedera-protobufs/services"))
            srcDir(layout.projectDirectory.dir("hedera-protobufs/streams"))
            srcDir(layout.projectDirectory.dir("hedera-protobufs/block"))
            srcDir(layout.projectDirectory.dir("hedera-protobufs/platform"))
            srcDir(layout.projectDirectory.dir("internal-protobufs/network"))
        }
    }
}

testModuleInfo {
    requires("com.hedera.node.hapi")
    // we depend on the protoc compiled hapi during test as we test our pbj generated code
    // against it to make sure it is compatible
    requires("com.google.protobuf.util")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
}

tasks.test {
    // We are running a lot of tests (10s of thousands), so they need to run in parallel. Make each
    // class run in parallel.
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    // limit amount of threads, so we do not use all CPU
    systemProperties["junit.jupiter.execution.parallel.config.dynamic.factor"] = "0.9"
    // us parallel GC to keep up with high temporary garbage creation,
    // and allow GC to use 40% of CPU if needed
    jvmArgs("-XX:+UseParallelGC", "-XX:GCTimeRatio=90")
}
