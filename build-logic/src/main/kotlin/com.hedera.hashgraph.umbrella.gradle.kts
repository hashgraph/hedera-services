import org.gradle.kotlin.dsl.repositories

/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

plugins {
    id("lifecycle-base")
}

// This is for configuring the umbrella build - i.e. the root of the repository, which can
// be used to build everything.
// Because builds are kept as independent as possible, even if they includeBuild each other,
// you can not do things like './gradlew assemble' to run assemble on all projects.
// You have to explicitly make lifecycle tasks available and link them (via dependsOn) to the
// corresponding lifecycle tasks in the other builds.
// https://docs.gradle.org/current/userguide/structuring_software_products_details.html#using_an_umbrella_build

tasks.register("spotlessCheck")
tasks.register("spotlessApply")
tasks.register("checkAllModuleInfo")

configureLifecycleTask("assemble")
configureLifecycleTask("check")
configureLifecycleTask("build")
configureLifecycleTask("spotlessCheck")
configureLifecycleTask("spotlessApply")
configureLifecycleTask("checkAllModuleInfo")

fun configureLifecycleTask(taskName: String) {
    tasks.named(taskName) {
        dependsOn(gradle.includedBuilds.filter { it.name != "build-logic" }.map { build ->
            build.task(":${taskName}")
        })
    }
}
