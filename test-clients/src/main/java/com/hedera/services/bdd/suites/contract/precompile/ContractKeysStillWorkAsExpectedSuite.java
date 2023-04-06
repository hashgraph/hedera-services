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
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.PAY_RECEIVABLE_CONTRACT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractKeysStillWorkAsExpectedSuite extends HapiSuite {
    private static final Logger log =
            LogManager.getLogger(ContractKeysStillWorkAsExpectedSuite.class);
    private static final String EVM_ALIAS_ENABLED_PROP =
            "cryptoCreateWithAliasAndEvmAddress.enabled";
    public static final String CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS =
            "contracts.maxNumWithHapiSigsAccess";
    private static final String TREASURY = "treasury";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";
    private static final String FAILED_CALL_TXN = "failedCallTxn";
    private static final String SERIAL_NO_1 = "serialNo1";
    private static final String SERIAL_NO_2 = "serialNo2";
    private static final String CONTRACTS_PRECOMPILE_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS =
            "contracts.precompile.unsupportedCustomFeeReceiverDebits";
    private static final String TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS =
            "transferSerialNo1FromToOthers";
    private static final String CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS =
            "contracts.allowSystemUseOfHapiSigs";
    private static final String TOKEN = "token";
    private static final String CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS =
            "contracts.withSpecialHapiSigsAccess";

    public static void main(String... args) {
        new ContractKeysStillWorkAsExpectedSuite().runSuiteSync();
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
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                contractKeysStillHaveSpecificityNoMatterTopLevelSignatures(),
                contractKeysWorkAsExpectedForFungibleTokenMgmt(),
                canStillTransferByVirtueOfContractIdInEOAThreshold(),
                approvalFallbacksRequiredWithoutTopLevelSigAccess(),
                topLevelSigsStillWorkWithDefaultGrandfatherNum(),
                contractCanStillTransferItsOwnAssets(),
                fallbackFeeForHtsPayerMustSign(),
                fallbackFeePayerMustSign(),
                fixedFeeFailsWhenDisabledButWorksWhenEnabled());
    }

    private HapiSpec fixedFeeFailsWhenDisabledButWorksWhenEnabled() {
        final AtomicReference<Address> senderAddr = new AtomicReference<>();
        final AtomicReference<Address> receiverAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();

        final var treasury = TREASURY;
        final var sender = SENDER;
        final var receiver = RECEIVER;
        final var nft = "nft";
        final var failedCallTxn = FAILED_CALL_TXN;

        return propertyPreservingHapiSpec("FixedFeeFailsWhenNotEnabled")
                .preserving(EVM_ALIAS_ENABLED_PROP)
                .given(
                        overriding(EVM_ALIAS_ENABLED_PROP, "true"),
                        uploadInitCode(WELL_KNOWN_TREASURY_CONTRACT),
                        contractCreate(WELL_KNOWN_TREASURY_CONTRACT),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(treasury),
                        cryptoCreate(sender)
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(senderAddr::set),
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
                                List.of(
                                        ByteString.copyFromUtf8(SERIAL_NO_1),
                                        ByteString.copyFromUtf8(SERIAL_NO_2))),
                        cryptoTransfer(movingUnique(nft, 1, 2).between(treasury, sender)))
                .then(
                        // override config to not allow fixed fees
                        overriding(
                                CONTRACTS_PRECOMPILE_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS,
                                "FIXED_FEE"),
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
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
                        overriding(CONTRACTS_PRECOMPILE_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS, ""),
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
                                                        nonFungibleTokenMirrorAddr.get(),
                                                        senderAddr.get(),
                                                        receiverAddr.get())
                                                .gas(2_000_000)
                                                .via(failedCallTxn)
                                                .hasKnownStatus(SUCCESS)
                                                .alsoSigningWithFullPrefix(sender)));
    }

    private HapiSpec fallbackFeePayerMustSign() {
        final AtomicReference<Address> senderAddr = new AtomicReference<>();
        final AtomicReference<Address> receiverAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();

        final var treasury = TREASURY;
        final var sender = SENDER;
        final var receiver = RECEIVER;
        final var nft = "nft";
        final var failedCallTxn = FAILED_CALL_TXN;

        return propertyPreservingHapiSpec("FallbackFeePayerMustSign")
                .preserving(EVM_ALIAS_ENABLED_PROP)
                .given(
                        overriding(EVM_ALIAS_ENABLED_PROP, "true"),
                        uploadInitCode(WELL_KNOWN_TREASURY_CONTRACT),
                        contractCreate(WELL_KNOWN_TREASURY_CONTRACT),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(treasury),
                        cryptoCreate(sender)
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(senderAddr::set),
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
                                .withCustom(
                                        royaltyFeeWithFallback(
                                                1,
                                                2,
                                                fixedHbarFeeInheritingRoyaltyCollector(ONE_HBAR),
                                                treasury))
                                .treasury(treasury),
                        getTokenInfo(nft).logged(),
                        tokenAssociate(sender, nft),
                        tokenAssociate(receiver, nft),
                        mintToken(
                                nft,
                                List.of(
                                        ByteString.copyFromUtf8(SERIAL_NO_1),
                                        ByteString.copyFromUtf8(SERIAL_NO_2))),
                        cryptoTransfer(movingUnique(nft, 1, 2).between(treasury, sender)))
                .then(
                        // Without the receiver signature, will fail with INVALID_SIGNATURE
                        overriding(CONTRACTS_PRECOMPILE_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS, ""),
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
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
                        overriding(
                                CONTRACTS_PRECOMPILE_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS,
                                "ROYALTY_FALLBACK_FEE"),
                        // But now sign with receiver as well
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
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
                        overriding(CONTRACTS_PRECOMPILE_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS, ""),
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
                                                        nonFungibleTokenMirrorAddr.get(),
                                                        senderAddr.get(),
                                                        receiverAddr.get())
                                                .gas(2_000_000)
                                                .alsoSigningWithFullPrefix(sender, receiver)),
                        // Receiver balance will be debited ONE_HBAR
                        getAccountBalance(receiver).hasTinyBars(0L));
    }

    private HapiSpec fallbackFeeForHtsPayerMustSign() {
        final AtomicReference<Address> senderAddr = new AtomicReference<>();
        final AtomicReference<Address> receiverAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();

        final var treasury = TREASURY;
        final var sender = SENDER;
        final var receiver = RECEIVER;
        final var nft = "nft";
        final var fungible = "fungible";
        final var failedCallTxn = FAILED_CALL_TXN;

        return propertyPreservingHapiSpec("FallbackFeeHtsPayerMustSign")
                .preserving(EVM_ALIAS_ENABLED_PROP)
                .given(
                        overriding(EVM_ALIAS_ENABLED_PROP, "true"),
                        uploadInitCode(WELL_KNOWN_TREASURY_CONTRACT),
                        contractCreate(WELL_KNOWN_TREASURY_CONTRACT),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(treasury),
                        cryptoCreate(sender)
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(senderAddr::set),
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
                                .withCustom(
                                        royaltyFeeWithFallback(
                                                1,
                                                2,
                                                fixedHtsFeeInheritingRoyaltyCollector(1, fungible),
                                                treasury))
                                .treasury(treasury),
                        getTokenInfo(nft).logged(),
                        tokenAssociate(sender, nft),
                        tokenAssociate(receiver, nft),
                        mintToken(
                                nft,
                                List.of(
                                        ByteString.copyFromUtf8(SERIAL_NO_1),
                                        ByteString.copyFromUtf8(SERIAL_NO_2))),
                        cryptoTransfer(movingUnique(nft, 1, 2).between(treasury, sender)))
                .then(
                        // Without the receiver signature, will fail with INVALID_SIGNATURE
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
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
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
                                                        nonFungibleTokenMirrorAddr.get(),
                                                        senderAddr.get(),
                                                        receiverAddr.get())
                                                .gas(2_000_000)
                                                .alsoSigningWithFullPrefix(sender, receiver)),
                        // Receiver token balance of custom fee denomination should debit by 1
                        getAccountBalance(receiver).hasTokenBalance(fungible, 9));
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
                .given(
                        someWellKnownTokensAndAccounts(
                                fungibleTokenMirrorAddr,
                                nonFungibleTokenMirrorAddr,
                                aSenderAddr,
                                aReceiverAddr,
                                bSenderAddr,
                                bReceiverAddr,
                                false))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var treasuryId =
                                            spec.registry()
                                                    .getAccountID(WELL_KNOWN_TREASURY_CONTRACT);
                                    treasuryContractAddr.set(idAsHeadlongAddress(treasuryId));
                                }))
                .then(
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        "transferTokenUnitFromToOthers",
                                                        fungibleTokenMirrorAddr.get(),
                                                        treasuryContractAddr.get(),
                                                        aReceiverAddr.get())
                                                .gas(2_000_000)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
                                                        nonFungibleTokenMirrorAddr.get(),
                                                        treasuryContractAddr.get(),
                                                        aReceiverAddr.get())
                                                .gas(2_000_000)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        WELL_KNOWN_TREASURY_CONTRACT,
                                                        "transferNFTSerialNos2And3ToFromToOthers",
                                                        nonFungibleTokenMirrorAddr.get(),
                                                        treasuryContractAddr.get(),
                                                        aReceiverAddr.get(),
                                                        bSenderAddr.get(),
                                                        bReceiverAddr.get())
                                                .gas(2_000_000)
                                                .alsoSigningWithFullPrefix(B_WELL_KNOWN_SENDER)),
                        sourcing(
                                () ->
                                        contractCall(
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

    private HapiSpec approvalFallbacksRequiredWithoutTopLevelSigAccess() {
        final AtomicReference<Address> fungibleTokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> nonFungibleTokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> aSenderAddr = new AtomicReference<>();
        final AtomicReference<Address> aReceiverAddr = new AtomicReference<>();
        final AtomicReference<Address> bSenderAddr = new AtomicReference<>();
        final AtomicReference<Address> bReceiverAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("ApprovalFallbacksRequiredWithoutTopLevelSigAccess")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        // No top-level signatures are available to any contract
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "0"),
                        someWellKnownTokensAndAccounts(
                                fungibleTokenMirrorAddr,
                                nonFungibleTokenMirrorAddr,
                                aSenderAddr,
                                aReceiverAddr,
                                bSenderAddr,
                                bReceiverAddr,
                                true))
                .when(
                        // Nothing works without approvals now
                        someWellKnownOperationsWithAllNeededSigsInSigMap(
                                fungibleTokenMirrorAddr,
                                nonFungibleTokenMirrorAddr,
                                aSenderAddr,
                                aReceiverAddr,
                                bSenderAddr,
                                bReceiverAddr,
                                CONTRACT_REVERT_EXECUTED),
                        // So grant all the approvals we need
                        cryptoApproveAllowance()
                                .payingWith(A_WELL_KNOWN_SENDER)
                                .addNftAllowance(
                                        A_WELL_KNOWN_SENDER,
                                        WELL_KNOWN_NON_FUNGIBLE_TOKEN,
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        false,
                                        List.of(1L, 2L))
                                .addTokenAllowance(
                                        A_WELL_KNOWN_SENDER,
                                        WELL_KNOWN_FUNGIBLE_TOKEN,
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        Long.MAX_VALUE)
                                .fee(ONE_HBAR),
                        cryptoApproveAllowance()
                                .payingWith(B_WELL_KNOWN_SENDER)
                                .addNftAllowance(
                                        B_WELL_KNOWN_SENDER,
                                        WELL_KNOWN_NON_FUNGIBLE_TOKEN,
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        false,
                                        List.of(3L))
                                .addTokenAllowance(
                                        B_WELL_KNOWN_SENDER,
                                        WELL_KNOWN_FUNGIBLE_TOKEN,
                                        WELL_KNOWN_TREASURY_CONTRACT,
                                        Long.MAX_VALUE)
                                .fee(ONE_HBAR))
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
                getTokenNftInfo(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 1L)
                        .hasAccountID(A_WELL_KNOWN_RECEIVER),
                getTokenNftInfo(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 2L)
                        .hasAccountID(A_WELL_KNOWN_RECEIVER),
                getTokenNftInfo(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 3L)
                        .hasAccountID(B_WELL_KNOWN_RECEIVER));
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
                cryptoCreate(A_WELL_KNOWN_SENDER)
                        .exposingCreatedIdTo(id -> aSenderAddr.set(idAsHeadlongAddress(id))),
                cryptoCreate(B_WELL_KNOWN_SENDER)
                        .exposingCreatedIdTo(id -> bSenderAddr.set(idAsHeadlongAddress(id))),
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
                tokenAssociate(
                        A_WELL_KNOWN_SENDER,
                        List.of(WELL_KNOWN_FUNGIBLE_TOKEN, WELL_KNOWN_NON_FUNGIBLE_TOKEN)),
                tokenAssociate(
                        B_WELL_KNOWN_SENDER,
                        List.of(WELL_KNOWN_FUNGIBLE_TOKEN, WELL_KNOWN_NON_FUNGIBLE_TOKEN)),
                tokenAssociate(
                        A_WELL_KNOWN_RECEIVER,
                        List.of(WELL_KNOWN_FUNGIBLE_TOKEN, WELL_KNOWN_NON_FUNGIBLE_TOKEN)),
                tokenAssociate(
                        B_WELL_KNOWN_RECEIVER,
                        List.of(WELL_KNOWN_FUNGIBLE_TOKEN, WELL_KNOWN_NON_FUNGIBLE_TOKEN)),
                transferToASender
                        ? cryptoTransfer(
                                TokenMovement.movingUnique(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 1L, 2L)
                                        .between(WELL_KNOWN_TREASURY_CONTRACT, A_WELL_KNOWN_SENDER))
                        : noOp(),
                cryptoTransfer(
                        TokenMovement.movingUnique(WELL_KNOWN_NON_FUNGIBLE_TOKEN, 3L)
                                .between(WELL_KNOWN_TREASURY_CONTRACT, B_WELL_KNOWN_SENDER)),
                transferToASender
                        ? cryptoTransfer(
                                TokenMovement.moving(1_000, WELL_KNOWN_FUNGIBLE_TOKEN)
                                        .between(WELL_KNOWN_TREASURY_CONTRACT, A_WELL_KNOWN_SENDER))
                        : noOp(),
                cryptoTransfer(
                        TokenMovement.moving(1_000, WELL_KNOWN_FUNGIBLE_TOKEN)
                                .between(WELL_KNOWN_TREASURY_CONTRACT, B_WELL_KNOWN_SENDER)),
                contractUpdate(WELL_KNOWN_TREASURY_CONTRACT).properlyEmptyingAdminKey());
    }

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
                sourcing(
                        () ->
                                contractCall(
                                                WELL_KNOWN_TREASURY_CONTRACT,
                                                "transferTokenUnitFromToOthers",
                                                fungibleTokenMirrorAddr.get(),
                                                aSenderAddr.get(),
                                                aReceiverAddr.get())
                                        .gas(2_000_000)
                                        .alsoSigningWithFullPrefix(
                                                A_WELL_KNOWN_SENDER, B_WELL_KNOWN_SENDER)
                                        .hasKnownStatus(expectedStatus)),
                sourcing(
                        () ->
                                contractCall(
                                                WELL_KNOWN_TREASURY_CONTRACT,
                                                TRANSFER_SERIAL_NO_1_FROM_TO_OTHERS,
                                                nonFungibleTokenMirrorAddr.get(),
                                                aSenderAddr.get(),
                                                aReceiverAddr.get())
                                        .gas(2_000_000)
                                        .alsoSigningWithFullPrefix(
                                                A_WELL_KNOWN_SENDER, B_WELL_KNOWN_SENDER)
                                        .hasKnownStatus(expectedStatus)),
                sourcing(
                        () ->
                                contractCall(
                                                WELL_KNOWN_TREASURY_CONTRACT,
                                                "transferNFTSerialNos2And3ToFromToOthers",
                                                nonFungibleTokenMirrorAddr.get(),
                                                aSenderAddr.get(),
                                                aReceiverAddr.get(),
                                                bSenderAddr.get(),
                                                bReceiverAddr.get())
                                        .gas(2_000_000)
                                        .alsoSigningWithFullPrefix(
                                                A_WELL_KNOWN_SENDER, B_WELL_KNOWN_SENDER)
                                        .hasKnownStatus(expectedStatus)),
                sourcing(
                        () ->
                                contractCall(
                                                WELL_KNOWN_TREASURY_CONTRACT,
                                                "transferTokenUnitsFromToOthers",
                                                fungibleTokenMirrorAddr.get(),
                                                aSenderAddr.get(),
                                                aReceiverAddr.get(),
                                                bSenderAddr.get(),
                                                bReceiverAddr.get())
                                        .gas(2_000_000)
                                        .alsoSigningWithFullPrefix(
                                                A_WELL_KNOWN_SENDER, B_WELL_KNOWN_SENDER)
                                        .hasKnownStatus(expectedStatus)));
    }

    private HapiSpec canStillTransferByVirtueOfContractIdInEOAThreshold() {
        final var fungibleToken = TOKEN;
        final var managementContract = WELL_KNOWN_TREASURY_CONTRACT;
        final AtomicReference<Address> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> controlledSpenderAddr = new AtomicReference<>();
        final AtomicReference<Address> receiverAddr = new AtomicReference<>();
        final var threshKeyShape = KeyShape.threshOf(1, CONTRACT, SECP256K1);
        final var controlledSpender = "controlledSpender";
        final var receiver = RECEIVER;
        final var controlledSpenderKey = "controlledSpenderKey";

        return propertyPreservingHapiSpec("CanStillTransferByVirtueOfContractIdInEOAThreshold")
                .preserving(
                        CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                        CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS,
                        CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        overridingThree(
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                "0",
                                CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS,
                                "",
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                ""),
                        uploadInitCode(managementContract),
                        // Create an immutable contract with a method
                        // transferViaThresholdContractKey()
                        // that tries to transfer token units from a spender to a receiver
                        contractCreate(managementContract).omitAdminKey(),
                        // Setup a 1/2 threshold key with this contract's ID as the first key
                        newKeyNamed(controlledSpenderKey)
                                .shape(threshKeyShape.signedWith(sigs(managementContract, ON))),
                        // Assign this key to an account
                        cryptoCreate(controlledSpender)
                                .key(controlledSpenderKey)
                                .exposingCreatedIdTo(
                                        id -> controlledSpenderAddr.set(idAsHeadlongAddress(id))),
                        // Make this account the treasury of a fungible token
                        tokenCreate(fungibleToken)
                                .exposingAddressTo(tokenMirrorAddr::set)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(1_000_000)
                                .treasury(controlledSpender),
                        // Create a receiver
                        cryptoCreate(receiver)
                                .maxAutomaticTokenAssociations(1)
                                .exposingCreatedIdTo(
                                        id -> receiverAddr.set(idAsHeadlongAddress(id))))
                .when(
                        // And now transfer from the controlled spender (treasury with 1M balance)
                        // without its signature, by virtue of being in the threshold key
                        sourcing(
                                () ->
                                        contractCall(
                                                        managementContract,
                                                        "transferViaThresholdContractKey",
                                                        tokenMirrorAddr.get(),
                                                        controlledSpenderAddr.get(),
                                                        receiverAddr.get())
                                                .gas(2_000_000)
                                                .via("txn")))
                .then(
                        // Validate the receiver really did get a unit
                        getAccountBalance(receiver).logged().hasTokenBalance(fungibleToken, 1));
    }

    private HapiSpec contractKeysWorkAsExpectedForFungibleTokenMgmt() {
        final var fungibleToken = TOKEN;
        final var managementContract = WELL_KNOWN_TREASURY_CONTRACT;
        final var mgmtContractAsKey = "mgmtContractAsKey";
        final var tmpAdminKey = "tmpAdminKey";
        final var associatedAccount = "associatedAccount";
        final AtomicReference<Address> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> accountAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("ContractKeysWorkAsExpectedForFungibleTokenMgmt")
                .preserving(
                        CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                        CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS,
                        CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                        EVM_ALIAS_ENABLED_PROP)
                .given(
                        overridingAllOf(
                                Map.of(
                                        CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                        "0",
                                        CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS,
                                        "",
                                        CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                        "",
                                        EVM_ALIAS_ENABLED_PROP,
                                        "true")),
                        uploadInitCode(managementContract),
                        newKeyNamed(tmpAdminKey),
                        contractCreate(managementContract).adminKey(tmpAdminKey),
                        newKeyNamed(mgmtContractAsKey)
                                .shape(CONTRACT.signedWith(managementContract)),
                        cryptoCreate(associatedAccount)
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(accountAddr::set),
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
                        sourcing(
                                () ->
                                        contractCall(
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

    private HapiSpec contractKeysStillHaveSpecificityNoMatterTopLevelSignatures() {
        final var fungibleToken = TOKEN;
        final var managementContract = WELL_KNOWN_TREASURY_CONTRACT;
        final var otherContractAsKey = "otherContractAsKey";
        final var tmpAdminKey = "tmpAdminKey";
        final var associatedAccount = "associatedAccount";
        final AtomicReference<Address> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> accountAddr = new AtomicReference<>();

        return defaultHapiSpec("ContractKeysStillHaveSpecificityNoMatterTopLevelSignatures")
                .given(
                        uploadInitCode(managementContract, PAY_RECEIVABLE_CONTRACT),
                        newKeyNamed(tmpAdminKey),
                        contractCreate(managementContract).adminKey(tmpAdminKey),
                        // Just create some other contract to be the real admin key
                        contractCreate(PAY_RECEIVABLE_CONTRACT),
                        newKeyNamed(otherContractAsKey)
                                .shape(CONTRACT.signedWith(PAY_RECEIVABLE_CONTRACT)),
                        cryptoCreate(associatedAccount)
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(accountAddr::set),
                        tokenCreate(fungibleToken)
                                .exposingAddressTo(tokenMirrorAddr::set)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(1_000_000)
                                .treasury(managementContract)
                                .supplyKey(otherContractAsKey)
                                .wipeKey(otherContractAsKey)
                                .kycKey(otherContractAsKey)
                                .pauseKey(otherContractAsKey)
                                .freezeKey(otherContractAsKey),
                        tokenAssociate(associatedAccount, fungibleToken),
                        contractUpdate(managementContract).properlyEmptyingAdminKey())
                .when(
                        // Confirm the contract is really immutable
                        getContractInfo(managementContract)
                                .has(contractWith().immutableContractKey(managementContract)))
                .then(
                        // And now test a bunch of management functions are not authorized by
                        // the management contract's ContractID key under these conditions;
                        // even when it is the token treasury, and 0.0.2 has a top-level signature
                        sourcing(
                                () ->
                                        contractCall(
                                                        managementContract,
                                                        "justBurnFungible",
                                                        tokenMirrorAddr.get())
                                                .gas(15_000_000L)
                                                .via("burnTxn")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        childRecordsCheck(
                                "burnTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        managementContract,
                                                        "justFreezeAccount",
                                                        tokenMirrorAddr.get(),
                                                        accountAddr.get())
                                                .gas(15_000_000L)
                                                .via("freezeTxn")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        childRecordsCheck(
                                "freezeTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        managementContract,
                                                        "justGrantKyc",
                                                        tokenMirrorAddr.get(),
                                                        accountAddr.get())
                                                .gas(15_000_000L)
                                                .via("grantTxn")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        childRecordsCheck(
                                "grantTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        managementContract,
                                                        "justRevokeKyc",
                                                        tokenMirrorAddr.get(),
                                                        accountAddr.get())
                                                .gas(15_000_000L)
                                                .via("revokeTxn")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        childRecordsCheck(
                                "revokeTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)),
                        sourcing(
                                () ->
                                        contractCall(
                                                        managementContract,
                                                        "justWipeFungible",
                                                        tokenMirrorAddr.get(),
                                                        accountAddr.get())
                                                .gas(15_000_000L)
                                                .via("wipeTxn")
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        childRecordsCheck(
                                "wipeTxn",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)));
    }
}
