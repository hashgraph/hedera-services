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
package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
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

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.*;
import com.hedera.services.bdd.spec.assertions.*;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.*;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.apache.logging.log4j.*;

// Some of the test cases cannot be converted to use eth calls,
// since they use admin keys, which are held by the txn payer.
// In the case of an eth txn, we revoke the payers keys and the txn would fail.
// The only way an eth account to create a token is the admin key to be of a contractId type.
public class SigningReqsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SigningReqsSuite.class);

    private static final String FIRST_CREATE_TXN = "firstCreateTxn";
    private static final String SECOND_CREATE_TXN = "secondCreateTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String MINIMAL_CREATIONS_CONTRACT = "MinimalTokenCreations";

    private static final String LEGACY_ACTIVATIONS_PROP = "contracts.keys.legacyActivations";
    public static final String AUTO_RENEW = "autoRenew";
    public static final String AR_KEY = "arKey";

    public static void main(String... args) {
        new SigningReqsSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                newAutoRenewAccountMustSignUpdate(),
                newTreasuryAccountMustSignUpdate(),
                autoRenewAccountMustSignCreation(),
                fractionalFeeCollectorMustSign(),
                selfDenominatedFixedCollectorMustSign(),
                autoRenewAccountCanUseLegacySigActivationIfConfigured());
    }

    @SuppressWarnings("java:S5960")
    private HapiSpec selfDenominatedFixedCollectorMustSign() {
        final var fcKey = "fcKey";
        final var arKey = AR_KEY;
        final var feeCollector = "feeCollector";
        final var autoRenew = AUTO_RENEW;
        final AtomicLong contractId = new AtomicLong();
        final AtomicReference<Address> autoRenewAlias = new AtomicReference<>();
        final AtomicReference<Address> feeCollectorAlias = new AtomicReference<>();
        final AtomicReference<TokenID> createdToken = new AtomicReference<>();

        return defaultHapiSpec("SelfDenominatedFixedCollectorMustSign")
                .given(
                        newKeyNamed(arKey).shape(SECP256K1),
                        newKeyNamed(fcKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        cryptoCreate(autoRenew)
                                .exposingEvmAddressTo(autoRenewAlias::set)
                                .key(arKey),
                        cryptoCreate(feeCollector)
                                .exposingEvmAddressTo(feeCollectorAlias::set)
                                .key(fcKey),
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
                                                        autoRenewAlias.get(),
                                                        THREE_MONTHS_IN_SECONDS,
                                                        feeCollectorAlias.get())
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10L * GAS_TO_OFFER)
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
                                                        autoRenewAlias.get(),
                                                        THREE_MONTHS_IN_SECONDS,
                                                        feeCollectorAlias.get())
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10L * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .alsoSigningWithFullPrefix(autoRenew, feeCollector)
                                                .refusingEthConversion()))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN)
                                .andAllChildRecords()
                                .exposingTokenCreationsTo(
                                        creations -> createdToken.set(creations.get(0))),
                        sourcing(
                                () ->
                                        getTokenInfo(asTokenString(createdToken.get()))
                                                .hasAutoRenewAccount(autoRenew)
                                                .hasCustom(
                                                        (spec, fees) -> {
                                                            assertEquals(1, fees.size());
                                                            final var fee = fees.get(0);
                                                            assertTrue(fee.hasFixedFee());
                                                            assertEquals(
                                                                    createdToken.get(),
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
    private HapiSpec fractionalFeeCollectorMustSign() {
        final var fcKey = "fcKey";
        final var arKey = AR_KEY;
        final var feeCollector = "feeCollector";
        final var autoRenew = AUTO_RENEW;
        final AtomicLong contractId = new AtomicLong();
        final AtomicReference<Address> autoRenewAlias = new AtomicReference<>();
        final AtomicReference<Address> feeCollectorAlias = new AtomicReference<>();
        final AtomicReference<TokenID> createdToken = new AtomicReference<>();

        return defaultHapiSpec("FractionalFeeCollectorMustSign")
                .given(
                        newKeyNamed(arKey).shape(SECP256K1),
                        newKeyNamed(fcKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        cryptoCreate(autoRenew)
                                .exposingEvmAddressTo(autoRenewAlias::set)
                                .key(arKey),
                        cryptoCreate(feeCollector)
                                .exposingEvmAddressTo(feeCollectorAlias::set)
                                .key(fcKey),
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
                                                        autoRenewAlias.get(),
                                                        THREE_MONTHS_IN_SECONDS,
                                                        feeCollectorAlias.get())
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10L * GAS_TO_OFFER)
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
                                                        autoRenewAlias.get(),
                                                        THREE_MONTHS_IN_SECONDS,
                                                        feeCollectorAlias.get())
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10L * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .alsoSigningWithFullPrefix(autoRenew, feeCollector)
                                                .refusingEthConversion()))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN)
                                .andAllChildRecords()
                                .exposingTokenCreationsTo(
                                        creations -> createdToken.set(creations.get(0))),
                        sourcing(
                                () ->
                                        getTokenInfo(asTokenString(createdToken.get()))
                                                .hasAutoRenewAccount(autoRenew)
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

    private HapiSpec autoRenewAccountCanUseLegacySigActivationIfConfigured() {
        final var autoRenew = AUTO_RENEW;
        final AtomicReference<Address> autoRenewMirrorAddr = new AtomicReference<>();
        final AtomicLong contractId = new AtomicLong();
        final var origKey = KeyShape.threshOf(1, ED25519, CONTRACT);
        final AtomicReference<TokenID> createdToken = new AtomicReference<>();

        return propertyPreservingHapiSpec("AutoRenewAccountCanUseLegacySigActivationIfConfigured")
                .preserving(LEGACY_ACTIVATIONS_PROP)
                .given(
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .exposingNumTo(contractId::set)
                                .gas(GAS_TO_OFFER),
                        cryptoCreate(autoRenew)
                                .keyShape(origKey.signedWith(sigs(ON, MINIMAL_CREATIONS_CONTRACT)))
                                .exposingCreatedIdTo(
                                        id -> autoRenewMirrorAddr.set(idAsHeadlongAddress(id))))
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
                                                .gas(10L * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .refusingEthConversion()
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords(),
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
                                                .gas(10L * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .refusingEthConversion()),
                        getTxnRecord(SECOND_CREATE_TXN)
                                .andAllChildRecords()
                                .exposingTokenCreationsTo(
                                        creations -> createdToken.set(creations.get(0))))
                .then(
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        sourcing(
                                () ->
                                        getTokenInfo(asTokenString(createdToken.get()))
                                                .hasAutoRenewAccount(autoRenew)));
    }

    private HapiSpec autoRenewAccountMustSignCreation() {
        final var arKey = AR_KEY;
        final var autoRenew = AUTO_RENEW;
        final AtomicReference<Address> autoRenewAlias = new AtomicReference<>();
        final AtomicLong contractId = new AtomicLong();
        final AtomicReference<TokenID> createdToken = new AtomicReference<>();

        return defaultHapiSpec("AutoRenewAccountMustSignCreation")
                .given(
                        newKeyNamed(arKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        cryptoCreate(autoRenew)
                                .exposingEvmAddressTo(autoRenewAlias::set)
                                .key(arKey),
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
                                                        autoRenewAlias.get(),
                                                        THREE_MONTHS_IN_SECONDS)
                                                .via(FIRST_CREATE_TXN)
                                                .gas(10L * GAS_TO_OFFER)
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
                                                        autoRenewAlias.get(),
                                                        THREE_MONTHS_IN_SECONDS)
                                                .via(SECOND_CREATE_TXN)
                                                .gas(10L * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                                .payingWith(CIVILIAN)
                                                .alsoSigningWithFullPrefix(arKey)
                                                .refusingEthConversion()))
                .then(
                        getTxnRecord(SECOND_CREATE_TXN)
                                .andAllChildRecords()
                                .exposingTokenCreationsTo(
                                        creations -> createdToken.set(creations.get(0))),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        sourcing(
                                () ->
                                        getTokenInfo(asTokenString(createdToken.get()))
                                                .hasAutoRenewAccount(autoRenew)));
    }

    private HapiSpec newTreasuryAccountMustSignUpdate() {
        final var ft = "fungibleToken";
        final var ntKey = "ntKey";
        final var updateTxn = "updateTxn";
        final var newTreasury = "newTreasury";
        final AtomicReference<Address> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> newTreasuryAliasAddr = new AtomicReference<>();

        return defaultHapiSpec("NewTreasuryAccountMustSignUpdate")
                .given(
                        newKeyNamed(ntKey).shape(SECP256K1),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(newTreasury)
                                // The new treasury must either already be associated or
                                // have open auto-association slots; it's therefore a bit
                                // odd that we require it to also sign, but this is the
                                // HAPI behavior, so we should be consistent for now
                                .maxAutomaticTokenAssociations(1)
                                .key(ntKey)
                                .exposingEvmAddressTo(newTreasuryAliasAddr::set),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(ft)
                                .adminKey(CIVILIAN)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        idAsHeadlongAddress(asToken(idLit)))))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "updateTokenWithNewTreasury",
                                                        tokenMirrorAddr.get(),
                                                        newTreasuryAliasAddr.get())
                                                .via(updateTxn)
                                                .gas(10L * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
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

    private HapiSpec newAutoRenewAccountMustSignUpdate() {
        final var ft = "fungibleToken";
        final var narKey = "narKey";
        final var adminKey = "adminKey";
        final var updateTxn = "updateTxn";
        final var newAutoRenewAccount = "newAutoRenewAccount";
        final AtomicReference<Address> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> newAutoRenewAliasAddr = new AtomicReference<>();

        return defaultHapiSpec("NewAutoRenewAccountMustSign")
                .given(
                        newKeyNamed(adminKey),
                        newKeyNamed(narKey).shape(SECP256K1),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(newAutoRenewAccount)
                                .maxAutomaticTokenAssociations(2)
                                .key(narKey)
                                .exposingEvmAddressTo(newAutoRenewAliasAddr::set),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
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
                                                        idAsHeadlongAddress(asToken(idLit)))))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        MINIMAL_CREATIONS_CONTRACT,
                                                        "updateTokenWithNewAutoRenewInfo",
                                                        tokenMirrorAddr.get(),
                                                        newAutoRenewAliasAddr.get(),
                                                        THREE_MONTHS_IN_SECONDS + 3600)
                                                .via(updateTxn)
                                                .gas(10L * GAS_TO_OFFER)
                                                .sending(DEFAULT_AMOUNT_TO_SEND)
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
