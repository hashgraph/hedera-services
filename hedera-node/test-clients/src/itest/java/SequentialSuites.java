/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.suites.contract.evm.Evm38ValidationSuite;
import com.hedera.services.bdd.suites.contract.opcodes.Create2OperationSuite;
import com.hedera.services.bdd.suites.contract.traceability.TraceabilitySuite;
import com.hedera.services.bdd.suites.crypto.staking.StakingSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.leaky.FeatureFlagSuite;
import com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite;
import com.hedera.services.bdd.suites.leaky.LeakyCryptoTestsSuite;
import com.hedera.services.bdd.suites.leaky.LeakyEthereumTestsSuite;
import com.hedera.services.bdd.suites.leaky.LeakySecurityModelV1Suite;
import com.hedera.services.bdd.suites.misc.CannotDeleteSystemEntitiesSuite;
import com.hedera.services.bdd.suites.regression.TargetNetworkPrep;
import com.hedera.services.bdd.suites.throttling.PrivilegedOpsSuite;
import java.util.function.Supplier;
import org.apache.commons.lang3.ArrayUtils;

public class SequentialSuites {
    static Supplier<HapiSuite>[] all() {
        return ArrayUtils.addAll(globalPrerequisiteSuites(), sequentialSuites());
    }

    @SuppressWarnings("unchecked")
    static Supplier<HapiSuite>[] globalPrerequisiteSuites() {
        return (Supplier<HapiSuite>[]) new Supplier[] {TargetNetworkPrep::new, FeatureFlagSuite::new};
    }

    @SuppressWarnings("unchecked")
    static Supplier<HapiSuite>[] sequentialSuites() {
        return (Supplier<HapiSuite>[]) new Supplier[] {
            SpecialAccountsAreExempted::new,
            PrivilegedOpsSuite::new,
            TraceabilitySuite::new,
            LeakyContractTestsSuite::new,
            LeakyCryptoTestsSuite::new,
            LeakyEthereumTestsSuite::new,
            LeakySecurityModelV1Suite::new,
            Create2OperationSuite::new,
            CannotDeleteSystemEntitiesSuite::new,
            Evm38ValidationSuite::new,
            StakingSuite::new,
        };
    }
}
