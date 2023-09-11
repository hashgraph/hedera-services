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

import com.hedera.hashgraph.gradlebuild.lifecycle.LifecycleSupport.Companion.configureLifecycleTask

plugins { id("com.hedera.hashgraph.root") }

tasks.register("releaseDevelopSnapshot") { group = "release" }

tasks.register("releaseDevelopDailySnapshot") { group = "release" }

tasks.register("releaseDevelopCommit") { group = "release" }

tasks.register("releaseAdhocCommit") { group = "release" }

tasks.register("releasePrereleaseChannel") { group = "release" }

tasks.register("releaseMavenCentral") { group = "release" }

tasks.register("releaseMavenCentralSnapshot") { group = "release" }

configureLifecycleTask(project, "releaseMavenCentral")

configureLifecycleTask(project, "releaseMavenCentralSnapshot")

configureLifecycleTask(project, "releaseDevelopSnapshot")

configureLifecycleTask(project, "releaseDevelopDailySnapshot")

configureLifecycleTask(project, "releaseDevelopCommit")

configureLifecycleTask(project, "releaseAdhocCommit")

configureLifecycleTask(project, "releasePrereleaseChannel")
