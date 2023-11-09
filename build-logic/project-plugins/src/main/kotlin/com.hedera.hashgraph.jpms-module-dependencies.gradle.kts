/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

plugins { id("org.gradlex.java-module-dependencies") }

javaModuleDependencies {
    warnForMissingVersions.set(false) // do not expect versions in catalog

    // The following is not working because it looks for 'swirlds.test.framework'
    // instead of 'swirlds-test-framework':
    //   moduleNamePrefixToGroup.put("com.", "com.swirlds")
    // Hence, we list all 'swirlds' modules that are used outside the 'swirlds' build
    moduleNameToGA.put("com.swirlds.base", "com.swirlds:swirlds-base")
    moduleNameToGA.put("com.swirlds.cli", "com.swirlds:swirlds-cli")
    moduleNameToGA.put("com.swirlds.common", "com.swirlds:swirlds-common")
    moduleNameToGA.put(
        "com.swirlds.common.test.fixtures",
        "com.swirlds:swirlds-common|com.swirlds:swirlds-common-test-fixtures"
    )
    moduleNameToGA.put("com.swirlds.common.testing", "com.swirlds:swirlds-common-testing")
    moduleNameToGA.put("com.swirlds.config.api", "com.swirlds:swirlds-config-api")
    moduleNameToGA.put("com.swirlds.config.impl", "com.swirlds:swirlds-config-impl")
    moduleNameToGA.put("com.swirlds.merkle.test", "com.swirlds:swirlds-merkle-test")
    moduleNameToGA.put("com.swirlds.merkledb", "com.swirlds:swirlds-merkledb")
    moduleNameToGA.put("com.swirlds.platform.core", "com.swirlds:swirlds-platform-core")
    moduleNameToGA.put("com.swirlds.platform.gui", "com.swirlds:swirlds-platform-gui")
    moduleNameToGA.put("com.swirlds.test.framework", "com.swirlds:swirlds-test-framework")

    // Other Hedera modules
    moduleNameToGA.put("com.hedera.pbj.runtime", "com.hedera.pbj:pbj-runtime")

    // Third-party modules
    moduleNameToGA.put(
        "io.netty.transport.epoll.linux.x86_64",
        "io.netty:netty-transport-native-epoll|io.netty:netty-transport-native-epoll-linux-x86_64"
    )
    moduleNameToGA.put(
        "io.netty.transport.epoll.linux.aarch_64",
        "io.netty:netty-transport-native-epoll|io.netty:netty-transport-native-epoll-linux-aarch_64"
    )

    // Testing only
    moduleNameToGA.put("org.mockito.junit.jupiter", "org.mockito:mockito-junit-jupiter")
    moduleNameToGA.put("org.objenesis", "org.objenesis:objenesis")
}
