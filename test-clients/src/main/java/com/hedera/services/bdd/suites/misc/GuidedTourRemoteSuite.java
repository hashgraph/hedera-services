/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiApiSpec.customFailingHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.OFF;
import static com.hedera.services.bdd.spec.keys.KeyShape.ON;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuidedTourRemoteSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(GuidedTourRemoteSuite.class);

    public static void main(String... args) {
        new GuidedTourRemoteSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(guidedTour());
    }

    private List<HapiApiSpec> guidedTour() {
        return Arrays.asList(
                //				transferChangesBalance()
                //				updateWithInvalidKeyFailsInPrecheck()
                //				updateWithInvalidatedKeyFailsInHandle()
                //				topLevelHederaKeyMustBeActive()
                //				topLevelListBehavesAsRevocationService()
                balanceLookupContractWorks());
    }

    private HapiApiSpec balanceLookupContractWorks() {
        final long ACTUAL_BALANCE = 1_234L;
        final var contract = "BalanceLookup";

        return customHapiSpec("BalanceLookupContractWorks")
                .withProperties(Map.of("host", "34.74.191.8"))
                .given(
                        cryptoCreate("targetAccount").balance(ACTUAL_BALANCE),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(
                        /* This contract (c.f. src/main/resource/contract/contracts/BalanceLookup/BalanceLookup.sol) assumes
                        a shard and realm of 0; accepts just the sequence number of an account. */
                        contractCallLocal(
                                        contract,
                                        "lookup",
                                        spec ->
                                                new Object[] {
                                                    spec.registry()
                                                            .getAccountID("targetAccount")
                                                            .getAccountNum()
                                                })
                                .has(
                                        resultWith()
                                                .resultThruAbi(
                                                        getABIFor(FUNCTION, "lookup", contract),
                                                        isLiteralResult(
                                                                new Object[] {
                                                                    BigInteger.valueOf(
                                                                            ACTUAL_BALANCE)
                                                                }))));
    }

    private HapiApiSpec topLevelHederaKeyMustBeActive() {
        KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
        SigControl updateSigControl = waclShape.signedWith(sigs(ON, sigs(ON, OFF, OFF)));

        return customHapiSpec("TopLevelListBehavesAsRevocationService")
                .withProperties(Map.of("host", "34.74.191.8"))
                .given()
                .when()
                .then(
                        fileCreate("target")
                                .waclShape(waclShape)
                                .sigControl(ControlForKey.forKey("target", updateSigControl))
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    /* Feature is pending; top-level KeyList should allow deletion with an active
      signature for any ONE of its child keys active. (I.e. a top-level KeyList
      behaves as a revocation service.)

      NOTE: KeyLists lower in the key hierarchy still require all child keys
      to have active signatures.
    */
    private HapiApiSpec topLevelListBehavesAsRevocationService() {
        KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
        SigControl deleteSigControl = waclShape.signedWith(sigs(OFF, sigs(ON, ON, OFF)));

        return customFailingHapiSpec("TopLevelListBehavesAsRevocationService")
                .withProperties(Map.of("host", "34.74.191.8"))
                .given(fileCreate("target").waclShape(waclShape))
                .when()
                .then(
                        fileDelete("target")
                                .sigControl(ControlForKey.forKey("target", deleteSigControl)));
    }

    private HapiApiSpec updateWithInvalidatedKeyFailsInHandle() {
        return customHapiSpec("UpdateWithInvalidatedKeyFailsIHandle")
                .withProperties(Map.of("host", "34.74.191.8"))
                .given(
                        newKeyNamed("oldKey"),
                        newKeyNamed("newKey"),
                        cryptoCreate("target").key("oldKey"))
                .when(cryptoUpdate("target").key("newKey").deferStatusResolution())
                .then(
                        cryptoUpdate("target")
                                .signedBy(GENESIS, "oldKey")
                                .receiverSigRequired(true)
                                .hasPrecheck(OK)
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiApiSpec updateWithInvalidKeyFailsInPrecheck() {
        KeyShape keyShape = listOf(3);

        return HapiApiSpec.customHapiSpec("UpdateWithInvalidKeyFailsInPrecheck")
                .withProperties(Map.of("host", "34.74.191.8"))
                .given(newKeyNamed("invalidPayerKey").shape(keyShape))
                .when()
                .then(
                        cryptoUpdate(SYSTEM_ADMIN)
                                .receiverSigRequired(true)
                                .signedBy("invalidPayerKey")
                                .hasPrecheck(INVALID_SIGNATURE));
    }

    private HapiApiSpec transferChangesBalance() {
        final long AMOUNT = 1_000L;

        return HapiApiSpec.customHapiSpec("TransferChangesBalance")
                .withProperties(Map.of("host", "34.74.191.8"))
                .given(cryptoCreate("targetAccount").balance(0L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, "targetAccount", AMOUNT)))
                .then(
                        getAccountBalance("targetAccount").hasTinyBars(AMOUNT),
                        getAccountInfo("targetAccount").logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
