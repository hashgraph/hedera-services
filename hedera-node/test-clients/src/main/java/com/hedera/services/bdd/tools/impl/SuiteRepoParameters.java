/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.tools.impl;

import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.crypto.AbstractCryptoTransferLoadTest;
import java.util.Set;

public class SuiteRepoParameters {

    public static final String[] hapiSuiteContainingJars = {"hedera-*.jar", "hapi-*.jar", "test-*.jar", "cli-*.jar"};

    public static final String hapiSuiteRootPackageName = "com.hedera.services.bdd";

    public static final Set<Class<?>> hapiAbstractSuiteBases =
            Set.of(HapiSuite.class, LoadTest.class, AbstractCryptoTransferLoadTest.class);
}
