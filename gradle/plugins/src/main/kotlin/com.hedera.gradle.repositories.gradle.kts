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

repositories {
    mavenCentral()
    maven { url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-prerelease-channel") }
    maven { url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-commits") }
    maven { url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-adhoc-commits") }
    maven { url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-daily-snapshots") }
    maven { url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-snapshots") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven {
        url = uri("https://hyperledger.jfrog.io/artifactory/besu-maven")
        content { includeGroupByRegex("org\\.hyperledger\\..*") }
    }
    maven {
        url = uri("https://artifacts.consensys.net/public/maven/maven/")
        content { includeGroupByRegex("tech\\.pegasys(\\..*)?") }
    }
    maven { url = uri("https://oss.sonatype.org/content/repositories/comhederahashgraph-1502") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/comhederahashgraph-1531") }
}
