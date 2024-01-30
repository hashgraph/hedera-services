package com.hedera.hashgraph.gradlebuild.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class GitClone : DefaultTask() {

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val branchOrTag: Property<String>

    @get:Input
    abstract val offline: Property<Boolean>

    @get:OutputDirectory
    abstract val localCloneDirectory: DirectoryProperty

    @get:Inject
    protected abstract val exec: ExecOperations

    @TaskAction
    fun cloneOrUpdate() {
        val localClone = localCloneDirectory.get()
        if (!offline.get()) {
            exec.exec {
                if (!localClone.dir(".git").asFile.exists()) {
                    workingDir = localClone.asFile.parentFile
                    commandLine(
                        "git",
                        "clone",
                        "https://github.com/hashgraph/hedera-protobufs.git",
                        "-q"
                    )
                } else {
                    workingDir = localClone.asFile
                    commandLine("git", "fetch", "-q")
                }
            }
        }
        exec.exec {
            workingDir = localClone.asFile
            commandLine("git", "checkout", branchOrTag.get(), "-q")
        }
        exec.exec {
            workingDir = localClone.asFile
            commandLine("git", "reset", "--hard", "origin/${branchOrTag.get()}", "-q")
        }
    }
}