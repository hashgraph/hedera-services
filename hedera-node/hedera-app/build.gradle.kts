/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

plugins { id("com.hedera.hashgraph.conventions") }

description = "Hedera Application - Implementation"

dependencies {
  annotationProcessor(libs.dagger.compiler)

  implementation(project(":hedera-node:hedera-app-spi"))
  implementation(project(":hedera-node:hedera-mono-service"))
  implementation(project(":hedera-node:hapi-utils"))
  implementation(project(":hedera-node:hapi-fees"))
  implementation(project(":hedera-node:hedera-admin-service-impl"))
  implementation(project(":hedera-node:hedera-consensus-service-impl"))
  implementation(project(":hedera-node:hedera-file-service-impl"))
  implementation(project(":hedera-node:hedera-network-service-impl"))
  implementation(project(":hedera-node:hedera-schedule-service-impl"))
  implementation(project(":hedera-node:hedera-smart-contract-service-impl"))
  implementation(project(":hedera-node:hedera-token-service-impl"))
  implementation(project(":hedera-node:hedera-util-service-impl"))
  implementation(project(":hedera-node:hedera-evm"))
  implementation(libs.bundles.di)
  implementation(libs.bundles.swirlds)
  implementation(libs.bundles.helidon)
  implementation(libs.helidon.grpc.server)
  implementation(libs.pbj.runtime)

  itestImplementation(project(":hedera-node:hapi"))
  itestImplementation(testFixtures(project(":hedera-node:hapi")))
  itestImplementation(testFixtures(project(":hedera-node:hedera-app-spi")))
  itestImplementation(libs.pbj.runtime)
  itestImplementation(libs.bundles.helidon)
  itestImplementation(libs.bundles.swirlds)
  itestImplementation(testLibs.helidon.grpc.client)
  itestImplementation(testLibs.bundles.mockito)
  itestCompileOnly(libs.spotbugs.annotations)

  testImplementation(testFixtures(project(":hedera-node:hedera-mono-service")))
  testImplementation(testFixtures(project(":hedera-node:hedera-app-spi")))
  testImplementation(testLibs.classgraph)
  testImplementation(testLibs.bundles.testing)
  testCompileOnly(libs.spotbugs.annotations)
}

tasks.withType<Test> {
  testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}

// Add all the libs dependencies into the jar manifest!
tasks.jar {
  doFirst {
    tasks.jar.configure {
      manifest {
        attributes(
            "Main-Class" to "com.hedera.node.app.ServicesMain",
            "Class-Path" to
                configurations.getByName("runtimeClasspath").joinToString(separator = " ") {
                  "../../data/lib/" + it.name
                })
      }
    }
  }
}

// Copy dependencies into `data/lib`
val copyLib =
    tasks.register<Copy>("copyLib") {
      from(project.configurations.getByName("runtimeClasspath"))
      into(project(":hedera-node").file("data/lib"))
    }

// Copy built jar into `data/apps` and rename HederaNode.jar
val copyApp =
    tasks.register<Copy>("copyApp") {
      from(tasks.jar)
      into(project(":hedera-node").file("data/apps"))
      rename { "HederaNode.jar" }
      shouldRunAfter(tasks.getByName("copyLib"))
    }

tasks.assemble {
  dependsOn(copyLib)
  dependsOn(copyApp)
}

val generatedSources = file("build/generated/sources/annotationProcessor/java/main")

java.sourceSets["main"].java.srcDir(generatedSources)

// Create the "run" task for running a Hedera consensus node
tasks.register<JavaExec>("run") {
  group = "application"
  dependsOn(tasks.assemble)
  workingDir = project(":hedera-node").projectDir
  jvmArgs = listOf("-cp", "data/lib/*")
  mainClass.set("com.swirlds.platform.Browser")
}

val cleanRun =
    tasks.register("cleanRun") {
      val prj = project(":hedera-node")
      prj.delete(File(prj.projectDir, "database"))
      prj.delete(File(prj.projectDir, "output"))
      prj.delete(File(prj.projectDir, "settingsUsed.txt"))
      prj.delete(File(prj.projectDir, "swirlds.jar"))
      prj.projectDir
          .list { _, fileName -> fileName.startsWith("MainNetStats") }
          ?.forEach { file -> prj.delete(file) }

      val dataDir = File(prj.projectDir, "data")
      prj.delete(File(dataDir, "accountBalances"))
      prj.delete(File(dataDir, "apps"))
      prj.delete(File(dataDir, "lib"))
      prj.delete(File(dataDir, "recordstreams"))
      prj.delete(File(dataDir, "saved"))
    }

tasks.clean { dependsOn(cleanRun) }

tasks.register("showHapiVersion") {
  doLast {
//    println(versionCatalogs.named("libs").findVersion("hapi-version").get().requiredVersion)
  }
}
