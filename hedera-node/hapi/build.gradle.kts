/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.google.protobuf.gradle.*

/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
  id("com.hedera.hashgraph.conventions")
  alias(libs.plugins.pbj)
  alias(libs.plugins.protobuf)
  `java-test-fixtures`
}

group = "com.hedera.node"

description = "Hedera API"

dependencies {
  api(libs.spotbugs.annotations)
  api(libs.io.grpc.api)
  api(libs.io.grpc.netty)
  api(libs.io.grpc.services)
  api(libs.io.grpc.stub)
  api(libs.io.grpc.protobuf)
  api(libs.io.grpc.protobuf.lite)
  api(libs.guava)
  api(libs.protobuf.java)
  implementation(libs.pbj.runtime)
  implementation(libs.bundles.di)
  compileOnly(libs.jsr305.annotation)
  testImplementation(testLibs.bundles.testing)
  testFixturesImplementation(libs.pbj.runtime)
}

// Configure Protobuf Plugin to download protoc executable rather than using local installed version
protobuf {
  protoc { artifact = "com.google.protobuf:protoc:" + libs.versions.protobuf.java.version.get() }
  plugins {
    // Add GRPC plugin as we need to generate GRPC services
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:" + libs.versions.protoc.gen.grpc.java.version.get()
    }
  }
  generateProtoTasks { ofSourceSet("main").forEach { it.plugins { id("grpc") } } }
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
tasks.withType<Test> {
  // We are running a lot of tests 10s of thousands, so they need to run in parallel. Make each
  // class run in parallel.
  systemProperties["junit.jupiter.execution.parallel.enabled"] = true
  systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
  // limit amount of threads, so we do not use all CPU
  systemProperties["junit.jupiter.execution.parallel.config.dynamic.factor"] = "0.9"
  // us parallel GC to keep up with high temporary garbage creation, and allow GC to use 40% of CPU
  // if needed
  jvmArgs("-XX:+UseParallelGC", "-XX:GCTimeRatio=90")
  // Some also need more memory
  minHeapSize = "512m"
  maxHeapSize = "4096m"
}

// Add "hedera-protobufs" repository to clean task
tasks.named("clean") { delete(projectDir.absolutePath + "hedera-protobufs") }
