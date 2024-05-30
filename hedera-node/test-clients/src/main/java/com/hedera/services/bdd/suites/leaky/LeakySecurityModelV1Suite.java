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

package com.hedera.services.bdd.suites.leaky;

import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCallV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.hapi.ContractCreateV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.opcodes.Create2OperationV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractHTSV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractKeysHTSV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.ContractMintHTSV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.DeleteTokenPrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.DissociatePrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.ERCPrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.FreezeUnfreezeTokenPrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.GrantRevokeKycV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.LazyCreateThroughPrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.MixedHTSPrecompileTestsV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.PauseUnpauseTokenAccountPrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.SigningReqsV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenExpiryInfoV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenInfoHTSV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.TokenUpdatePrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileV1SecurityModelSuite;
import com.hedera.services.bdd.suites.ethereum.EthereumV1SecurityModelSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationV1SecurityModelSpecs;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class LeakySecurityModelV1Suite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(LeakySecurityModelV1Suite.class);

    public static void main(String... args) {
        new LeakySecurityModelV1Suite().runSuiteSync();
    }

    @NonNull
    final List<HapiSuite> suites;

    public LeakySecurityModelV1Suite() {
        suites = List.of(
                new AssociatePrecompileV1SecurityModelSuite(),
                new ContractBurnHTSV1SecurityModelSuite(),
                new ContractCallV1SecurityModelSuite(),
                new ContractCreateV1SecurityModelSuite(),
                new ContractHTSV1SecurityModelSuite(),
                new ContractKeysHTSV1SecurityModelSuite(),
                new ContractMintHTSV1SecurityModelSuite(),
                new Create2OperationV1SecurityModelSuite(),
                new CreatePrecompileV1SecurityModelSuite(),
                new CryptoTransferHTSV1SecurityModelSuite(),
                new DeleteTokenPrecompileV1SecurityModelSuite(),
                new DissociatePrecompileV1SecurityModelSuite(),
                new ERCPrecompileV1SecurityModelSuite(),
                new EthereumV1SecurityModelSuite(),
                new FreezeUnfreezeTokenPrecompileV1SecurityModelSuite(),
                new GrantRevokeKycV1SecurityModelSuite(),
                new LazyCreateThroughPrecompileV1SecurityModelSuite(),
                new MixedHTSPrecompileTestsV1SecurityModelSuite(),
                new PauseUnpauseTokenAccountPrecompileV1SecurityModelSuite(),
                new SigningReqsV1SecurityModelSuite(),
                new TokenAssociationV1SecurityModelSpecs(),
                new TokenExpiryInfoV1SecurityModelSuite(),
                new TokenInfoHTSV1SecurityModelSuite(),
                new TokenUpdatePrecompileV1SecurityModelSuite(),
                new WipeTokenAccountPrecompileV1SecurityModelSuite());
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return suites.stream()
                .map(HapiSuite::getSpecsInSuite)
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
