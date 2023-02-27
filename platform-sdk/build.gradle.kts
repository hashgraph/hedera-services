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

plugins {
  id("com.swirlds.platform.aggregate-reports")
  id("com.swirlds.platform.spotless-conventions")
  id("com.swirlds.platform.spotless-kotlin-conventions")
}

repositories { mavenCentral() }

tasks.register<JavaExec>("run") {
  group = "application"
  val sdkDir = File(rootProject.projectDir, "sdk")
  workingDir = sdkDir
  jvmArgs =
      listOf(
          "-agentlib:jdwp=transport=dt_socket,address=8888,server=y,suspend=n",
          "-cp",
          "swirlds.jar:data/lib/*",
          "com.swirlds.platform.Browser")
  classpath = rootProject.files(File(sdkDir, "data/lib"))
  maxHeapSize = "8g"
  project(":swirlds-platform-apps:demos").subprojects.forEach {
    dependsOn(it.tasks.named("copyApp"))
    dependsOn(it.tasks.named("copyLib"))
  }
  project(":swirlds-platform-apps:tests").subprojects.forEach {
    dependsOn(it.tasks.named("copyApp"))
    dependsOn(it.tasks.named("copyLib"))
  }
  dependsOn(project(":swirlds").tasks.named("copyApp"))
}
