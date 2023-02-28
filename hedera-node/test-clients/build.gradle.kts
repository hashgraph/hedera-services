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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("com.hedera.hashgraph.conventions")
  id("com.hedera.hashgraph.shadow-jar")
}

description = "Hedera Services Test Clients for End to End Tests (EET)"

tasks.jar { manifest { attributes("Automatic-Module-Name" to "com.hedera.node.app.testclient") } }

tasks.test {
  // Disable these EET tests from being executed as part of the gradle "test" task. We should maybe
  // remove them
  // from src/test into src/eet, so it can be part of an eet test task instead. See issue #3412
  // (https://github.com/hashgraph/hedera-services/issues/3412).
  exclude("**/*")
}

configurations { evaluationDependsOn(":hedera-node:hapi-fees") }

sourceSets {
  // Needed because "resource" directory is misnamed. See
  // https://github.com/hashgraph/hedera-services/issues/3361
  main { resources { srcDir("src/main/resource") } }
}

dependencies {
  implementation(project(":hedera-node:hapi-utils"))
  implementation(project(":hedera-node:hapi-fees"))
  implementation(libs.bundles.besu) { exclude("javax.annotation", "javax.annotation-api") }
  implementation(libs.bundles.logging)
  implementation(testLibs.besu.internal)
  implementation(testLibs.commons.collections4)
  implementation(libs.commons.io)
  implementation(libs.guava)
  implementation(libs.hapi) { exclude("javax.annotation", "javax.annotation-api") }

  implementation(libs.grpc.stub)
  implementation(libs.grpc.protobuf)
  implementation(libs.grpc.netty)

  implementation(libs.headlong)
  implementation(libs.log4j.core)
  implementation(testLibs.json)
  implementation(testLibs.junit.jupiter.api)
  implementation(testLibs.picocli)
  implementation(libs.protobuf.java)
  implementation(testLibs.snakeyaml)
  implementation(libs.swirlds.common)
  implementation(testLibs.testcontainers.core)
  itestImplementation(libs.bundles.swirlds)
  itestImplementation(testLibs.bundles.testcontainers)
  itestImplementation(project(":hedera-node:hedera-app"))
  itestImplementation(project(":hedera-node:hedera-app-spi"))
  itestImplementation(project(":hedera-node:hedera-evm"))
  itestImplementation(project(":hedera-node:hedera-evm-impl"))
  itestImplementation(project(":hedera-node:hedera-mono-service"))
  itestImplementation(project(":hedera-node:hedera-admin-service"))
  itestImplementation(project(":hedera-node:hedera-admin-service-impl"))
  itestImplementation(project(":hedera-node:hedera-consensus-service"))
  itestImplementation(project(":hedera-node:hedera-consensus-service-impl"))
  itestImplementation(project(":hedera-node:hedera-file-service"))
  itestImplementation(project(":hedera-node:hedera-file-service-impl"))
  itestImplementation(project(":hedera-node:hedera-network-service"))
  itestImplementation(project(":hedera-node:hedera-network-service-impl"))
  itestImplementation(project(":hedera-node:hedera-schedule-service"))
  itestImplementation(project(":hedera-node:hedera-schedule-service-impl"))
  itestImplementation(project(":hedera-node:hedera-smart-contract-service"))
  itestImplementation(project(":hedera-node:hedera-smart-contract-service-impl"))
  itestImplementation(project(":hedera-node:hedera-token-service"))
  itestImplementation(project(":hedera-node:hedera-token-service-impl"))
  itestImplementation(project(":hedera-node:hedera-util-service"))
  itestImplementation(project(":hedera-node:hedera-util-service-impl"))
  eetImplementation(testLibs.bundles.testcontainers)
  eetImplementation(project(":hedera-node:hedera-app"))
  eetImplementation(project(":hedera-node:hedera-app-spi"))
  eetImplementation(project(":hedera-node:hedera-evm"))
  eetImplementation(project(":hedera-node:hedera-evm-impl"))
  eetImplementation(project(":hedera-node:hedera-mono-service"))
  eetImplementation(project(":hedera-node:hedera-admin-service"))
  eetImplementation(project(":hedera-node:hedera-admin-service-impl"))
  eetImplementation(project(":hedera-node:hedera-consensus-service"))
  eetImplementation(project(":hedera-node:hedera-consensus-service-impl"))
  eetImplementation(project(":hedera-node:hedera-file-service"))
  eetImplementation(project(":hedera-node:hedera-file-service-impl"))
  eetImplementation(project(":hedera-node:hedera-network-service"))
  eetImplementation(project(":hedera-node:hedera-network-service-impl"))
  eetImplementation(project(":hedera-node:hedera-schedule-service"))
  eetImplementation(project(":hedera-node:hedera-schedule-service-impl"))
  eetImplementation(project(":hedera-node:hedera-smart-contract-service"))
  eetImplementation(project(":hedera-node:hedera-smart-contract-service-impl"))
  eetImplementation(project(":hedera-node:hedera-token-service"))
  eetImplementation(project(":hedera-node:hedera-token-service-impl"))
  eetImplementation(project(":hedera-node:hedera-util-service"))
  eetImplementation(project(":hedera-node:hedera-util-service-impl"))
}

