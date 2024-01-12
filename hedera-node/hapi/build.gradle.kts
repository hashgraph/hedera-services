/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("com.hedera.hashgraph.hapi")
    id("com.hedera.hashgraph.evm-maven-publish")
    @Suppress("DSL_SCOPE_VIOLATION") alias(libs.plugins.pbj)
    id("java-test-fixtures")
}

description = "Hedera API"

val hapiProtoBranchOrTag = "add-first-cons-of-current-block-to-info"
val hederaProtoDir = layout.projectDirectory.dir("hedera-protobufs")

if (!gradle.startParameter.isOffline) {
    @Suppress("UnstableApiUsage")
    providers
        .exec {
            if (!hederaProtoDir.dir(".git").asFile.exists()) {
                workingDir = layout.projectDirectory.asFile
                commandLine(
                    "git",
                    "clone",
                    "https://github.com/hashgraph/hedera-protobufs.git",
                    "-q"
                )
            } else {
                workingDir = hederaProtoDir.asFile
                commandLine("git", "fetch", "-q")
            }
        }
        .result
        .get()
}

@Suppress("UnstableApiUsage")
providers
    .exec {
        workingDir = hederaProtoDir.asFile
        commandLine("git", "checkout", hapiProtoBranchOrTag, "-q")
    }
    .result
    .get()

@Suppress("UnstableApiUsage")
providers
    .exec {
        workingDir = hederaProtoDir.asFile
        commandLine("git", "reset", "--hard", "origin/$hapiProtoBranchOrTag", "-q")
    }
    .result
    .get()

testModuleInfo {
    requires("com.hedera.node.hapi")
    // we depend on the protoc compiled hapi during test as we test our pbj generated code
    // against it to make sure it is compatible
    requires("com.google.protobuf.util")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
}

// Add downloaded HAPI repo protobuf files into build directory and add to sources to build them
sourceSets {
    main {
        pbj {
            srcDir("hedera-protobufs/services")
            srcDir("hedera-protobufs/streams")
        }
        proto {
            srcDir("hedera-protobufs/services")
            srcDir("hedera-protobufs/streams")
        }
    }
}

// Give JUnit more ram and make it execute tests in parallel
tasks.test {
    // We are running a lot of tests 10s of thousands, so they need to run in parallel. Make each
    // class run in parallel.
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    // limit amount of threads, so we do not use all CPU
    systemProperties["junit.jupiter.execution.parallel.config.dynamic.factor"] = "0.9"
    // us parallel GC to keep up with high temporary garbage creation,
    // and allow GC to use 40% of CPU if needed
    jvmArgs("-XX:+UseParallelGC", "-XX:GCTimeRatio=90")
    // Some also need more memory
    minHeapSize = "512m"
    maxHeapSize = "4096m"
}
