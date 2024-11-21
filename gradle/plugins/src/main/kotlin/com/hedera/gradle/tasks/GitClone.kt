/*
 * Copyright (C) 2022-2024 Hiero a Series of LF Projects, LLC
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

package com.hedera.gradle.tasks

import javax.inject.Inject
import org.gradle.StartParameter
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@Suppress("LeakingThis")
abstract class GitClone : DefaultTask() {

    @get:Input abstract val url: Property<String>

    @get:Input @get:Optional abstract val tag: Property<String>

    @get:Input @get:Optional abstract val branch: Property<String>

    @get:Input abstract val offline: Property<Boolean>

    @get:OutputDirectory abstract val localCloneDirectory: DirectoryProperty

    @get:Inject protected abstract val exec: ExecOperations

    @get:Inject protected abstract val startParameter: StartParameter

    @get:Inject protected abstract val layout: ProjectLayout

    init {
        offline.set(startParameter.isOffline)
        localCloneDirectory.set(layout.buildDirectory.dir("hedera-protobufs"))
        // If a 'branch' is configured, the task is never up-to-date as it may change
        outputs.upToDateWhen { !branch.isPresent }
    }

    @TaskAction
    fun cloneOrUpdate() {
        if (!tag.isPresent && !branch.isPresent || tag.isPresent && branch.isPresent) {
            throw RuntimeException("Define either 'tag' or 'branch'")
        }

        val localClone = localCloneDirectory.get()
        if (!offline.get()) {
            exec.exec {
                if (!localClone.dir(".git").asFile.exists()) {
                    workingDir = localClone.asFile.parentFile
                    commandLine("git", "clone", url.get(), "-q")
                } else {
                    workingDir = localClone.asFile
                    commandLine("git", "fetch", "-q")
                }
            }
        }
        if (tag.isPresent) {
            exec.exec {
                workingDir = localClone.asFile
                commandLine("git", "checkout", tag.get(), "-q")
            }
            exec.exec {
                workingDir = localClone.asFile
                commandLine("git", "reset", "--hard", tag.get(), "-q")
            }
        } else {
            exec.exec {
                workingDir = localClone.asFile
                commandLine("git", "checkout", branch.get(), "-q")
            }
            exec.exec {
                workingDir = localClone.asFile
                commandLine("git", "reset", "--hard", "origin/${branch.get()}", "-q")
            }
        }
    }
}
