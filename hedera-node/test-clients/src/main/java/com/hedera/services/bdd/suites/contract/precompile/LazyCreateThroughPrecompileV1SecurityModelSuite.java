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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getLiteralAliasAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmountAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nftTransferToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.transferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.headlongFromHexed;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.Utils.nCopiesOfSender;
import static com.hedera.services.bdd.suites.contract.Utils.nNonMirrorAddressFrom;
import static com.hedera.services.bdd.suites.contract.Utils.nonMirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class LazyCreateThroughPrecompileV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(LazyCreateThroughPrecompileV1SecurityModelSuite.class);
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String MULTI_KEY = "purpose";
    private static final String OWNER = "owner";
    private static final String FIRST = "FIRST";
    public static final ByteString FIRST_META = ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    public static final ByteString SECOND_META = ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    private static final String TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT = "PrecompileAliasXfer";
    private static final String SPENDER = "spender";
    private static final String TRANSFER_TOKEN_TXN = "transferTokenTxn";
    private static final String TRANSFER_TOKENS_TXN = "transferTokensTxn";
    private static final String TRANSFER_NFT_TXN = "transferNFTTxn";
    private static final String TRANSFER_NFTS_TXN = "transferNFTsTxn";
    private static final String SENDER = "sender";
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String NFT_TOKEN = "Token_NFT";
    private static final String TRANSFER_TXN = "transferTxn";
    private static final String TRANSFER_TXN2 = "transferTxn2";
    private static final String NFT_KEY = "nftKey";
    private static final String AUTO_CREATION_MODES = "AutoCreationModes";
    private static final String CREATION_ATTEMPT = "creationAttempt";
    private static final String ONE_TIME = "ONE TIME";
    private static final String CREATE_DIRECTLY = "createDirectly";
    private static final String HTS_TRANSFER_FROM_CONTRACT = "HtsTransferFrom";
    private static final ByteString META1 = ByteStringUtils.wrapUnsafely("meta1".getBytes());
    private static final ByteString META2 = ByteStringUtils.wrapUnsafely("meta2".getBytes());
    private static final String TOKEN_TREASURY = "treasury";
    private static final String HTS_TRANSFER_FROM = "htsTransferFrom";
    private static final String BASE_APPROVE_TXN = "baseApproveTxn";
    private static final String TRANSFER_TXN3 = "transferTxn3";
    private static final Tuple[] EMPTY_TUPLE_ARRAY = new Tuple[] {};
    private static final String ECDSA_KEY = "abcdECDSAkey";

    public static void main(String... args) {
        new LazyCreateThroughPrecompileV1SecurityModelSuite().runSuiteSync();
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
                cryptoTransferV1LazyCreate(),
                cryptoTransferV2LazyCreate(),
                transferTokenLazyCreate(),
                transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                transferNftLazyCreate(),
                transferNftsLazyCreate(),
                htsTransferFromFungibleTokenLazyCreate(),
                hollowAccountSigningReqsStillEnforced(),
                revertedAutoCreationRollsBackEvenIfTopLevelSucceeds(),
                canCreateMultipleHollows(),
                canCreateViaFungibleWithFractionalFee());
    }

    final Stream<DynamicTest> hollowAccountSigningReqsStillEnforced() {
        final var nft = "nft";
        final var nftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final var creationReversal = "creationReversal";
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("hollowAccountSigningReqsStillEnforced")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(nftKey),
                        uploadInitCode(AUTO_CREATION_MODES),
                        contractCreate(AUTO_CREATION_MODES),
                        cryptoCreate(CIVILIAN)
                                .keyShape(ED25519)
                                .exposingCreatedIdTo(id -> civilianId.set(id.getAccountNum())),
                        tokenCreate(nft)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(nftKey)
                                .initialSupply(0)
                                .treasury(CIVILIAN)
                                .exposingCreatedIdTo(
                                        idLit -> nftMirrorAddr.set(asHexedSolidityAddress(asToken(idLit)))),
                        mintToken(nft, List.of(ByteString.copyFromUtf8(ONE_TIME))))
                .when(sourcing(() -> contractCall(
                                AUTO_CREATION_MODES,
                                CREATE_DIRECTLY,
                                headlongFromHexed(nftMirrorAddr.get()),
                                mirrorAddrWith(civilianId.get()),
                                nonMirrorAddrWith(civilianId.get() + 4_000_000),
                                1L,
                                false)
                        .via(creationAttempt)
                        .gas(GAS_TO_OFFER)
                        .alsoSigningWithFullPrefix(CIVILIAN)))
                .then(
                        getTxnRecord(creationAttempt).andAllChildRecords().logged(),
                        sourcing(() -> getLiteralAliasAccountInfo(
                                        hex(Bytes.fromHexString(nonMirrorAddrWith(civilianId.get() + 4_000_000)
                                                        .toString())
                                                .toArray()))
                                .logged()),
                        // Now try to reverse the transfer and take the hollow account's NFT
                        sourcing(() -> contractCall(
                                        AUTO_CREATION_MODES,
                                        CREATE_DIRECTLY,
                                        headlongFromHexed(nftMirrorAddr.get()),
                                        nonMirrorAddrWith(civilianId.get() + 4_000_000),
                                        mirrorAddrWith(civilianId.get()),
                                        1L,
                                        false)
                                .via(creationReversal)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> childRecordsCheck(
                                creationReversal,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))));
    }

    final Stream<DynamicTest> revertedAutoCreationRollsBackEvenIfTopLevelSucceeds() {
        final var nft = "nft";
        final var nftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("revertedAutoCreationRollsBackEvenIfTopLevelSucceeds")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(nftKey),
                        uploadInitCode(AUTO_CREATION_MODES),
                        contractCreate(AUTO_CREATION_MODES),
                        cryptoCreate(CIVILIAN)
                                .keyShape(ED25519)
                                .exposingCreatedIdTo(id -> civilianId.set(id.getAccountNum())),
                        tokenCreate(nft)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(nftKey)
                                .initialSupply(0)
                                .treasury(CIVILIAN)
                                .exposingCreatedIdTo(
                                        idLit -> nftMirrorAddr.set(asHexedSolidityAddress(asToken(idLit)))),
                        mintToken(nft, List.of(ByteString.copyFromUtf8(ONE_TIME))))
                .when(sourcing(() -> contractCall(
                                AUTO_CREATION_MODES,
                                "createIndirectlyRevertingAndRecover",
                                headlongFromHexed(nftMirrorAddr.get()),
                                mirrorAddrWith(civilianId.get()),
                                nonMirrorAddrWith(civilianId.get() + 8_000_000),
                                1L)
                        .via(creationAttempt)
                        .gas(GAS_TO_OFFER)
                        .alsoSigningWithFullPrefix(CIVILIAN)
                        .hasKnownStatus(SUCCESS)))
                .then(childRecordsCheck(
                        creationAttempt,
                        SUCCESS,
                        recordWith()
                                .status(REVERTED_SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(
                                                htsPrecompileResult().withStatus(SUCCESS)))));
    }

    @SuppressWarnings("java:S5960")
    final Stream<DynamicTest> canCreateViaFungibleWithFractionalFee() {
        final var ft = "ft";
        final var ftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> ftMirrorAddr = new AtomicReference<>();
        final long supply = 100_000_000;

        return propertyPreservingHapiSpec("canCreateViaFungibleWithFractionalFee")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ftKey),
                        uploadInitCode(AUTO_CREATION_MODES),
                        contractCreate(AUTO_CREATION_MODES),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(CIVILIAN)
                                .maxAutomaticTokenAssociations(1)
                                .keyShape(ED25519)
                                .exposingCreatedIdTo(id -> civilianId.set(id.getAccountNum())),
                        // If running locally, ensures the entity 0.0.<civilianId + 1> is an account w/ EVM address
                        cryptoCreate("somebody").keyShape(SECP256K1_ON).withMatchingEvmAddress(),
                        tokenCreate(ft)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyKey(ftKey)
                                .initialSupply(supply)
                                .withCustom(fractionalFee(1L, 20L, 0L, OptionalLong.of(0L), TOKEN_TREASURY))
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> ftMirrorAddr.set(asHexedSolidityAddress(asToken(idLit)))),
                        cryptoTransfer(TokenMovement.moving(supply, ft).between(TOKEN_TREASURY, CIVILIAN)))
                .when(withOpContext((spec, opLog) -> {
                    final var op = contractCall(
                                    AUTO_CREATION_MODES,
                                    "createDirectlyViaFungible",
                                    headlongFromHexed(ftMirrorAddr.get()),
                                    mirrorAddrWith(civilianId.get()),
                                    nonMirrorAddrWith(123, civilianId.get() + 1),
                                    supply)
                            .via(creationAttempt)
                            .gas(GAS_TO_OFFER)
                            .alsoSigningWithFullPrefix(CIVILIAN)
                            .hasKnownStatusFrom(SUCCESS, CONTRACT_REVERT_EXECUTED);
                    allRunFor(spec, op);
                    // If this ContractCall was converted to an EthereumTransaction, then it will
                    // not be tracking the last receipt and we can't do this extra logging; this is
                    // fine for now, since the _Eth spec hasn't been flaky
                    if (op.hasActualStatus() && op.getActualStatus() == CONTRACT_REVERT_EXECUTED) {
                        final var lookup = getTxnRecord(creationAttempt).andAllChildRecords();
                        allRunFor(spec, lookup);
                        Assertions.fail("canCreateViaFungibleWithFractionalFee() failed w/ record "
                                + lookup.getResponseRecord()
                                + " and child records "
                                + lookup.getChildRecords());
                    }
                }))
                .then();
    }

    final Stream<DynamicTest> canCreateMultipleHollows() {
        final var n = 3;
        final var nft = "nft";
        final var nftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("canCreateMultipleHollows")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(nftKey),
                        uploadInitCode(AUTO_CREATION_MODES),
                        contractCreate(AUTO_CREATION_MODES),
                        cryptoCreate(CIVILIAN)
                                .keyShape(ED25519)
                                .exposingCreatedIdTo(id -> civilianId.set(id.getAccountNum())),
                        tokenCreate(nft)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(nftKey)
                                .initialSupply(0)
                                .treasury(CIVILIAN)
                                .exposingCreatedIdTo(
                                        idLit -> nftMirrorAddr.set(asHexedSolidityAddress(asToken(idLit)))),
                        mintToken(
                                nft,
                                IntStream.range(0, n)
                                        .mapToObj(i -> ByteString.copyFromUtf8(ONE_TIME + i))
                                        .toList()))
                .when(sourcing(() -> contractCall(
                                AUTO_CREATION_MODES,
                                "createSeveralDirectly",
                                headlongFromHexed(nftMirrorAddr.get()),
                                nCopiesOfSender(n, mirrorAddrWith(civilianId.get())),
                                nNonMirrorAddressFrom(n, civilianId.get() + 1_234_567_890L),
                                LongStream.iterate(1L, l -> l + 1).limit(n).toArray())
                        .via(creationAttempt)
                        .gas(GAS_TO_OFFER)
                        .alsoSigningWithFullPrefix(CIVILIAN)
                        .hasKnownStatus(SUCCESS)))
                .then(getTxnRecord(creationAttempt).andAllChildRecords().logged());
    }

    final Stream<DynamicTest> cryptoTransferV1LazyCreate() {
        final var NESTED_LAZY_PRECOMPILE_CONTRACT = "LazyPrecompileTransfers";
        final var FUNGIBLE_TOKEN_2 = "ftnt";
        return propertyPreservingHapiSpec("cryptoTransferV1LazyCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER)
                                .balance(10 * ONE_HUNDRED_HBARS)
                                .key(MULTI_KEY)
                                .maxAutomaticTokenAssociations(5),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN_2)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(SENDER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY),
                        mintToken(NFT_TOKEN, List.of(META1, META2)),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(moving(500, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(moving(500, FUNGIBLE_TOKEN_2).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(NESTED_LAZY_PRECOMPILE_CONTRACT),
                        contractCreate(NESTED_LAZY_PRECOMPILE_CONTRACT)
                                .gas(500_000L)
                                .maxAutomaticTokenAssociations(1),
                        getContractInfo(NESTED_LAZY_PRECOMPILE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
                                .logged())
                .when(withOpContext((spec, opLog) -> {
                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                    final var token2 = spec.registry().getTokenID(FUNGIBLE_TOKEN_2);
                    final var nftToken = spec.registry().getTokenID(NFT_TOKEN);
                    final var sender = spec.registry().getAccountID(SENDER);
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var evmAddressBytes = ByteString.copyFrom(recoverAddressFromPubKey(
                            ecdsaKey.getECDSASecp256K1().toByteArray()));
                    final var amountToBeSent = 50L;
                    final var transferFn = "cryptoTransferV1LazyCreate";
                    allRunFor(
                            spec,
                            contractCall(
                                            NESTED_LAZY_PRECOMPILE_CONTRACT,
                                            transferFn,
                                            tokenTransferLists()
                                                    .withTokenTransferList(
                                                            tokenTransferList()
                                                                    .forToken(token)
                                                                    .withAccountAmounts(
                                                                            accountAmount(sender, -amountToBeSent),
                                                                            accountAmountAlias(
                                                                                    recoverAddressFromPubKey(
                                                                                            ecdsaKey.getECDSASecp256K1()
                                                                                                    .toByteArray()),
                                                                                    amountToBeSent))
                                                                    .build(),
                                                            tokenTransferList()
                                                                    .forToken(nftToken)
                                                                    .withNftTransfers(nftTransferToAlias(
                                                                            sender,
                                                                            recoverAddressFromPubKey(
                                                                                    ecdsaKey.getECDSASecp256K1()
                                                                                            .toByteArray()),
                                                                            1L))
                                                                    .build())
                                                    .build(),
                                            tokenTransferLists()
                                                    .withTokenTransferList(tokenTransferList()
                                                            .forToken(token)
                                                            .withAccountAmounts(
                                                                    accountAmount(sender, -amountToBeSent),
                                                                    accountAmountAlias(
                                                                            recoverAddressFromPubKey(
                                                                                    ecdsaKey.getECDSASecp256K1()
                                                                                            .toByteArray()),
                                                                            amountToBeSent))
                                                            .build())
                                                    .build())
                                    .payingWith(GENESIS)
                                    .via(TRANSFER_TXN)
                                    .signedBy(GENESIS, MULTI_KEY)
                                    .alsoSigningWithFullPrefix(MULTI_KEY)
                                    .gas(GAS_TO_OFFER),
                            contractCall(
                                            NESTED_LAZY_PRECOMPILE_CONTRACT,
                                            transferFn,
                                            tokenTransferLists()
                                                    .withTokenTransferList(tokenTransferList()
                                                            .forToken(token)
                                                            .withAccountAmounts(
                                                                    accountAmount(sender, -1L),
                                                                    accountAmountAlias(
                                                                            recoverAddressFromPubKey(
                                                                                    ecdsaKey.getECDSASecp256K1()
                                                                                            .toByteArray()),
                                                                            1L))
                                                            .build())
                                                    .build(),
                                            tokenTransferLists()
                                                    .withTokenTransferList(tokenTransferList()
                                                            .forToken(token)
                                                            .withAccountAmounts(
                                                                    accountAmount(sender, -1L),
                                                                    accountAmountAlias(
                                                                            recoverAddressFromPubKey(
                                                                                    ecdsaKey.getECDSASecp256K1()
                                                                                            .toByteArray()),
                                                                            1L))
                                                            .build())
                                                    .build())
                                    .payingWith(GENESIS)
                                    .signedBy(GENESIS, MULTI_KEY)
                                    .alsoSigningWithFullPrefix(MULTI_KEY)
                                    .via(TRANSFER_TXN2)
                                    .gas(GAS_TO_OFFER),
                            contractCall(
                                            NESTED_LAZY_PRECOMPILE_CONTRACT,
                                            transferFn,
                                            tokenTransferLists()
                                                    .withTokenTransferList(tokenTransferList()
                                                            .forToken(token2)
                                                            .withAccountAmounts(
                                                                    accountAmount(sender, -1L),
                                                                    accountAmountAlias(
                                                                            recoverAddressFromPubKey(
                                                                                    ecdsaKey.getECDSASecp256K1()
                                                                                            .toByteArray()),
                                                                            1L))
                                                            .build())
                                                    .build(),
                                            tokenTransferLists()
                                                    .withTokenTransferList(tokenTransferList()
                                                            .forToken(token2)
                                                            .withAccountAmounts(
                                                                    accountAmount(sender, -1L),
                                                                    accountAmountAlias(
                                                                            recoverAddressFromPubKey(
                                                                                    ecdsaKey.getECDSASecp256K1()
                                                                                            .toByteArray()),
                                                                            1L))
                                                            .build())
                                                    .build())
                                    .payingWith(GENESIS)
                                    .signedBy(GENESIS, MULTI_KEY)
                                    .alsoSigningWithFullPrefix(MULTI_KEY)
                                    .via(TRANSFER_TXN3)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                    .gas(GAS_TO_OFFER),
                            childRecordsCheck(
                                    TRANSFER_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)),
                            childRecordsCheck(
                                    TRANSFER_TXN2,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)),
                            childRecordsCheck(
                                    TRANSFER_TXN3,
                                    CONTRACT_REVERT_EXECUTED,
                                    recordWith().status(NO_REMAINING_AUTOMATIC_ASSOCIATIONS)),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(evmAddressBytes)
                                    .hasTokenBalance(FUNGIBLE_TOKEN, amountToBeSent * 2 + 2)
                                    .hasTokenBalance(NFT_TOKEN, 1)
                                    .logged());
                }))
                .then();
    }

    final Stream<DynamicTest> cryptoTransferV2LazyCreate() {
        final var NESTED_LAZY_PRECOMPILE_CONTRACT = "LazyPrecompileTransfersAtomic";
        final var FUNGIBLE_TOKEN_2 = "ftnt";
        final var INIT_BALANCE = 10 * ONE_HUNDRED_HBARS;
        return propertyPreservingHapiSpec("cryptoTransferV2LazyCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER)
                                .balance(INIT_BALANCE)
                                .key(MULTI_KEY)
                                .maxAutomaticTokenAssociations(5),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN_2)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(SENDER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY),
                        mintToken(NFT_TOKEN, List.of(META1, META2)),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(moving(500, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(moving(500, FUNGIBLE_TOKEN_2).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(NESTED_LAZY_PRECOMPILE_CONTRACT),
                        contractCreate(NESTED_LAZY_PRECOMPILE_CONTRACT).maxAutomaticTokenAssociations(1),
                        getContractInfo(NESTED_LAZY_PRECOMPILE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
                                .logged())
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                    final var token2 = spec.registry().getTokenID(FUNGIBLE_TOKEN_2);
                    final var nftToken = spec.registry().getTokenID(NFT_TOKEN);
                    final var sender = spec.registry().getAccountID(SENDER);
                    final var amountToBeSent = 50L;

                    final var cryptoTransferV2LazyCreateFn = "cryptoTransferV2LazyCreate";
                    allRunFor(
                            spec,
                            contractCall(
                                            NESTED_LAZY_PRECOMPILE_CONTRACT,
                                            cryptoTransferV2LazyCreateFn,
                                            transferList()
                                                    .withAccountAmounts(
                                                            accountAmount(sender, -amountToBeSent, false),
                                                            UtilVerbs.accountAmountAlias(
                                                                    addressBytes, amountToBeSent, false))
                                                    .build(),
                                            tokenTransferLists()
                                                    .withTokenTransferList(
                                                            tokenTransferList()
                                                                    .forToken(token)
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    sender, -amountToBeSent, false),
                                                                            UtilVerbs.accountAmountAlias(
                                                                                    addressBytes,
                                                                                    amountToBeSent,
                                                                                    false))
                                                                    .build(),
                                                            tokenTransferList()
                                                                    .forToken(nftToken)
                                                                    .withNftTransfers(UtilVerbs.nftTransferToAlias(
                                                                            sender, addressBytes, 1L, false))
                                                                    .build())
                                                    .build(),
                                            transferList()
                                                    .withAccountAmounts(
                                                            accountAmount(sender, -amountToBeSent, false),
                                                            UtilVerbs.accountAmountAlias(
                                                                    addressBytes, amountToBeSent, false))
                                                    .build(),
                                            tokenTransferLists()
                                                    .withTokenTransferList(tokenTransferList()
                                                            .forToken(token)
                                                            .withAccountAmounts(
                                                                    accountAmount(sender, -amountToBeSent, false),
                                                                    UtilVerbs.accountAmountAlias(
                                                                            addressBytes, amountToBeSent, false))
                                                            .build())
                                                    .build())
                                    .payingWith(GENESIS)
                                    .via(TRANSFER_TXN)
                                    .signedBy(GENESIS, MULTI_KEY)
                                    .alsoSigningWithFullPrefix(MULTI_KEY)
                                    .gas(GAS_TO_OFFER),
                            contractCall(
                                            NESTED_LAZY_PRECOMPILE_CONTRACT,
                                            cryptoTransferV2LazyCreateFn,
                                            transferList()
                                                    .withAccountAmounts(
                                                            accountAmount(sender, -amountToBeSent, false),
                                                            UtilVerbs.accountAmountAlias(
                                                                    addressBytes, amountToBeSent, false))
                                                    .build(),
                                            EMPTY_TUPLE_ARRAY,
                                            transferList()
                                                    .withAccountAmounts(
                                                            accountAmount(sender, -amountToBeSent, false),
                                                            UtilVerbs.accountAmountAlias(
                                                                    addressBytes, amountToBeSent, false))
                                                    .build(),
                                            EMPTY_TUPLE_ARRAY)
                                    .payingWith(GENESIS)
                                    .signedBy(GENESIS, MULTI_KEY)
                                    .alsoSigningWithFullPrefix(MULTI_KEY)
                                    .via(TRANSFER_TXN2)
                                    .gas(GAS_TO_OFFER),
                            contractCall(
                                            NESTED_LAZY_PRECOMPILE_CONTRACT,
                                            cryptoTransferV2LazyCreateFn,
                                            transferList()
                                                    .withAccountAmounts(EMPTY_TUPLE_ARRAY)
                                                    .build(),
                                            tokenTransferLists()
                                                    .withTokenTransferList(tokenTransferList()
                                                            .forToken(token2)
                                                            .withAccountAmounts(
                                                                    accountAmount(sender, -amountToBeSent, false),
                                                                    UtilVerbs.accountAmountAlias(
                                                                            addressBytes, amountToBeSent, false))
                                                            .build())
                                                    .build(),
                                            transferList()
                                                    .withAccountAmounts(EMPTY_TUPLE_ARRAY)
                                                    .build(),
                                            EMPTY_TUPLE_ARRAY)
                                    .payingWith(GENESIS)
                                    .signedBy(GENESIS, MULTI_KEY)
                                    .alsoSigningWithFullPrefix(MULTI_KEY)
                                    .via(TRANSFER_TXN3)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                    .gas(GAS_TO_OFFER),
                            childRecordsCheck(
                                    TRANSFER_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)),
                            childRecordsCheck(
                                    TRANSFER_TXN2,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)),
                            childRecordsCheck(
                                    TRANSFER_TXN3,
                                    CONTRACT_REVERT_EXECUTED,
                                    recordWith().status(NO_REMAINING_AUTOMATIC_ASSOCIATIONS)),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(evmAddressBytes)
                                    .hasTinyBars(4 * amountToBeSent)
                                    .hasTokenBalance(FUNGIBLE_TOKEN, amountToBeSent * 2)
                                    .hasTokenBalance(NFT_TOKEN, 1)
                                    .logged());
                }))
                .then();
    }

    final Stream<DynamicTest> transferTokenLazyCreate() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("transferTokenLazyCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> tokenAddr.set(
                                        HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT).gas(500_000L),
                        tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    allRunFor(
                            spec,
                            contractCall(
                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                            "transferTokenCallNestedThenAgain",
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            2L,
                                            2L)
                                    .via(TRANSFER_TOKEN_TXN)
                                    .alsoSigningWithFullPrefix(OWNER)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(SUCCESS),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(alias)
                                    .hasTokenBalance(FUNGIBLE_TOKEN, 4)
                                    .logged(),
                            childRecordsCheck(
                                    TRANSFER_TOKEN_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)));
                }))
                .then();
    }

    final Stream<DynamicTest> transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> tokenAddr.set(
                                        HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT).gas(500_000L),
                        tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    assert addressBytes != null;
                    allRunFor(
                            spec,
                            contractCall(
                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                            "transferTokensCallNestedThenAgain",
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            new Address[] {
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(OWNER))),
                                                HapiParserUtil.asHeadlongAddress(addressBytes)
                                            },
                                            new long[] {-2L, 2L},
                                            new long[] {-2L, 2L})
                                    .via(TRANSFER_TOKENS_TXN)
                                    .gas(GAS_TO_OFFER)
                                    .alsoSigningWithFullPrefix(OWNER)
                                    .hasKnownStatus(SUCCESS),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(alias)
                                    .hasTokenBalance(FUNGIBLE_TOKEN, 4)
                                    .logged(),
                            childRecordsCheck(
                                    TRANSFER_TOKENS_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)));
                }))
                .then();
    }

    final Stream<DynamicTest> transferNftLazyCreate() {
        return propertyPreservingHapiSpec("transferNftLazyCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT).gas(500_000L),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    allRunFor(
                            spec,
                            contractCall(
                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                            "transferNFTCallNestedThenAgain",
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            1L,
                                            2L)
                                    .via(TRANSFER_NFT_TXN)
                                    .alsoSigningWithFullPrefix(OWNER)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(SUCCESS),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(alias)
                                    .hasTokenBalance(NON_FUNGIBLE_TOKEN, 2)
                                    .logged(),
                            childRecordsCheck(
                                    TRANSFER_NFT_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)));
                }))
                .then();
    }

    final Stream<DynamicTest> transferNftsLazyCreate() {
        return propertyPreservingHapiSpec("transferNftsLazyCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT).gas(500_000L),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    assert addressBytes != null;
                    allRunFor(
                            spec,
                            contractCall(
                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                            "transferNFTsCallNestedThenAgain",
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                            new Address[] {
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(OWNER)))
                                            },
                                            new Address[] {HapiParserUtil.asHeadlongAddress(addressBytes)},
                                            new long[] {1L},
                                            new long[] {2L})
                                    .via(TRANSFER_NFTS_TXN)
                                    .alsoSigningWithFullPrefix(OWNER)
                                    .gas(GAS_TO_OFFER),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(alias)
                                    .hasTokenBalance(NON_FUNGIBLE_TOKEN, 2)
                                    .logged(),
                            childRecordsCheck(
                                    TRANSFER_NFTS_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)));
                }))
                .then();
    }

    final Stream<DynamicTest> htsTransferFromFungibleTokenLazyCreate() {
        final var allowance = 10L;
        final var successfulTransferFromTxn = "txn";
        return propertyPreservingHapiSpec("htsTransferFromFungibleTokenLazyCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(5),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .supplyKey(MULTI_KEY)
                                .treasury(OWNER),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, HTS_TRANSFER_FROM_CONTRACT, allowance)
                                .via(BASE_APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .tokenAllowancesContaining(
                                                FUNGIBLE_TOKEN, HTS_TRANSFER_FROM_CONTRACT, allowance)))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final ByteString alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    allRunFor(
                            spec,
                            // transfer allowance/2 amount
                            contractCall(
                                            HTS_TRANSFER_FROM_CONTRACT,
                                            HTS_TRANSFER_FROM,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.valueOf(allowance / 2))
                                    .gas(GAS_TO_OFFER)
                                    .via(successfulTransferFromTxn)
                                    .hasKnownStatus(SUCCESS),
                            childRecordsCheck(
                                    successfulTransferFromTxn,
                                    SUCCESS,
                                    recordWith().status(SUCCESS).memo(LAZY_MEMO),
                                    recordWith()
                                            .status(SUCCESS)
                                            .contractCallResult(resultWith()
                                                    .contractCallResult(htsPrecompileResult()
                                                            .forFunction(FunctionType.HAPI_TRANSFER_FROM)
                                                            .withStatus(SUCCESS)))),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .logged()
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(alias)
                                    .hasTokenBalance(FUNGIBLE_TOKEN, allowance / 2)
                                    .logged());
                }))
                .then();
    }
}
