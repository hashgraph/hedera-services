package com.hedera.hashgraph.gradlebuild.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class GitClone : DefaultTask() {

    @get:Input
    abstract val url: Property<String>

    @get:Input
    @get:Optional
    abstract val tag: Property<String>

    @get:Input
    @get:Optional
    abstract val branch: Property<String>

    @get:Input
    abstract val offline: Property<Boolean>

    @get:OutputDirectory
    abstract val localCloneDirectory: DirectoryProperty

    @get:Inject
    protected abstract val exec: ExecOperations

    init {
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
                    commandLine(
                        "git",
                        "clone",
                        "https://github.com/LimeChain/hedera-protobufs.git",
                        "-q"
                    )
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
