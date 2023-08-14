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
    `java-library`
}

// Copy dependencies into `sdk/data/lib`
val copyLib = tasks.register<Copy>("copyLib") {
    from(project.configurations.getByName("runtimeClasspath"))
    into(File(rootProject.projectDir, "sdk/data/lib"))
    shouldRunAfter(tasks.assemble)
}

// Copy built jar into `data/apps` and rename
val copyApp = tasks.register<Copy>("copyApp") {
    from(tasks.jar)
    into(File(rootProject.projectDir, "sdk/data/apps"))
    rename { "${project.name}.jar" }
    shouldRunAfter(tasks.assemble)
}

tasks.assemble {
    dependsOn(copyApp)
    dependsOn(copyLib)
}

val cleanRun = tasks.register("cleanRun") {
    doLast {
        val sdkDir = File(rootProject.projectDir, "sdk")
        rootProject.delete(File(sdkDir, "settingsUsed.txt"))
        rootProject.delete(File(sdkDir, "swirlds.jar"))
        sdkDir.list { _, fileName -> fileName.endsWith(".csv") }
            ?.forEach { file ->
                rootProject.delete(File(sdkDir, file))
            }

        sdkDir.list { _, fileName -> fileName.endsWith(".log") }
            ?.forEach { file ->
                rootProject.delete(File(sdkDir, file))
            }
        rootProject.delete(File(sdkDir, "metricsDoc.tsv"))

        val dataDir = File(sdkDir, "data")
        rootProject.delete(File(dataDir, "accountBalances"))
        rootProject.delete(File(dataDir, "apps"))
        rootProject.delete(File(dataDir, "lib"))
        rootProject.delete(File(dataDir, "recordstreams"))
        rootProject.delete(File(dataDir, "saved"))
    }
}

tasks.clean {
    dependsOn(cleanRun)
}

tasks.jar {
    val mainClass: String by project
    manifest {
        attributes(
            "Main-Class" to mainClass,
        )
    }
}
