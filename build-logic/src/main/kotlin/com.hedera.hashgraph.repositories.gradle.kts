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

repositories {
    mavenCentral()
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-prerelease-channel")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-commits")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-adhoc-commits")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-daily-snapshots")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-snapshots")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
    maven {
        url = uri("https://hyperledger.jfrog.io/artifactory/besu-maven")
        content { includeGroupByRegex("org\\.hyperledger\\..*") }
    }
    maven {
        url = uri("https://artifacts.consensys.net/public/maven/maven/")
        content { includeGroupByRegex("tech\\.pegasys(\\..*)?") }
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/comhederahashgraph-1502")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/comhederahashgraph-1531")
    }
}
