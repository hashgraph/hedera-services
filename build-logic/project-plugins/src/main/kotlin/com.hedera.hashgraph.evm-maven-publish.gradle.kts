/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

plugins {
    id("java")
    id("com.hedera.hashgraph.maven-publish")
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            pom.developers {
                developer {
                    name.set("Hedera Base Team")
                    email.set("hedera-base@swirldslabs.com")
                    organization.set("Hedera Hashgraph")
                    organizationUrl.set("https://www.hedera.com")
                }
                developer {
                    name.set("Hedera Services Team")
                    email.set("hedera-services@swirldslabs.com")
                    organization.set("Hedera Hashgraph")
                    organizationUrl.set("https://www.hedera.com")
                }
                developer {
                    name.set("Hedera Smart Contracts Team")
                    email.set("hedera-smart-contracts@swirldslabs.com")
                    organization.set("Hedera Hashgraph")
                    organizationUrl.set("https://www.hedera.com")
                }
                developer {
                    name.set("Release Engineering Team")
                    email.set("release-engineering@swirldslabs.com")
                    organization.set("Hedera Hashgraph")
                    organizationUrl.set("https://www.hedera.com")
                }
            }
        }
    }
}
