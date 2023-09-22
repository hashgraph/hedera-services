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

package com.hedera.services.bdd.suites.contract.classiccalls;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcingContextual;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.CRYPTO_KEY;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.FAILABLE_CALLS_CONTRACT;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.FAILABLE_CONTROL_KEY;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_ACCOUNT_IDS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_FUNGIBLE_TOKEN_IDS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_NON_FUNGIBLE_TOKEN_IDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.classiccalls.views.FailableIsTokenCall;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FailureCharacterizationSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FailureCharacterizationSuite.class);

    public static void main(String... args) {
        new FailureCharacterizationSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(characterizeClassicFailureModes(List.of(new FailableIsTokenCall())));
    }

    private HapiSpec characterizeClassicFailureModes(List<FailableClassicCall> calls) {
        return defaultHapiSpec("CharacterizeClassicFailureModes")
                .given(uploadInitCode(FAILABLE_CALLS_CONTRACT), contractCreate(FAILABLE_CALLS_CONTRACT))
                .when(
                        classicInventoryIsAvailable(),
                        inParallel(Arrays.stream(ClassicFailureMode.values())
                                .flatMap(mode -> calls.stream()
                                        .filter(call -> call.hasFailureMode(mode))
                                        .map(call -> sourcingContextual(spec -> {
                                            final var params = call.encodedFailureFromInventory(mode, spec);
                                            final var nonStaticTxnId = "CALL-" + call.name() + "-" + mode.name();
                                            final var nonStaticCall = blockingOrder(
                                                    contractCall(FAILABLE_CALLS_CONTRACT, "makeClassicCall", params)
                                                            .via(nonStaticTxnId)
                                                            .hasKnownStatusFrom(SUCCESS, CONTRACT_REVERT_EXECUTED),
                                                    getTxnRecord(nonStaticTxnId)
                                                            .exposingAllTo(records ->
                                                                    call.assertExpectedRecords(records, spec, mode)));
                                            return !call.staticCallOk()
                                                    ? nonStaticCall
                                                    : blockingOrder(
                                                            nonStaticCall,
                                                            contractCallLocal(
                                                                            FAILABLE_CALLS_CONTRACT,
                                                                            "makeClassicCall",
                                                                            params)
                                                                    .hasAnswerOnlyPrecheckFrom(
                                                                            OK, CONTRACT_REVERT_EXECUTED)
                                                                    .exposingResultTo(
                                                                            result -> call.assertExpectedResult(
                                                                                    result, spec, mode)));
                                        })))
                                .toArray(HapiSpecOperation[]::new)))
                .then(doingContextual(ignore -> calls.forEach(FailableClassicCall::reportOnAssertedFailureModes)));
    }

    private HapiSpecOperation classicInventoryIsAvailable() {
        return blockingOrder(
                newKeyNamed(CRYPTO_KEY).shape(ED25519_ON),
                doingContextual(this::saveClassicContractControlledKey),
                inParallel(
                        inParallel(Arrays.stream(VALID_ACCOUNT_IDS)
                                .map(name -> cryptoCreate(name)
                                        .key(FAILABLE_CONTROL_KEY)
                                        .signedBy(DEFAULT_PAYER, CRYPTO_KEY))
                                .toArray(HapiSpecOperation[]::new)),
                        inParallel(Arrays.stream(VALID_FUNGIBLE_TOKEN_IDS)
                                .map(name -> tokenCreate(name)
                                        .tokenType(FUNGIBLE_COMMON)
                                        .adminKey(FAILABLE_CONTROL_KEY)
                                        .supplyKey(FAILABLE_CONTROL_KEY)
                                        .signedBy(DEFAULT_PAYER, CRYPTO_KEY))
                                .toArray(HapiSpecOperation[]::new)),
                        inParallel(Arrays.stream(VALID_NON_FUNGIBLE_TOKEN_IDS)
                                .map(name -> tokenCreate(name)
                                        .tokenType(NON_FUNGIBLE_UNIQUE)
                                        .initialSupply(0)
                                        .adminKey(FAILABLE_CONTROL_KEY)
                                        .supplyKey(FAILABLE_CONTROL_KEY)
                                        .signedBy(DEFAULT_PAYER, CRYPTO_KEY))
                                .toArray(HapiSpecOperation[]::new))),
                inParallel(inParallel(ClassicInventory.VALID_ASSOCIATIONS.stream()
                        .map(association -> tokenAssociate(association.left(), association.right()))
                        .toArray(HapiSpecOperation[]::new))));
    }

    private void saveClassicContractControlledKey(@NonNull final HapiSpec spec) {
        final var registry = spec.registry();
        final var failableControlKey = Key.newBuilder()
                .setThresholdKey(ThresholdKey.newBuilder()
                        .setThreshold(1)
                        .setKeys(KeyList.newBuilder()
                                .addKeys(
                                        Key.newBuilder().setContractID(registry.getContractId(FAILABLE_CALLS_CONTRACT)))
                                .addKeys(registry.getKey(CRYPTO_KEY)))
                        .build())
                .build();
        registry.saveKey(FAILABLE_CONTROL_KEY, failableControlKey);
        spec.keys().setControl(failableControlKey, SigControl.threshSigs(1, OFF, ON));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
