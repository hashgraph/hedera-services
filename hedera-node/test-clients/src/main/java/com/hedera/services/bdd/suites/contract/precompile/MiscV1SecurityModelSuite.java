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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.precompile.ContractKeysStillWorkAsExpectedSuite.B_WELL_KNOWN_SENDER;
import static com.hedera.services.bdd.suites.contract.precompile.ContractKeysStillWorkAsExpectedSuite.EVM_ALIAS_ENABLED_PROP;
import static com.hedera.services.bdd.suites.contract.precompile.ContractKeysStillWorkAsExpectedSuite.TOKEN_UNIT_FROM_TO_OTHERS_TXN;
import static com.hedera.services.bdd.suites.contract.precompile.ContractKeysStillWorkAsExpectedSuite.someWellKnownAssertions;
import static com.hedera.services.bdd.suites.contract.precompile.ContractKeysStillWorkAsExpectedSuite.someWellKnownOperationsWithAllNeededSigsInSigMap;
import static com.hedera.services.bdd.suites.contract.precompile.ContractKeysStillWorkAsExpectedSuite.someWellKnownTokensAndAccounts;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains miscellaneous {@link HapiSpec}s that can only work
 * with the legacy security model that permitted HTS system contracts to
 * access the top-level HAPI signatures under many conditions.
 *
 * <p>Before we can ever expect them to pass as {@link HapiTest}s, we must
 * refactor them so that everywhere they call {@code alsoSigningWithFullPrefix()},
 * instead they update the signing account's key to be a threshold key with
 * the calling contract's id.
 */
