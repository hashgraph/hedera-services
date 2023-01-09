/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.SelfDestructSuite;
import com.hedera.services.bdd.suites.contract.records.LogsSuite;
import com.hedera.services.bdd.suites.contract.traceability.TraceabilitySuite;
import com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoUpdateSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.leaky.FeatureFlagSuite;
import com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite;
import com.hedera.services.bdd.suites.leaky.LeakyCryptoTestsSuite;
import com.hedera.services.bdd.suites.regression.TargetNetworkPrep;
import com.hedera.services.bdd.suites.throttling.PrivilegedOpsSuite;
import java.util.function.Supplier;

public class SequentialSuites {
    @SuppressWarnings("unchecked")
    static Supplier<HapiSuite>[] all() {
        return (Supplier<HapiSuite>[])
                new Supplier[] {
                    TargetNetworkPrep::new,
                    FeatureFlagSuite::new,
                    AutoAccountUpdateSuite::new,
                    SpecialAccountsAreExempted::new,
                    CryptoUpdateSuite::new,
                    PrivilegedOpsSuite::new,
                    TraceabilitySuite::new,
                    LogsSuite::new,
                    SelfDestructSuite::new,
                    LeakyContractTestsSuite::new,
                    LeakyCryptoTestsSuite::new,
                    Create2OperationSuite::new,
                };
    }
}
