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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.assertions.*;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.*;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.apache.logging.log4j.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

// Some of the test cases cannot be converted to use eth calls,
// since they use admin keys, which are held by the txn payer.
// In the case of an eth txn, we revoke the payers keys and the txn would fail.
// The only way an eth account to create a token is the admin key to be of a contractId type.
@Tag(SMART_CONTRACT)
public class SigningReqsSuite {
    private static final String FIRST_CREATE_TXN = "firstCreateTxn";
    private static final String SECOND_CREATE_TXN = "secondCreateTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String MINIMAL_CREATIONS_CONTRACT = "MinimalTokenCreations";

    private static final String LEGACY_ACTIVATIONS_PROP = "contracts.keys.legacyActivations";
    public static final String AUTO_RENEW = "autoRenew";
    public static final int GAS_TO_OFFER = 1_000_000;

    @HapiTest
    final Stream<DynamicTest> autoRenewAccountCanUseLegacySigActivationIfConfigured() {
        final var autoRenew = AUTO_RENEW;
        final AtomicReference<Address> autoRenewMirrorAddr = new AtomicReference<>();
        final AtomicLong contractId = new AtomicLong();
        final var origKey = KeyShape.threshOf(1, ED25519, CONTRACT);
        final AtomicReference<TokenID> createdToken = new AtomicReference<>();

        return propertyPreservingHapiSpec(
                        "AutoRenewAccountCanUseLegacySigActivationIfConfigured",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving(LEGACY_ACTIVATIONS_PROP)
                .given(
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .exposingNumTo(contractId::set)
                                .gas(GAS_TO_OFFER)
                                .refusingEthConversion(),
                        cryptoCreate(autoRenew)
                                .keyShape(origKey.signedWith(sigs(ON, MINIMAL_CREATIONS_CONTRACT)))
                                .exposingCreatedIdTo(id -> autoRenewMirrorAddr.set(idAsHeadlongAddress(id))))
                .when(
                        // Fails without the auto-renew account's full-prefix signature
                        sourcing(() -> contractCall(
                                        MINIMAL_CREATIONS_CONTRACT,
                                        "makeRenewableTokenIndirectly",
                                        autoRenewMirrorAddr.get(),
                                        THREE_MONTHS_IN_SECONDS)
                                .via(FIRST_CREATE_TXN)
                                .gas(10L * GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(CIVILIAN)
                                .refusingEthConversion()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        withOpContext((spec, opLog) -> {
                            final var registry = spec.registry();
                            final var autoRenewNum =
                                    registry.getAccountID(autoRenew).getAccountNum();
                            final var parentContractNum = registry.getContractId(MINIMAL_CREATIONS_CONTRACT)
                                    .getContractNum();
                            final var overrideValue = autoRenewNum + "by[" + parentContractNum + "]";
                            final var propertyUpdate = overriding(LEGACY_ACTIVATIONS_PROP, overrideValue);
                            CustomSpecAssert.allRunFor(spec, propertyUpdate);
                        }),
                        // Succeeds now because the called contract received legacy activation privilege
                        sourcing(() -> contractCall(
                                        MINIMAL_CREATIONS_CONTRACT,
                                        "makeRenewableTokenIndirectly",
                                        autoRenewMirrorAddr.get(),
                                        THREE_MONTHS_IN_SECONDS)
                                .via(SECOND_CREATE_TXN)
                                .gas(10L * GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(CIVILIAN)
                                .refusingEthConversion()),
                        getTxnRecord(SECOND_CREATE_TXN)
                                .andAllChildRecords()
                                .exposingTokenCreationsTo(creations -> createdToken.set(creations.get(0))))
                .then(
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        sourcing(() ->
                                getTokenInfo(asTokenString(createdToken.get())).hasAutoRenewAccount(autoRenew)));
    }
}