public class MiscV1SecurityModelSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MiscV1SecurityModelSuite.class);
    private static final String WELL_KNOWN_TREASURY_CONTRACT = "DoTokenManagement";

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                fallbackFeePayerMustSign(),
                fallbackFeeForHtsPayerMustSign(),
                contractCanStillTransferItsOwnAssets(),
                fixedFeeFailsWhenDisabledButWorksWhenEnabled(),
                topLevelSigsStillWorkWithDefaultGrandfatherNum());
    }

    private HapiSpec fallbackFeePayerMustSign() {
        final AtomicReference<Address> senderAddr = new AtomicReference<>();
        final AtomicReference<Address> receiverAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();

        final var treasury = "treasury";
        final var sender = "sender";
        final var receiver = "receiver";
        final var nft = "nft";
        final var failedCallTxn = "failedCallTxn";

        return propertyPreservingHapiSpec("FallbackFeePayerMustSign")
                .preserving(EVM_ALIAS_ENABLED_PROP)
                .given(
                        overriding(EVM_ALIAS_ENABLED_PROP, "true"),
                        uploadInitCode(WELL_KNOWN_TREASURY_CONTRACT),
                        contractCreate(WELL_KNOWN_TREASURY_CONTRACT),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(treasury),
                        cryptoCreate(sender).keyShape(SECP256K1_ON).exposingEvmAddressTo(senderAddr::set),
                        getAccountInfo(sender).logged(),
                        cryptoCreate(receiver)
                                .keyShape(SECP256K1_ON)
                                .balance(ONE_HBAR)
                                .exposingEvmAddressTo(receiverAddr::set))
                .when(
                        tokenCreate(nft)
                                .exposingAddressTo(nonFungibleTokenMirrorAddr::set)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHbarFeeInheritingRoyaltyCollector(ONE_HBAR), treasury))
                                .treasury(treasury),
                        getTokenInfo(nft).logged(),
                        tokenAssociate(sender, nft),
                        tokenAssociate(receiver, nft),
                        mintToken(
                                nft,
                                List.of(ByteString.copyFromUtf8("serialNo1"), ByteString.copyFromUtf8("serialNo2"))),
                        cryptoTransfer(movingUnique(nft, 1, 2).between(treasury, sender)))
                .then(
                        // Without the receiver signature, will fail with INVALID_SIGNATURE
                        overriding("contracts.precompile.unsupportedCustomFeeReceiverDebits", ""),
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferSerialNo1FromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        senderAddr.get(),
                                        receiverAddr.get())
                                .gas(2_000_000)
                                .via(failedCallTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(sender)),
                        childRecordsCheck(
                                failedCallTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        // And no hbar were deducted from the receiver
                        getAccountBalance(receiver).hasTinyBars(ONE_HBAR),
                        // override config to not allow royalty fee
                        overriding("contracts.precompile.unsupportedCustomFeeReceiverDebits", "ROYALTY_FALLBACK_FEE"),
                        // But now sign with receiver as well
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferSerialNo1FromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        senderAddr.get(),
                                        receiverAddr.get())
                                .gas(2_000_000)
                                .via("removeRoyaltyFee")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(sender, receiver)),
                        childRecordsCheck(
                                "removeRoyaltyFee",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(NOT_SUPPORTED)),
                        // And no hbar were deducted from the receiver
                        getAccountBalance(receiver).hasTinyBars(ONE_HBAR),
                        // But now sign with receiver as well and allow royalty fee
                        overriding("contracts.precompile.unsupportedCustomFeeReceiverDebits", ""),
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferSerialNo1FromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        senderAddr.get(),
                                        receiverAddr.get())
                                .gas(2_000_000)
                                .alsoSigningWithFullPrefix(sender, receiver)),
                        // Receiver balance will be debited ONE_HBAR
                        getAccountBalance(receiver).hasTinyBars(0L));
    }

    private HapiSpec contractCanStillTransferItsOwnAssets() {
        final AtomicReference<Address> fungibleTokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> aSenderAddr = new AtomicReference<>();
        final AtomicReference<Address> aReceiverAddr = new AtomicReference<>();
        final AtomicReference<Address> bSenderAddr = new AtomicReference<>();
        final AtomicReference<Address> bReceiverAddr = new AtomicReference<>();
        final AtomicReference<Address> treasuryContractAddr = new AtomicReference<>();

        return defaultHapiSpec("ContractCanStillTransferItsOwnAssets")
                .given(someWellKnownTokensAndAccounts(
                        fungibleTokenMirrorAddr,
                        nonFungibleTokenMirrorAddr,
                        aSenderAddr,
                        aReceiverAddr,
                        bSenderAddr,
                        bReceiverAddr,
                        false))
                .when(withOpContext((spec, opLog) -> {
                    final var treasuryId = spec.registry().getAccountID(WELL_KNOWN_TREASURY_CONTRACT);
                    treasuryContractAddr.set(idAsHeadlongAddress(treasuryId));
                }))
                .then(
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        TOKEN_UNIT_FROM_TO_OTHERS_TXN,
                                        fungibleTokenMirrorAddr.get(),
                                        treasuryContractAddr.get(),
                                        aReceiverAddr.get())
                                .gas(2_000_000)),
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferSerialNo1FromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        treasuryContractAddr.get(),
                                        aReceiverAddr.get())
                                .gas(2_000_000)),
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferNFTSerialNos2And3ToFromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        treasuryContractAddr.get(),
                                        aReceiverAddr.get(),
                                        bSenderAddr.get(),
                                        bReceiverAddr.get())
                                .gas(2_000_000)
                                .alsoSigningWithFullPrefix(B_WELL_KNOWN_SENDER)),
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferTokenUnitsFromToOthers",
                                        fungibleTokenMirrorAddr.get(),
                                        treasuryContractAddr.get(),
                                        aReceiverAddr.get(),
                                        bSenderAddr.get(),
                                        bReceiverAddr.get())
                                .gas(2_000_000)
                                .alsoSigningWithFullPrefix(B_WELL_KNOWN_SENDER)),
                        someWellKnownAssertions());
    }

    private HapiSpec fallbackFeeForHtsPayerMustSign() {
        final AtomicReference<Address> senderAddr = new AtomicReference<>();
        final AtomicReference<Address> receiverAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();

        final var treasury = "treasury";
        final var sender = "sender";
        final var receiver = "receiver";
        final var nft = "nft";
        final var fungible = "fungible";
        final var failedCallTxn = "failedCallTxn";

        return propertyPreservingHapiSpec("FallbackFeeHtsPayerMustSign")
                .preserving(EVM_ALIAS_ENABLED_PROP)
                .given(
                        overriding(EVM_ALIAS_ENABLED_PROP, "true"),
                        uploadInitCode(WELL_KNOWN_TREASURY_CONTRACT),
                        contractCreate(WELL_KNOWN_TREASURY_CONTRACT),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(treasury),
                        cryptoCreate(sender).keyShape(SECP256K1_ON).exposingEvmAddressTo(senderAddr::set),
                        getAccountInfo(sender).logged(),
                        cryptoCreate(receiver)
                                .keyShape(SECP256K1_ON)
                                .balance(ONE_HBAR)
                                .exposingEvmAddressTo(receiverAddr::set))
                .when(
                        tokenCreate(fungible)
                                .initialSupply(100)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(sender, fungible),
                        tokenAssociate(receiver, fungible),
                        cryptoTransfer(moving(10, fungible).between(TOKEN_TREASURY, receiver)),
                        tokenCreate(nft)
                                .exposingAddressTo(nonFungibleTokenMirrorAddr::set)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, fungible), treasury))
                                .treasury(treasury),
                        getTokenInfo(nft).logged(),
                        tokenAssociate(sender, nft),
                        tokenAssociate(receiver, nft),
                        mintToken(
                                nft,
                                List.of(ByteString.copyFromUtf8("serialNo1"), ByteString.copyFromUtf8("serialNo2"))),
                        cryptoTransfer(movingUnique(nft, 1, 2).between(treasury, sender)))
                .then(
                        // Without the receiver signature, will fail with INVALID_SIGNATURE
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferSerialNo1FromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        senderAddr.get(),
                                        receiverAddr.get())
                                .gas(2_000_000)
                                .via(failedCallTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(sender)),
                        childRecordsCheck(
                                failedCallTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        // And no tokens were deducted from the receiver
                        getAccountBalance(receiver).hasTokenBalance(fungible, 10),
                        // But now sign with receiver as well
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferSerialNo1FromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        senderAddr.get(),
                                        receiverAddr.get())
                                .gas(2_000_000)
                                .alsoSigningWithFullPrefix(sender, receiver)),
                        // Receiver token balance of custom fee denomination should debit by 1
                        getAccountBalance(receiver).hasTokenBalance(fungible, 9));
    }

    private HapiSpec topLevelSigsStillWorkWithDefaultGrandfatherNum() {
        final AtomicReference<Address> fungibleTokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> aSenderAddr = new AtomicReference<>();
        final AtomicReference<Address> aReceiverAddr = new AtomicReference<>();
        final AtomicReference<Address> bSenderAddr = new AtomicReference<>();
        final AtomicReference<Address> bReceiverAddr = new AtomicReference<>();

        return defaultHapiSpec("TopLevelSigsStillWorkWithDefaultGrandfatherNum")
                .given(
                        // Top-level signatures are available to any contract with 10M grandfather
                        // num
                        someWellKnownTokensAndAccounts(
                                fungibleTokenMirrorAddr,
                                nonFungibleTokenMirrorAddr,
                                aSenderAddr,
                                aReceiverAddr,
                                bSenderAddr,
                                bReceiverAddr,
                                true))
                .when()
                .then(
                        // Everything should have the needed approvals
                        someWellKnownOperationsWithAllNeededSigsInSigMap(
                                fungibleTokenMirrorAddr,
                                nonFungibleTokenMirrorAddr,
                                aSenderAddr,
                                aReceiverAddr,
                                bSenderAddr,
                                bReceiverAddr,
                                SUCCESS),
                        someWellKnownAssertions());
    }

    private HapiSpec fixedFeeFailsWhenDisabledButWorksWhenEnabled() {
        final AtomicReference<Address> senderAddr = new AtomicReference<>();
        final AtomicReference<Address> receiverAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();

        final var treasury = "treasury";
        final var sender = "sender";
        final var receiver = "receiver";
        final var nft = "nft";
        final var failedCallTxn = "failedCallTxn";

        return propertyPreservingHapiSpec("FixedFeeFailsWhenNotEnabled")
                .preserving(EVM_ALIAS_ENABLED_PROP)
                .given(
                        overriding(EVM_ALIAS_ENABLED_PROP, "true"),
                        uploadInitCode(WELL_KNOWN_TREASURY_CONTRACT),
                        contractCreate(WELL_KNOWN_TREASURY_CONTRACT),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(treasury),
                        cryptoCreate(sender).keyShape(SECP256K1_ON).exposingEvmAddressTo(senderAddr::set),
                        getAccountInfo(sender).logged(),
                        cryptoCreate(receiver)
                                .keyShape(SECP256K1_ON)
                                .balance(ONE_HBAR)
                                .exposingEvmAddressTo(receiverAddr::set))
                .when(
                        tokenCreate(nft)
                                .exposingAddressTo(nonFungibleTokenMirrorAddr::set)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .withCustom(fixedHbarFee(1, treasury, false))
                                .treasury(treasury),
                        getTokenInfo(nft).logged(),
                        tokenAssociate(sender, nft),
                        tokenAssociate(receiver, nft),
                        mintToken(
                                nft,
                                List.of(ByteString.copyFromUtf8("serialNo1"), ByteString.copyFromUtf8("serialNo2"))),
                        cryptoTransfer(movingUnique(nft, 1, 2).between(treasury, sender)))
                .then(
                        // override config to not allow fixed fees
                        overriding("contracts.precompile.unsupportedCustomFeeReceiverDebits", "FIXED_FEE"),
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferSerialNo1FromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        senderAddr.get(),
                                        receiverAddr.get())
                                .gas(2_000_000)
                                .via(failedCallTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(sender)),
                        childRecordsCheck(
                                failedCallTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(NOT_SUPPORTED)),
                        // And no hbar were deducted from the receiver
                        getAccountBalance(receiver).hasTinyBars(ONE_HBAR),
                        // override config to  allow fixed fees
                        overriding("contracts.precompile.unsupportedCustomFeeReceiverDebits", ""),
                        sourcing(() -> contractCall(
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        "transferSerialNo1FromToOthers",
                                        nonFungibleTokenMirrorAddr.get(),
                                        senderAddr.get(),
                                        receiverAddr.get())
                                .gas(2_000_000)
                                .via(failedCallTxn)
                                .hasKnownStatus(SUCCESS)
                                .alsoSigningWithFullPrefix(sender)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
