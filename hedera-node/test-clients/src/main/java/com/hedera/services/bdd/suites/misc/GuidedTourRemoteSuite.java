/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.customFailingHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("java:S1144")
public class GuidedTourRemoteSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(GuidedTourRemoteSuite.class);
    public static final String TODO = "<TODO>";
    public static final String HOST = "<DESIRED-HOST>";
    public static final String TARGET_ACCOUNT = "targetAccount";
    public static final String TARGET = "target";
    public static final String OLD_KEY = "oldKey";

    public static void main(String... args) {
        new GuidedTourRemoteSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(guidedTour());
    }

    private List<HapiSpec> guidedTour() {
        return Arrays.asList(
                //				transferChangesBalance()
                //				updateWithInvalidKeyFailsInPrecheck()
                //				updateWithInvalidatedKeyFailsInHandle()
                //				topLevelHederaKeyMustBeActive()
                //				topLevelListBehavesAsRevocationService()
                balanceLookupContractWorks());
    }

    /**
     * Provides an example of a spec that replaces a target account's key with a 1/2 threshold key,
     * where the two top-level keys are the target's existing Ed25519 key and a 2/3 threshold key
     * with all these three keys simple Ed25519.
     *
     * <p>All the keys are imported from PEM files.
     *
     * @return the example spec
     */
    private HapiSpec rekeyAccountWith2Of3Choice() {
        // The account to re-key
        final var target = "0.0.1234";
        // The PEM files with the involved keys
        final var targetKeyLoc = "keys/original1234.pem";
        final var oneOfThreeKeysLoc = "keys/oneOfThree.pem";
        final var twoOfThreeKeysLoc = "keys/twoOfThree.pem";
        final var threeOfThreeKeysLoc = "keys/threeOfThree.pem";
        // The registry names of the key we'll use
        final var extantKey = "original";
        final var newKey1 = "k1";
        final var newKey2 = "k2";
        final var newKey3 = "k3";
        final var replacementKey = "updated";
        final var newShape =
                threshOf(1, PREDEFINED_SHAPE, threshOf(2, PREDEFINED_SHAPE, PREDEFINED_SHAPE, PREDEFINED_SHAPE));

        return customHapiSpec("RekeyAccountWith2Of3Choice")
                .withProperties(Map.of(
                        "nodes", TODO,
                        "default.payer", TODO,
                        "default.payer.pemKeyLoc", TODO,
                        "default.payer.pemKeyPassphrase", TODO))
                .given(
                        keyFromFile(extantKey, targetKeyLoc),
                        keyFromFile(newKey1, oneOfThreeKeysLoc),
                        keyFromFile(newKey2, twoOfThreeKeysLoc),
                        keyFromFile(newKey3, threeOfThreeKeysLoc),
                        newKeyNamed(replacementKey)
                                .shape(newShape.signedWith(sigs(extantKey, sigs(newKey1, newKey2, newKey3)))),
                        getAccountInfo(target).logged().loggingHexedKeys())
                .when(cryptoUpdate(target).key(replacementKey).signedBy(DEFAULT_PAYER, extantKey))
                .then(
                        getAccountInfo(target).loggingHexedKeys().savingProtoTo("targetInfo.bin"),
                        cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(target, FUNDING, 1))
                                .signedBy(DEFAULT_PAYER, extantKey));
    }

    private HapiSpec balanceLookupContractWorks() {
        final long ACTUAL_BALANCE = 1_234L;
        final var contract = "BalanceLookup";

        return customHapiSpec("BalanceLookupContractWorks")
                .withProperties(Map.of("host", HOST))
                .given(
                        cryptoCreate(TARGET_ACCOUNT).balance(ACTUAL_BALANCE),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when()
                .then(
                        /* This contract (c.f. src/main/resource/contract/contracts/BalanceLookup/BalanceLookup.sol) assumes
                        a shard and realm of 0; accepts just the sequence number of an account. */
                        contractCallLocal(contract, "lookup", spec -> new Object[] {
                                    spec.registry().getAccountID(TARGET_ACCOUNT).getAccountNum()
                                })
                                .has(resultWith()
                                        .resultThruAbi(
                                                getABIFor(FUNCTION, "lookup", contract),
                                                isLiteralResult(new Object[] {BigInteger.valueOf(ACTUAL_BALANCE)}))));
    }

    private HapiSpec topLevelHederaKeyMustBeActive() {
        KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
        SigControl updateSigControl = waclShape.signedWith(sigs(ON, sigs(ON, OFF, OFF)));

        return customHapiSpec("TopLevelListBehavesAsRevocationService")
                .withProperties(Map.of("host", HOST))
                .given()
                .when()
                .then(fileCreate(TARGET)
                        .waclShape(waclShape)
                        .sigControl(ControlForKey.forKey(TARGET, updateSigControl))
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    /* Feature is pending; top-level KeyList should allow deletion with an active
      signature for any ONE of its child keys active. (I.e. a top-level KeyList
      behaves as a revocation service.)

      NOTE: KeyLists lower in the key hierarchy still require all child keys
      to have active signatures.
    */
    private HapiSpec topLevelListBehavesAsRevocationService() {
        KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
        SigControl deleteSigControl = waclShape.signedWith(sigs(OFF, sigs(ON, ON, OFF)));

        return customFailingHapiSpec("TopLevelListBehavesAsRevocationService")
                .withProperties(Map.of("host", HOST))
                .given(fileCreate(TARGET).waclShape(waclShape))
                .when()
                .then(fileDelete(TARGET).sigControl(ControlForKey.forKey(TARGET, deleteSigControl)));
    }

    private HapiSpec updateWithInvalidatedKeyFailsInHandle() {
        return customHapiSpec("UpdateWithInvalidatedKeyFailsIHandle")
                .withProperties(Map.of("host", HOST))
                .given(
                        newKeyNamed(OLD_KEY),
                        newKeyNamed("newKey"),
                        cryptoCreate(TARGET).key(OLD_KEY))
                .when(cryptoUpdate(TARGET).key("newKey").deferStatusResolution())
                .then(cryptoUpdate(TARGET)
                        .signedBy(GENESIS, OLD_KEY)
                        .receiverSigRequired(true)
                        .hasPrecheck(OK)
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    private HapiSpec updateWithInvalidKeyFailsInPrecheck() {
        KeyShape keyShape = listOf(3);

        return HapiSpec.customHapiSpec("UpdateWithInvalidKeyFailsInPrecheck")
                .withProperties(Map.of("host", HOST))
                .given(newKeyNamed("invalidPayerKey").shape(keyShape))
                .when()
                .then(cryptoUpdate(SYSTEM_ADMIN)
                        .receiverSigRequired(true)
                        .signedBy("invalidPayerKey")
                        .hasPrecheck(INVALID_SIGNATURE));
    }

    private HapiSpec transferChangesBalance() {
        final long AMOUNT = 1_000L;

        return HapiSpec.customHapiSpec("TransferChangesBalance")
                .withProperties(Map.of("host", HOST))
                .given(cryptoCreate(TARGET_ACCOUNT).balance(0L))
                .when(cryptoTransfer(tinyBarsFromTo(GENESIS, TARGET_ACCOUNT, AMOUNT)))
                .then(
                        getAccountBalance(TARGET_ACCOUNT).hasTinyBars(AMOUNT),
                        getAccountInfo(TARGET_ACCOUNT).logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
