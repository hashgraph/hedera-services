/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class TokenAssociationV1SecurityModelSpecs extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TokenAssociationV1SecurityModelSpecs.class);

    public static final String VANILLA_TOKEN = "TokenD";
    public static final String MULTI_KEY = "multiKey";
    public static final String SIMPLE = "simple";
    public static final String FREEZE_KEY = "freezeKey";

    public static void main(String... args) {
        final var spec = new TokenAssociationV1SecurityModelSpecs();

        spec.deferResultsSummary();
        spec.runSuiteSync();
        spec.summarizeDeferredResults();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(multiAssociationWithSameRepeatedTokenAsExpected());
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    final Stream<DynamicTest> multiAssociationWithSameRepeatedTokenAsExpected() {
        final var nfToken = "nfToken";
        final var civilian = "civilian";
        final var multiAssociate = "multiAssociate";
        final var theContract = "AssociateDissociate";
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> civilianMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("MultiAssociationWithSameRepeatedTokenAsExpected")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "CryptoTransfer,TokenAssociateToAccount,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(civilian)
                                .exposingCreatedIdTo(id -> civilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        tokenCreate(nfToken)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(GENESIS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        uploadInitCode(theContract),
                        contractCreate(theContract))
                .when(sourcing(() -> contractCall(
                                theContract,
                                "tokensAssociate",
                                asHeadlongAddress(civilianMirrorAddr.get()),
                                (new Address[] {
                                    asHeadlongAddress(tokenMirrorAddr.get()), asHeadlongAddress(tokenMirrorAddr.get())
                                }))
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                        .via(multiAssociate)
                        .payingWith(civilian)
                        .gas(4_000_000)))
                .then(
                        childRecordsCheck(
                                multiAssociate,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_ID_REPEATED_IN_TOKEN_LIST)),
                        getAccountInfo(civilian).hasNoTokenRelationship(nfToken));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
