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

// Copy dependencies into `sdk/data/lib`
val copyLib = tasks.register<Copy>("copyLib") {
    from(project.configurations.runtimeClasspath)
    into(layout.projectDirectory.dir("../../../sdk/data/lib"))
}

// Copy built jar into `data/apps` and rename
val copyApp = tasks.register<Copy>("copyApp") {
    from(tasks.jar)
    into(File(rootProject.projectDir, "../../../sdk/data/apps"))
    rename { "${project.name}.jar" }
}

tasks.assemble {
    dependsOn(copyLib)
    dependsOn(copyApp)
}

val cleanRun = tasks.register<Delete>("cleanRun") {
    val sdkDir = layout.projectDirectory.dir("../../../sdk")
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
    val mainClass: String by project
    manifest {
        attributes(
            "Main-Class" to mainClass,
        )
    }
}
