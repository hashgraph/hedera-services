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
    id("application")
    id("com.hedera.hashgraph.java")
    id("com.hedera.hashgraph.dependency-analysis")
}

group = "com.swirlds"

// Find the central SDK deployment dir by searching up the folder hierarchy
fun sdkDir(dir: Directory): Directory = if (dir.dir("sdk").asFile.exists()) dir.dir("sdk") else sdkDir(dir.dir(".."))

// Copy dependencies into `sdk/data/lib`
val copyLib = tasks.register<Copy>("copyLib") {
    from(project.configurations.runtimeClasspath)
    into(sdkDir(layout.projectDirectory).dir("data/lib"))
}

// Copy built jar into `data/apps` and rename
val copyApp = tasks.register<Copy>("copyApp") {
    from(tasks.jar)
    into(sdkDir(layout.projectDirectory).dir("data/apps"))
    rename { "${project.name}.jar" }
}

tasks.assemble {
    dependsOn(copyLib)
    dependsOn(copyApp)
}

val cleanRun = tasks.register<Delete>("cleanRun") {
    val sdkDir = sdkDir(layout.projectDirectory)
    delete(sdkDir.asFileTree.matching {
        include("settingsUsed.txt")
        include("swirlds.jar")
        include("metricsDoc.tsv")
        include("*.csv")
        include("*.log")
    })

    val dataDir = sdkDir.dir("data")
    delete(dataDir.dir("accountBalances"))
    delete(dataDir.dir("apps"))
    delete(dataDir.dir("lib"))
    delete(dataDir.dir("recordstreams"))
    delete(dataDir.dir("saved"))
}

tasks.clean {
    dependsOn(cleanRun)
}

tasks.jar {
    // Gradle fails to track 'configurations.runtimeClasspath' as an input to the task if it is
    // only used in the 'mainfest.attributes'. Hence, we explicitly add it as input.
    inputs.files(configurations.runtimeClasspath)
    manifest {
        attributes(
            "Main-Class" to application.mainClass,
            "Class-Path" to
                    configurations.runtimeClasspath.get().elements.map { entry ->
                        entry
                            .map { copyLib.get().destinationDir.relativeTo(File(copyApp.get().destinationDir, it.asFile.name)) }
                            .sorted()
                            .joinToString(separator = " ")
                    }
        )
    }
}
