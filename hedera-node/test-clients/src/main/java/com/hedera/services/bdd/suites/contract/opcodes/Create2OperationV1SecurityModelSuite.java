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

package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class Create2OperationV1SecurityModelSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(Create2OperationV1SecurityModelSuite.class);
    private static final String SWISS = "swiss";
    private static final String CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";

    public static void main(String... args) {
        new Create2OperationV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor());
    }

    final Stream<DynamicTest> childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor() {
        final var ft = "fungibleToken";
        final var multiKey = SWISS;
        final var creationAndAssociation = "creationAndAssociation";
        final var immediateChildAssoc = "ImmediateChildAssociation";

        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> childMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("childInheritanceOfAdminKeyAuthorizesParentAssociationInConstructor")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "TokenAssociateToAccount",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                "10_000_000"),
                        newKeyNamed(multiKey),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(ft)
                                .exposingCreatedIdTo(id ->
                                        tokenMirrorAddr.set(hex(asSolidityAddress(HapiPropertySource.asToken(id))))))
                .when(uploadInitCode(immediateChildAssoc), sourcing(() -> contractCreate(
                                immediateChildAssoc, asHeadlongAddress(tokenMirrorAddr.get()))
                        .gas(2_000_000)
                        .adminKey(multiKey)
                        .payingWith(GENESIS)
                        .sigMapPrefixes(uniqueWithFullPrefixesFor(GENESIS, multiKey))
                        .signedBy(GENESIS, multiKey)
                        .exposingNumTo(n -> childMirrorAddr.set("0.0." + (n + 1)))
                        .via(creationAndAssociation)))
                .then(sourcing(() -> getContractInfo(childMirrorAddr.get()).logged()));
    }
}
