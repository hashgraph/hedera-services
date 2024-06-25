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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class ContractKeysStillWorkAsExpectedV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ContractKeysStillWorkAsExpectedV1SecurityModelSuite.class);
    private static final String EVM_ALIAS_ENABLED_PROP = "cryptoCreateWithAliasAndEvmAddress.enabled";

    public static void main(String... args) {
        new ContractKeysStillWorkAsExpectedV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                contractKeysWorkAsExpectedForFungibleTokenMgmt(),
                topLevelSigsStillWorkWithDefaultGrandfatherNum(),
                contractCanStillTransferItsOwnAssets(),
                fallbackFeeForHtsPayerMustSign(),
                fallbackFeePayerMustSign(),
                fixedFeeFailsWhenDisabledButWorksWhenEnabled());
    }

    final Stream<DynamicTest> fixedFeeFailsWhenDisabledButWorksWhenEnabled() {
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

    final Stream<DynamicTest> fallbackFeePayerMustSign() {
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

    final Stream<DynamicTest> fallbackFeeForHtsPayerMustSign() {
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

    final Stream<DynamicTest> contractCanStillTransferItsOwnAssets() {
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

    final Stream<DynamicTest> topLevelSigsStillWorkWithDefaultGrandfatherNum() {
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

    private static final String A_WELL_KNOWN_SENDER = "A_SENDER";
    private static final String B_WELL_KNOWN_SENDER = "B_SENDER";
    private static final String A_WELL_KNOWN_RECEIVER = "A_RECEIVER";
    private static final String B_WELL_KNOWN_RECEIVER = "B_RECEIVER";
    private static final String WELL_KNOWN_FUNGIBLE_TOKEN = "FT";
    private static final String WELL_KNOWN_NON_FUNGIBLE_TOKEN = "NFT";
    private static final String WELL_KNOWN_TREASURY_CONTRACT = "DoTokenManagement";

    private HapiSpecOperation someWellKnownAssertions() {
        return blockingOrder(
                getAccountBalance(A_WELL_KNOWN_RECEIVER)
                        .hasTokenBalance(WELL_KNOWN_FUNGIBLE_TOKEN, 2L)
                        .hasTokenBalance(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 2L),
                getAccountBalance(B_WELL_KNOWN_RECEIVER)
                        .hasTokenBalance(WELL_KNOWN_FUNGIBLE_TOKEN, 1L)
                        .hasTokenBalance(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 1L),
                getTokenNftInfo(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 1L).hasAccountID(A_WELL_KNOWN_RECEIVER),
                getTokenNftInfo(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 2L).hasAccountID(A_WELL_KNOWN_RECEIVER),
                getTokenNftInfo(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 3L).hasAccountID(B_WELL_KNOWN_RECEIVER));
    }

    /**
     * Returns a multi-step operation that creates a fungible and non-fungible token; a contract
     * that serves as both their treasuries; and some accounts associated to both tokens, where the
     * sender accounts have fungible units and serial numbers 1 and 2, respectively.
     */
    private HapiSpecOperation someWellKnownTokensAndAccounts(
            final AtomicReference<Address> fungibleTokenMirrorAddr,
            final AtomicReference<Address> nonFungibleTokenMirrorAddr,
            final AtomicReference<Address> aSenderAddr,
            final AtomicReference<Address> aReceiverAddr,
            final AtomicReference<Address> bSenderAddr,
            final AtomicReference<Address> bReceiverAddr,
            final boolean transferToASender) {
        return blockingOrder(
                newKeyNamed(MULTI_KEY),
                cryptoCreate(A_WELL_KNOWN_SENDER).exposingCreatedIdTo(id -> aSenderAddr.set(idAsHeadlongAddress(id))),
                cryptoCreate(B_WELL_KNOWN_SENDER).exposingCreatedIdTo(id -> bSenderAddr.set(idAsHeadlongAddress(id))),
                cryptoCreate(A_WELL_KNOWN_RECEIVER)
                        .exposingCreatedIdTo(id -> aReceiverAddr.set(idAsHeadlongAddress(id))),
                cryptoCreate(B_WELL_KNOWN_RECEIVER)
                        .exposingCreatedIdTo(id -> bReceiverAddr.set(idAsHeadlongAddress(id))),
                uploadInitCode(WELL_KNOWN_TREASURY_CONTRACT),
                contractCreate(WELL_KNOWN_TREASURY_CONTRACT),
                tokenCreate(WELL_KNOWN_FUNGIBLE_TOKEN)
                        .exposingAddressTo(fungibleTokenMirrorAddr::set)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(1_000_000)
                        .treasury(WELL_KNOWN_TREASURY_CONTRACT),
                tokenCreate(WELL_KNOWN_NON_FUNGIBLE_TOKEN)
                        .exposingAddressTo(nonFungibleTokenMirrorAddr::set)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0)
                        .supplyKey(MULTI_KEY)
                        .treasury(WELL_KNOWN_TREASURY_CONTRACT),
                mintToken(
                        WELL_KNOWN_NON_FUNGIBLE_TOKEN,
                        List.of(
                                ByteString.copyFromUtf8("A"),
                                ByteString.copyFromUtf8("B"),
                                ByteString.copyFromUtf8("C"))),
                tokenAssociate(A_WELL_KNOWN_SENDER, List.of(WELL_KNOWN_FUNGIBLE_TOKEN, WELL_KNOWN_NON_FUNGIBLE_TOKEN)),
                tokenAssociate(B_WELL_KNOWN_SENDER, List.of(WELL_KNOWN_FUNGIBLE_TOKEN, WELL_KNOWN_NON_FUNGIBLE_TOKEN)),
                tokenAssociate(
                        A_WELL_KNOWN_RECEIVER, List.of(WELL_KNOWN_FUNGIBLE_TOKEN, WELL_KNOWN_NON_FUNGIBLE_TOKEN)),
                tokenAssociate(
                        B_WELL_KNOWN_RECEIVER, List.of(WELL_KNOWN_FUNGIBLE_TOKEN, WELL_KNOWN_NON_FUNGIBLE_TOKEN)),
                transferToASender
                        ? cryptoTransfer(TokenMovement.movingUnique(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 1L, 2L)
                                .between(WELL_KNOWN_TREASURY_CONTRACT, A_WELL_KNOWN_SENDER))
                        : noOp(),
                cryptoTransfer(TokenMovement.movingUnique(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 3L)
                        .between(WELL_KNOWN_TREASURY_CONTRACT, B_WELL_KNOWN_SENDER)),
                transferToASender
                        ? cryptoTransfer(TokenMovement.moving(1_000, WELL_KNOWN_FUNGIBLE_TOKEN)
                                .between(WELL_KNOWN_TREASURY_CONTRACT, A_WELL_KNOWN_SENDER))
                        : noOp(),
                cryptoTransfer(TokenMovement.moving(1_000, WELL_KNOWN_FUNGIBLE_TOKEN)
                        .between(WELL_KNOWN_TREASURY_CONTRACT, B_WELL_KNOWN_SENDER)),
                contractUpdate(WELL_KNOWN_TREASURY_CONTRACT).properlyEmptyingAdminKey());
    }

    private static final String TOKEN_UNIT_FROM_TO_OTHERS_TXN = "transferTokenUnitFromToOthers";

    /**
     * Returns a multi-step operation that does one of each of the {@code transferToken}, {@code
     * transferTokens}, {@code transferNFT}, {@code transferNFTs} with the given expected status.
     * Every operation adds all the needed signatures to the sigMap using full-prefix signatures.
     */
    private HapiSpecOperation someWellKnownOperationsWithAllNeededSigsInSigMap(
            final AtomicReference<Address> fungibleTokenMirrorAddr,
            final AtomicReference<Address> nonFungibleTokenMirrorAddr,
            final AtomicReference<Address> aSenderAddr,
            final AtomicReference<Address> aReceiverAddr,
            final AtomicReference<Address> bSenderAddr,
            final AtomicReference<Address> bReceiverAddr,
            final ResponseCodeEnum expectedStatus) {
        return blockingOrder(
                sourcing(() -> contractCall(
                                WELL_KNOWN_TREASURY_CONTRACT,
                                TOKEN_UNIT_FROM_TO_OTHERS_TXN,
                                fungibleTokenMirrorAddr.get(),
                                aSenderAddr.get(),
                                aReceiverAddr.get())
                        .gas(2_000_000)
                        .alsoSigningWithFullPrefix(A_WELL_KNOWN_SENDER, B_WELL_KNOWN_SENDER)
                        .via(TOKEN_UNIT_FROM_TO_OTHERS_TXN)
                        .hasKnownStatus(expectedStatus)),
                sourcing(() -> contractCall(
                                WELL_KNOWN_TREASURY_CONTRACT,
                                "transferSerialNo1FromToOthers",
                                nonFungibleTokenMirrorAddr.get(),
                                aSenderAddr.get(),
                                aReceiverAddr.get())
                        .gas(2_000_000)
                        .alsoSigningWithFullPrefix(A_WELL_KNOWN_SENDER, B_WELL_KNOWN_SENDER)
                        .hasKnownStatus(expectedStatus)),
                sourcing(() -> contractCall(
                                WELL_KNOWN_TREASURY_CONTRACT,
                                "transferNFTSerialNos2And3ToFromToOthers",
                                nonFungibleTokenMirrorAddr.get(),
                                aSenderAddr.get(),
                                aReceiverAddr.get(),
                                bSenderAddr.get(),
                                bReceiverAddr.get())
                        .gas(2_000_000)
                        .alsoSigningWithFullPrefix(A_WELL_KNOWN_SENDER, B_WELL_KNOWN_SENDER)
                        .hasKnownStatus(expectedStatus)),
                sourcing(() -> contractCall(
                                WELL_KNOWN_TREASURY_CONTRACT,
                                "transferTokenUnitsFromToOthers",
                                fungibleTokenMirrorAddr.get(),
                                aSenderAddr.get(),
                                aReceiverAddr.get(),
                                bSenderAddr.get(),
                                bReceiverAddr.get())
                        .gas(2_000_000)
                        .alsoSigningWithFullPrefix(A_WELL_KNOWN_SENDER, B_WELL_KNOWN_SENDER)
                        .hasKnownStatus(expectedStatus)));
    }

    final Stream<DynamicTest> contractKeysWorkAsExpectedForFungibleTokenMgmt() {
        final var fungibleToken = "token";
        final var managementContract = "DoTokenManagement";
        final var mgmtContractAsKey = "mgmtContractAsKey";
        final var tmpAdminKey = "tmpAdminKey";
        final var associatedAccount = "associatedAccount";
        final AtomicReference<Address> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> accountAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("ContractKeysWorkAsExpectedForFungibleTokenMgmt")
                .preserving(
                        CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                        "contracts.withSpecialHapiSigsAccess",
                        "contracts.allowSystemUseOfHapiSigs",
                        EVM_ALIAS_ENABLED_PROP)
                .given(
                        overridingAllOf(Map.of(
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                "0",
                                "contracts.withSpecialHapiSigsAccess",
                                "",
                                "contracts.allowSystemUseOfHapiSigs",
                                "",
                                EVM_ALIAS_ENABLED_PROP,
                                "true")),
                        uploadInitCode(managementContract),
                        newKeyNamed(tmpAdminKey),
                        contractCreate(managementContract).adminKey(tmpAdminKey),
                        newKeyNamed(mgmtContractAsKey).shape(CONTRACT.signedWith(managementContract)),
                        cryptoCreate(associatedAccount).keyShape(SECP256K1_ON).exposingEvmAddressTo(accountAddr::set),
                        tokenCreate(fungibleToken)
                                .exposingAddressTo(tokenMirrorAddr::set)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(1_000_000)
                                .treasury(managementContract)
                                .supplyKey(mgmtContractAsKey)
                                .wipeKey(mgmtContractAsKey)
                                .kycKey(mgmtContractAsKey)
                                .pauseKey(mgmtContractAsKey)
                                .freezeKey(mgmtContractAsKey),
                        tokenAssociate(associatedAccount, fungibleToken),
                        contractUpdate(managementContract).properlyEmptyingAdminKey())
                .when(
                        // Confirm the contract is really immutable
                        getContractInfo(managementContract)
                                .has(contractWith().immutableContractKey(managementContract)))
                .then(
                        // And now test a bunch of management functions are still authorized by
                        // ContractID key under these conditions
                        sourcing(() -> contractCall(
                                        managementContract,
                                        "manageEverythingForFungible",
                                        tokenMirrorAddr.get(),
                                        accountAddr.get())
                                .gas(15_000_000L)
                                .via("txn")),
                        getTxnRecord("txn")
                                .hasNonStakingChildRecordCount(10)
                                .andAllChildRecords()
                                .logged());
    }
}
