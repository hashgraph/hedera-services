/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.*;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.bdd.spec.*;
import com.hedera.services.bdd.spec.assertions.*;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.apache.logging.log4j.*;

// Some of the test cases cannot be converted to use eth calls,
// since they use admin keys, which are held by the txn payer.
// In the case of an eth txn, we revoke the payers keys and the txn would fail.
// The only way an eth account to create a token is the admin key to be of a contractId type.
public class SigningReqsSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(SigningReqsSuite.class);

    private static final String FIRST_CREATE_TXN = "firstCreateTxn";
    private static final String SECOND_CREATE_TXN = "secondCreateTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String MINIMAL_CREATIONS_CONTRACT = "MinimalTokenCreations";

    private static final String LEGACY_ACTIVATIONS_PROP = "contracts.keys.legacyActivations";
    private static final String DEFAULT_LEGACY_ACTIVATIONS =
            HapiSpecSetup.getDefaultNodeProps().get(LEGACY_ACTIVATIONS_PROP);

    public static void main(String... args) {
        new SigningReqsSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    newAutoRenewAccountMustSignUpdate(),
                    newTreasuryAccountMustSignUpdate(),
                    autoRenewAccountMustSignCreation(),
                    fractionalFeeCollectorMustSign(),
                    selfDenominatedFixedCollectorMustSign(),
                    autoRenewAccountCanUseLegacySigActivationIfConfigured(),
                });
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec selfDenominatedFixedCollectorMustSign() {
        final var fcKey = "fcKey";
        final var arKey = "arKey";
        final var feeCollector = "feeCollector";
        final var autoRenew = "autoRenew";
        final AtomicLong contractId = new AtomicLong();
        final AtomicReference<byte[]> autoRenewAlias = new AtomicReference<>();
        final AtomicReference<byte[]> feeCollectorAlias = new AtomicReference<>();
        final AtomicReference<byte[]> autoRenewMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> feeCollectorMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("SelfDenominatedFixedCollectorMustSign")
                .given(
                        newKeyNamed(arKey).shape(SECP256K1),
                        newKeyNamed(fcKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(autoRenew)
                                .exposingCreatedIdTo(
                                        id -> autoRenewMirrorAddr.set(asSolidityAddress(id)))
                                .key(arKey),
                        cryptoCreate(feeCollector)
                                .exposingCreatedIdTo(
                                        id -> feeCollectorMirrorAddr.set(asSolidityAddress(id)))
                                .key(fcKey),
                        getAccountInfo(autoRenew).exposingAliasTo(autoRenewAlias::set),
                        getAccountInfo(feeCollector).exposingAliasTo(feeCollectorAlias::set),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .exposingNumTo(contractId::set))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "makeRenewableTokenWithSelfDenominatedFixedFee",
                                                        autoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS,
                                                        feeCollectorMirrorAddr.get())
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .alsoSigningWithFullPrefix(autoRenew)
                                                .refusingEthConversion()
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "makeRenewableTokenWithSelfDenominatedFixedFee",
                                                        autoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS,
                                                        feeCollectorMirrorAddr.get())
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .alsoSigningWithFullPrefix(autoRenew, feeCollector)
                                                .refusingEthConversion()))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        sourcing(
                                () ->
                                        getTokenInfo("0.0." + (contractId.get() + 1))
                                                .hasAutoRenewAccount(autoRenew)
                                                .logged()
                                                .hasCustom(
                                                        (spec, fees) -> {
                                                            assertEquals(1, fees.size());
                                                            final var fee = fees.get(0);
                                                            assertTrue(fee.hasFixedFee());
                                                            assertEquals(
                                                                    asToken(
                                                                            "0.0."
                                                                                    + (contractId
                                                                                                    .get()
                                                                                            + 1)),
                                                                    fee.getFixedFee()
                                                                            .getDenominatingTokenId());
                                                            assertEquals(
                                                                    spec.registry()
                                                                            .getAccountID(
                                                                                    feeCollector),
                                                                    fee.getFeeCollectorAccountId());
                                                        })));
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec fractionalFeeCollectorMustSign() {
        final var fcKey = "fcKey";
        final var arKey = "arKey";
        final var feeCollector = "feeCollector";
        final var autoRenew = "autoRenew";
        final AtomicLong contractId = new AtomicLong();
        final AtomicReference<byte[]> autoRenewAlias = new AtomicReference<>();
        final AtomicReference<byte[]> feeCollectorAlias = new AtomicReference<>();
        final AtomicReference<byte[]> autoRenewMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> feeCollectorMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("FractionalFeeCollectorMustSign")
                .given(
                        newKeyNamed(arKey).shape(SECP256K1),
                        newKeyNamed(fcKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(autoRenew)
                                .exposingCreatedIdTo(
                                        id -> autoRenewMirrorAddr.set(asSolidityAddress(id)))
                                .key(arKey),
                        cryptoCreate(feeCollector)
                                .exposingCreatedIdTo(
                                        id -> feeCollectorMirrorAddr.set(asSolidityAddress(id)))
                                .key(fcKey),
                        getAccountInfo(autoRenew).exposingAliasTo(autoRenewAlias::set),
                        getAccountInfo(feeCollector).exposingAliasTo(feeCollectorAlias::set),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .exposingNumTo(contractId::set))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "makeRenewableTokenWithFractionalFee",
                                                        autoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS,
                                                        feeCollectorMirrorAddr.get())
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .alsoSigningWithFullPrefix(autoRenew)
                                                .refusingEthConversion()
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "makeRenewableTokenWithFractionalFee",
                                                        autoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS,
                                                        feeCollectorMirrorAddr.get())
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .alsoSigningWithFullPrefix(autoRenew, feeCollector)
                                                .refusingEthConversion()))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        sourcing(
                                () ->
                                        getTokenInfo("0.0." + (contractId.get() + 1))
                                                .hasAutoRenewAccount(autoRenew)
                                                .logged()
                                                .hasCustom(
                                                        (spec, fees) -> {
                                                            assertEquals(1, fees.size());
                                                            final var fee = fees.get(0);
                                                            assertTrue(fee.hasFractionalFee());
                                                            assertEquals(
                                                                    spec.registry()
                                                                            .getAccountID(
                                                                                    feeCollector),
                                                                    fee.getFeeCollectorAccountId());
                                                        })));
    }

    private HapiApiSpec autoRenewAccountCanUseLegacySigActivationIfConfigured() {
        final var autoRenew = "autoRenew";
        final AtomicReference<byte[]> autoRenewMirrorAddr = new AtomicReference<>();
        final AtomicLong contractId = new AtomicLong();
        final var origKey = KeyShape.threshOf(1, SIMPLE, CONTRACT);

        return defaultHapiSpec("AutoRenewAccountCanUseLegacySigActivationIfConfigured")
                .given(
                        cryptoCreate(CIVILIAN).balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .exposingNumTo(contractId::set)
                                .gas(GAS_TO_OFFER),
                        cryptoCreate(autoRenew)
                                .keyShape(origKey.signedWith(sigs(ON, MINIMAL_CREATIONS_CONTRACT)))
                                .exposingCreatedIdTo(
                                        id -> autoRenewMirrorAddr.set(asSolidityAddress(id))))
                .when(
                        // Fails without the auto-renew account's full-prefix signature
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "makeRenewableTokenIndirectly",
                                                        autoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS)
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .refusingEthConversion()
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    final var autoRenewNum =
                                            registry.getAccountID(autoRenew).getAccountNum();
                                    final var parentContractNum =
                                            registry.getContractId(MINIMAL_CREATIONS_CONTRACT)
                                                    .getContractNum();
                                    final var overrideValue =
                                            autoRenewNum + "by[" + parentContractNum + "]";
                                    final var propertyUpdate =
                                            overriding(LEGACY_ACTIVATIONS_PROP, overrideValue);
                                    CustomSpecAssert.allRunFor(spec, propertyUpdate);
                                }),
                        // Succeeds with the full-prefix signature
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "makeRenewableTokenIndirectly",
                                                        autoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS)
                                                .via(SECOND_CREATE_TXN)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .refusingEthConversion()))
                .then(
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        sourcing(
                                () ->
                                        // Three entities should have been created since the parent
                                        // contract
                                        getTokenInfo("0.0." + (contractId.get() + 3))
                                                .hasAutoRenewAccount(autoRenew)),
                        overriding(LEGACY_ACTIVATIONS_PROP, DEFAULT_LEGACY_ACTIVATIONS));
    }

    private HapiApiSpec autoRenewAccountMustSignCreation() {
        final var arKey = "arKey";
        final var autoRenew = "autoRenew";
        final AtomicReference<byte[]> autoRenewAlias = new AtomicReference<>();
        final AtomicReference<byte[]> autoRenewMirrorAddr = new AtomicReference<>();
        final AtomicLong contractId = new AtomicLong();

        return defaultHapiSpec("AutoRenewAccountMustSignCreation")
                .given(
                        newKeyNamed(arKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(autoRenew)
                                .key(arKey)
                                .exposingCreatedIdTo(
                                        id -> autoRenewMirrorAddr.set(asSolidityAddress(id))),
                        getAccountInfo(autoRenew).exposingAliasTo(autoRenewAlias::set),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .exposingNumTo(contractId::set)
                                .gas(GAS_TO_OFFER))
                .when(
                        // Fails without the auto-renew account's full-prefix signature
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "makeRenewableToken",
                                                        autoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS)
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .refusingEthConversion()
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // Succeeds with the full-prefix signature
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "makeRenewableToken",
                                                        autoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS)
                                                .via(SECOND_CREATE_TXN)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .alsoSigningWithFullPrefix(arKey)
                                                .refusingEthConversion()))
                .then(
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        sourcing(
                                () ->
                                        getTokenInfo("0.0." + (contractId.get() + 1))
                                                .hasAutoRenewAccount(autoRenew)));
    }

    private HapiApiSpec newTreasuryAccountMustSignUpdate() {
        final var ft = "fungibleToken";
        final var ntKey = "ntKey";
        final var adminKey = "adminKey";
        final var updateTxn = "updateTxn";
        final var newTreasury = "newTreasury";
        final AtomicReference<byte[]> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> newTreasuryMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> newTreasuryAliasAddr = new AtomicReference<>();

        return defaultHapiSpec("NewTreasuryAccountMustSignUpdate")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(ntKey).shape(SECP256K1),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(newTreasury)
                                // The new treasury must either already be associated or
                                // have open auto-association slots; it's therefore a bit
                                // odd that we require it to also sign, but this is the
                                // HAPI behavior, so we should be consistent for now
                                .maxAutomaticTokenAssociations(1)
                                .key(ntKey)
                                .exposingCreatedIdTo(
                                        id -> newTreasuryMirrorAddr.set(asSolidityAddress(id))),
                        getAccountInfo(newTreasury).exposingAliasTo(newTreasuryAliasAddr::set),
                        cryptoCreate(CIVILIAN).balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(ft)
                                .adminKey(CIVILIAN)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asSolidityAddress(asToken(idLit)))))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "updateTokenWithNewTreasury",
                                                        tokenMirrorAddr.get(),
                                                        newTreasuryMirrorAddr.get())
                                                .via(updateTxn)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .signingWith(adminKey)
                                                .payingWith(CIVILIAN)
                                                .refusingEthConversion()
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                updateTxn,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        // Treasury account is unchanged
                        getTokenInfo(ft).hasTreasury(TOKEN_TREASURY));
    }

    private HapiApiSpec newAutoRenewAccountMustSignUpdate() {
        final var ft = "fungibleToken";
        final var narKey = "narKey";
        final var adminKey = "adminKey";
        final var updateTxn = "updateTxn";
        final var newAutoRenewAccount = "newAutoRenewAccount";
        final AtomicReference<byte[]> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> newAutoRenewMirrorAddr = new AtomicReference<>();
        final AtomicReference<byte[]> newAutoRenewAliasAddr = new AtomicReference<>();

        return defaultHapiSpec("NewAutoRenewAccountMustSign")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(narKey).shape(SECP256K1),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(newAutoRenewAccount)
                                .maxAutomaticTokenAssociations(2)
                                .key(narKey)
                                .exposingCreatedIdTo(
                                        id -> newAutoRenewMirrorAddr.set(asSolidityAddress(id))),
                        getAccountInfo(newAutoRenewAccount)
                                .exposingAliasTo(newAutoRenewAliasAddr::set),
                        cryptoCreate(CIVILIAN).balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(ft)
                                .autoRenewAccount(TOKEN_TREASURY)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 3600L)
                                .adminKey(CIVILIAN)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asSolidityAddress(asToken(idLit)))))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "updateTokenWithNewAutoRenewInfo",
                                                        tokenMirrorAddr.get(),
                                                        newAutoRenewMirrorAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS + 3600)
                                                .via(updateTxn)
                                                .gas(10 * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .signingWith(adminKey)
                                                .payingWith(CIVILIAN)
                                                .refusingEthConversion()
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                updateTxn,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        // Auto-renew account is unchanged
                        getTokenInfo(ft).hasAutoRenewAccount(TOKEN_TREASURY));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