tasks.itest {
  systemProperty("junit.jupiter.execution.parallel.enabled", false)
  systemProperty("TAG", "services-node:" + project.version)
  systemProperty("networkWorkspaceDir", File(project.buildDir, "network/itest"))
}

tasks.eet {
  systemProperty("TAG", "services-node:" + project.version)
  systemProperty("networkWorkspaceDir", File(project.buildDir, "network/eet"))
}

tasks.shadowJar {
  dependsOn(project(":hedera-node:hapi-fees").tasks.jar)

  mergeServiceFiles()

  archiveFileName.set("SuiteRunner.jar")
  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
  fileMode = 664
  dirMode = 775

  manifest {
    attributes(
        "Main-Class" to "com.hedera.services.bdd.suites.SuiteRunner", "Multi-Release" to "true")
  }
}

val yahCliJar =
    tasks.register<ShadowJar>("yahCliJar") {
      dependsOn(project(":hedera-node:hapi-fees").tasks.jar)

      group = "shadow"
      from(sourceSets.main.get().output)
      configurations = listOf(project.configurations["runtimeClasspath"])
      mergeServiceFiles()

      exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))

      archiveClassifier.set("yahcli")
      isReproducibleFileOrder = true
      isPreserveFileTimestamps = false
      fileMode = 664
      dirMode = 775

      manifest {
        attributes("Main-Class" to "com.hedera.services.yahcli.Yahcli", "Multi-Release" to "true")
      }
    }

val validationJar =
    tasks.register<ShadowJar>("validationJar") {
      dependsOn(project(":hedera-node:hapi-fees").tasks.jar)

      group = "shadow"
      from(sourceSets.main.get().output)
      configurations = listOf(project.configurations["runtimeClasspath"])
      mergeServiceFiles()

      exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))

      archiveFileName.set("ValidationScenarios.jar")
      isReproducibleFileOrder = true
      isPreserveFileTimestamps = false
      fileMode = 664
      dirMode = 775

      manifest {
        attributes(
            "Main-Class" to "com.hedera.services.bdd.suites.utils.validation.ValidationScenarios",
            "Multi-Release" to "true")
      }
    }

val copyYahCli =
    tasks.register<Copy>("copyYahCli") {
      group = "copy"
      from(yahCliJar)
      into(project.file("yahcli"))
      rename { "yahcli.jar" }
    }

val cleanYahCli =
    tasks.register<Delete>("cleanYahCli") {
      group = "build"
      delete(File(project.file("yahcli"), "yahcli.jar"))
    }

tasks.assemble {
  dependsOn(tasks.shadowJar)
  dependsOn(copyYahCli)
}

tasks.clean { dependsOn(cleanYahCli) }
