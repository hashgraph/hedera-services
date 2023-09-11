package com.hedera.hashgraph.gradlebuild.lifecycle

import org.gradle.api.Project

class LifecycleSupport {
    companion object {
        @JvmStatic
        fun configureLifecycleTask(project: Project, taskName: String) {
            with(project) {
                tasks.named(taskName) {
                    // Link the lifecycle tasks in the root project to the corresponding lifecycle tasks in the
                    // root projects of all included builds.
                    dependsOn(
                        gradle.includedBuilds
                            // Not this build
                            .filter { it.projectDir != projectDir }
                            // Not the build-logic build
                            .filter { it.name != "build-logic" }
                            // Not the depependency-versions build
                            .filter { it.name != "hedera-dependency-versions" }
                            .map { build -> build.task(":${taskName}") }
                    )
                    // Link the lifecycle tasks in the root project to the corresponding lifecycle tasks in the
                    // subprojects.
                    dependsOn(subprojects.map { subproject -> ":${subproject.name}:${taskName}" })
                }
            }
        }
    }
}
