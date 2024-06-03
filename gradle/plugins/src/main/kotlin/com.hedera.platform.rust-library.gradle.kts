/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

plugins { base }

tasks.register<Exec>("buildRust") {
    group = "build"
    description = "Build the Rust library"
    var dir = layout.buildDirectory.dir("rust").get().asFile.path
    var id = RustHelper.selectTriplet().identifier
    doFirst {
        executable(RustHelper.rustCommand(false))
        args("build", "--release", "--target", id)
        environment("CARGO_TARGET_DIR", dir)
    }
}

tasks.register<Copy>("copyRustLibraries") {
    // TODO: this can be better organized. needs to be adopted by RE-TEAM and propperly develop the
    // logic
    group = "build"
    description = "Copy Rust binary library files (.so, .dll, .dylib) to the resources folder"
    dependsOn("buildRust")
    from(
        fileTree(
                layout.buildDirectory.dir(
                    "rust/" + RustHelper.selectTriplet().identifier + "/release"
                )
            ) {
                include("*." + RustHelper.selectTriplet().fileExtension)
            }
            .files
    )
    into(
        layout.buildDirectory.dir("resources/main/libs/" + RustHelper.selectTriplet().architecture)
    )
}

tasks.register("cleanRustBuild") {
    notCompatibleWithConfigurationCache("not compatible with Rust")
    var dir = layout.buildDirectory.dir("rust").get().asFile.path
    doLast {
        var command = RustHelper.rustCommand(false)
        if (command != null) {
            exec {
                environment("CARGO_TARGET_DIR", dir)
                executable(command)
                args("clean")
            }
        }
    }
}

tasks.named("processResources") { dependsOn("copyRustLibraries") }

tasks.named("clean") { dependsOn("cleanRustBuild") }
