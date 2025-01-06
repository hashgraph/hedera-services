/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

tasks.register("installGitHooks") {
    group = "setup"
    description = "Installs Git hooks"

    val gitDir = layout.projectDirectory.dir(".git")
    val hookSource = layout.projectDirectory.file("scripts/hooks/pre-commit")
    val hookDestination = gitDir.dir("hooks").file("pre-commit")

    doLast {
        if (!gitDir.asFile.exists()) {
            logger.warn(".git directory not found. Skipping Git hooks installation.")
            return@doLast
        }

        val hooksDir = gitDir.dir("hooks").asFile
        if (!hooksDir.exists()) {
            hooksDir.mkdirs()
        }

        if (hookSource.asFile.exists()) {
            hookSource.asFile.copyTo(hookDestination.asFile, overwrite = true)
            hookDestination.asFile.setExecutable(true)
            logger.lifecycle("Pre-commit hook installed at ${hookDestination.asFile.path}")
        } else {
            logger.error("Hook source file not found at ${hookSource.asFile.path}")
        }
    }
}

// Ensure installGitHooks runs before the build task
tasks.named("build") { dependsOn("installGitHooks") }
