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
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getLiteralAliasAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
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
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ifHapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ifNotHapiTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.ALLOW_SKIPPED_ENTITY_IDS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.FULLY_NONDETERMINISTIC;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_NONCE;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.headlongFromHexed;
import static com.hedera.services.bdd.suites.contract.Utils.mirrorAddrWith;
import static com.hedera.services.bdd.suites.contract.Utils.nCopiesOfSender;
import static com.hedera.services.bdd.suites.contract.Utils.nNonMirrorAddressFrom;
import static com.hedera.services.bdd.suites.contract.Utils.nonMirrorAddrWith;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
public class LazyCreateThroughPrecompileSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(LazyCreateThroughPrecompileSuite.class);
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String MULTI_KEY = "purpose";
    private static final String OWNER = "owner";
    private static final String FIRST = "FIRST";
    public static final ByteString FIRST_META = ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    public static final ByteString SECOND_META = ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    private static final String SPENDER = "spender";
    private static final String NFT_TOKEN = "Token_NFT";
    private static final String TRANSFER_TXN = "transferTxn";
    private static final String NFT_KEY = "nftKey";
    private static final String AUTO_CREATION_MODES = "AutoCreationModes";
    private static final String CREATION_ATTEMPT = "creationAttempt";
    private static final String ONE_TIME = "ONE TIME";
    private static final String CREATE_DIRECTLY = "createDirectly";
    private static final String ERC_721_CONTRACT = "ERC721Contract";
    private static final String TRANSFER_THEN_REVERT = "transferThenRevert";
    private static final String TRANSFER_FROM_THEN_REVERT = "transferFromThenRevert";
    private static final String TRANSFER_THEN_REVERT_TXN = "transferThenRevertTxn";
    private static final String TRANSFER_FROM_ACCOUNT_TXN = "transferFromAccountTxn";
    private static final String TRANSFER_FROM_ACCOUNT_REVERT_TXN = "transferFromAccountRevertTxn";
    private static final String TRANSFER = "transfer";
    private static final String TRANSFER_FROM = "transferFrom";
    private static final String ERC_20_CONTRACT = "ERC20Contract";
    private static final String HTS_TRANSFER_FROM_CONTRACT = "HtsTransferFrom";
    private static final ByteString META1 = ByteStringUtils.wrapUnsafely("meta1".getBytes());
    private static final ByteString META2 = ByteStringUtils.wrapUnsafely("meta2".getBytes());
    private static final String TOKEN_TREASURY = "treasury";
    private static final String HTS_TRANSFER_FROM = "htsTransferFrom";
    private static final String HTS_TRANSFER_FROM_NFT = "htsTransferFromNFT";
    private static final String BASE_APPROVE_TXN = "baseApproveTxn";
    private static final String RECIPIENT = "recipient";
    private static final String NOT_ENOUGH_GAS_TXN = "NOT_ENOUGH_GAS_TXN";
    private static final String ECDSA_KEY = "abcdECDSAkey";

    public static void main(String... args) {
        new LazyCreateThroughPrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                erc20TransferLazyCreate(),
                erc20TransferFromLazyCreate(),
                erc721TransferFromLazyCreate(),
                htsTransferFromFungibleTokenLazyCreate(),
                htsTransferFromForNFTLazyCreate(),
                resourceLimitExceededRevertsAllRecords(),
                autoCreationFailsWithMirrorAddress());
    }

    @HapiTest
    HapiSpec resourceLimitExceededRevertsAllRecords() {
        final var n = 4; // preceding child record limit is 3
        final var nft = "nft";
        final var nftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec(
                        "ResourceLimitExceededRevertsAllRecords",
                        FULLY_NONDETERMINISTIC) // marked as fully non-deterministic due to difference between mono and
                // mod, see use of ifHapiTest() / ifNotHapiTest() below
                .given(
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
                .when(
                        cryptoApproveAllowance()
                                .payingWith(CIVILIAN)
                                .addNftAllowance(CIVILIAN, nft, AUTO_CREATION_MODES, true, List.of()),
                        sourcing(() -> contractCall(
                                        AUTO_CREATION_MODES,
                                        "createSeveralDirectly",
                                        headlongFromHexed(nftMirrorAddr.get()),
                                        nCopiesOfSender(n, mirrorAddrWith(civilianId.get())),
                                        nNonMirrorAddressFrom(n, civilianId.get() + 3_050_000),
                                        LongStream.iterate(1L, l -> l + 1)
                                                .limit(n)
                                                .toArray())
                                .via(creationAttempt)
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(CIVILIAN)
                                .hasKnownStatusFrom(MAX_CHILD_RECORDS_EXCEEDED, CONTRACT_REVERT_EXECUTED)))
                .then(
                        // mono-service did not do an "orderly shutdown" of the EVM transaction when it hit
                        // a resource limit exception like MAX_CHILD_RECORDS_EXCEEDED, instead throwing an
                        // exception to the top-level HAPI transaction and eradicating traceability exports
                        // in the process; we don't want to replicate that behavior in mod-service, hence
                        // the ifHapiTest() / ifNotHapiTest() split below
                        ifHapiTest(childRecordsCheck(
                                creationAttempt,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(MAX_CHILD_RECORDS_EXCEEDED))),
                        ifNotHapiTest(
                                emptyChildRecordsCheck(creationAttempt, MAX_CHILD_RECORDS_EXCEEDED),
                                inParallel(IntStream.range(0, n)
                                        .mapToObj(i -> sourcing(() -> getLiteralAliasAccountInfo(
                                                        hex(Bytes.fromHexString(nonMirrorAddrWith(
                                                                                civilianId.get() + 3_050_000 + n)
                                                                        .toString())
                                                                .toArray()))
                                                .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID)))
                                        .toArray(HapiSpecOperation[]::new))));
    }

    @HapiTest
    HapiSpec autoCreationFailsWithMirrorAddress() {
        final var nft = "nft";
        final var nftKey = "nftKeyHere";
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("AutoCreationFailsWithMirrorAddress", NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(
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
                .when(
                        cryptoApproveAllowance()
                                .payingWith(CIVILIAN)
                                .addNftAllowance(CIVILIAN, nft, AUTO_CREATION_MODES, true, List.of()),
                        sourcing(() -> contractCall(
                                        AUTO_CREATION_MODES,
                                        CREATE_DIRECTLY,
                                        headlongFromHexed(nftMirrorAddr.get()),
                                        mirrorAddrWith(civilianId.get()),
                                        mirrorAddrWith(civilianId.get() + 1_000_001),
                                        1L,
                                        false)
                                .via(creationAttempt)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(childRecordsCheck(
                        creationAttempt, CONTRACT_REVERT_EXECUTED, recordWith().status(INVALID_ALIAS_KEY)));
    }

    @HapiTest
    final HapiSpec erc20TransferLazyCreate() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec(
                        "erc20TransferLazyCreate",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE,
                        ALLOW_SKIPPED_ENTITY_IDS)
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> tokenAddr.set(
                                        HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT)))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    allRunFor(
                            spec,
                            contractCall(
                                            ERC_20_CONTRACT,
                                            TRANSFER_THEN_REVERT,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.valueOf(2))
                                    .via(TRANSFER_THEN_REVERT_TXN)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                            contractCall(
                                            ERC_20_CONTRACT,
                                            TRANSFER,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.valueOf(2))
                                    .via(TRANSFER_TXN)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(SUCCESS),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(alias)
                                    .hasTokenBalance(FUNGIBLE_TOKEN, 2)
                                    .logged(),
                            childRecordsCheck(
                                    TRANSFER_THEN_REVERT_TXN,
                                    CONTRACT_REVERT_EXECUTED,
                                    recordWith().status(REVERTED_SUCCESS)),
                            childRecordsCheck(
                                    TRANSFER_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)));
                }))
                .then();
    }

    // Expected INSUFFICIENT_GAS but was REVERTED_SUCCESS
    @HapiTest
    final HapiSpec erc20TransferFromLazyCreate() {
        return defaultHapiSpec(
                        "erc20TransferFromLazyCreate",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        ALLOW_SKIPPED_ENTITY_IDS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    allRunFor(
                            spec,
                            cryptoApproveAllowance()
                                    .payingWith(DEFAULT_PAYER)
                                    .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, ERC_20_CONTRACT, 2L)
                                    .via(BASE_APPROVE_TXN)
                                    .logged()
                                    .signedBy(DEFAULT_PAYER, OWNER)
                                    .fee(ONE_HBAR),
                            // lazy create attempt with unsufficient gas
                            contractCall(
                                            ERC_20_CONTRACT,
                                            TRANSFER_FROM,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.TWO)
                                    .gas(500_000)
                                    .via(NOT_ENOUGH_GAS_TXN)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                            // lazy create with revert afterwards
                            contractCall(
                                            ERC_20_CONTRACT,
                                            TRANSFER_FROM_THEN_REVERT,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.TWO)
                                    .gas(4_000_000)
                                    .via(TRANSFER_FROM_ACCOUNT_REVERT_TXN)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                            getAliasedAccountInfo(ECDSA_KEY).hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                            // successful lazy create
                            contractCall(
                                            ERC_20_CONTRACT,
                                            TRANSFER_FROM,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.TWO)
                                    .gas(4_000_000)
                                    .via(TRANSFER_FROM_ACCOUNT_TXN)
                                    .hasKnownStatus(SUCCESS),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(alias)
                                    .hasTokenBalance(FUNGIBLE_TOKEN, 2)
                                    .logged(),
                            childRecordsCheck(
                                    NOT_ENOUGH_GAS_TXN,
                                    CONTRACT_REVERT_EXECUTED,
                                    recordWith().status(INSUFFICIENT_GAS)),
                            childRecordsCheck(
                                    TRANSFER_FROM_ACCOUNT_REVERT_TXN,
                                    CONTRACT_REVERT_EXECUTED,
                                    recordWith().status(REVERTED_SUCCESS)),
                            childRecordsCheck(
                                    TRANSFER_FROM_ACCOUNT_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)));
                }))
                .then();
    }

    @HapiTest
    final HapiSpec erc721TransferFromLazyCreate() {
        return defaultHapiSpec(
                        "erc721TransferFromLazyCreate",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE,
                        ALLOW_SKIPPED_ENTITY_IDS)
                .given(
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
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_721_CONTRACT, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    allRunFor(
                            spec,
                            cryptoApproveAllowance()
                                    .payingWith(DEFAULT_PAYER)
                                    .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ERC_721_CONTRACT, false, List.of(1L))
                                    .via(BASE_APPROVE_TXN)
                                    .logged()
                                    .signedBy(DEFAULT_PAYER, OWNER)
                                    .fee(ONE_HBAR),
                            getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(ERC_721_CONTRACT),
                            contractCall(
                                            ERC_721_CONTRACT,
                                            TRANSFER_FROM_THEN_REVERT,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.valueOf(1))
                                    .via(TRANSFER_FROM_ACCOUNT_REVERT_TXN)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                            contractCall(
                                            ERC_721_CONTRACT,
                                            TRANSFER_FROM,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.valueOf(1))
                                    .via(TRANSFER_FROM_ACCOUNT_TXN)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(SUCCESS),
                            getAliasedAccountInfo(ECDSA_KEY)
                                    .has(AccountInfoAsserts.accountWith()
                                            .key(EMPTY_KEY)
                                            .autoRenew(THREE_MONTHS_IN_SECONDS)
                                            .receiverSigReq(false)
                                            .memo(LAZY_MEMO)),
                            getAliasedAccountBalance(alias)
                                    .hasTokenBalance(NON_FUNGIBLE_TOKEN, 1)
                                    .logged(),
                            childRecordsCheck(
                                    TRANSFER_FROM_ACCOUNT_REVERT_TXN,
                                    CONTRACT_REVERT_EXECUTED,
                                    recordWith().status(REVERTED_SUCCESS)),
                            childRecordsCheck(
                                    TRANSFER_FROM_ACCOUNT_TXN,
                                    SUCCESS,
                                    recordWith().status(SUCCESS),
                                    recordWith().status(SUCCESS)));
                }))
                .then();
    }

    @HapiTest
    final HapiSpec htsTransferFromFungibleTokenLazyCreate() {
        final var allowance = 10L;
        final var successfulTransferFromTxn = "txn";
        return defaultHapiSpec(
                        "htsTransferFromFungibleTokenLazyCreate",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        NONDETERMINISTIC_NONCE)
                .given(
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

    @HapiTest
    final HapiSpec htsTransferFromForNFTLazyCreate() {
        return defaultHapiSpec(
                        "htsTransferFromForNFTLazyCreate",
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE)
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(5),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        mintToken(NFT_TOKEN, List.of(META1, META2)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NFT_TOKEN, HTS_TRANSFER_FROM_CONTRACT, false, List.of(2L))
                                .via(BASE_APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                    allRunFor(
                            spec,
                            // transfer allowed NFT
                            contractCall(
                                            HTS_TRANSFER_FROM_CONTRACT,
                                            HTS_TRANSFER_FROM_NFT,
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(NFT_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            BigInteger.valueOf(2L))
                                    .gas(GAS_TO_OFFER)
                                    .via(TRANSFER_TXN)
                                    .hasKnownStatus(SUCCESS),
                            childRecordsCheck(
                                    TRANSFER_TXN,
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
                                    .hasTokenBalance(NFT_TOKEN, 1)
                                    .logged());
                }))
                .then();
    }

    @HapiTest
    final HapiSpec revertedAutoCreationRollsBackEvenIfTopLevelSucceeds() {
        return defaultHapiSpec(
                        "revertedAutoCreationRollsBackEvenIfTopLevelSucceeds",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE)
                .given(
                        newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(5),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(AUTO_CREATION_MODES),
                        contractCreate(AUTO_CREATION_MODES),
                        mintToken(NFT_TOKEN, List.of(META1, META2)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NFT_TOKEN, AUTO_CREATION_MODES, false, List.of(2L))
                                .via(BASE_APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(ECDSA_KEY);
                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                    final var addressBytes = recoverAddressFromPubKey(tmp);
                    allRunFor(
                            spec,
                            // transfer allowed NFT
                            contractCall(
                                            AUTO_CREATION_MODES,
                                            "createIndirectlyRevertingAndRecover",
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(NFT_TOKEN))),
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(OWNER))),
                                            HapiParserUtil.asHeadlongAddress(addressBytes),
                                            2L)
                                    .gas(GAS_TO_OFFER)
                                    .via(TRANSFER_TXN)
                                    .alsoSigningWithFullPrefix(OWNER)
                                    .hasKnownStatus(SUCCESS));
                }))
                .then(
                        getTxnRecord(TRANSFER_TXN).hasNonStakingChildRecordCount(1),
                        childRecordsCheck(
                                TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(REVERTED_SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))));
    }
}
