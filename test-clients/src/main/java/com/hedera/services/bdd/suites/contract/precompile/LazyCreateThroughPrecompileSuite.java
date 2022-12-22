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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getLiteralAliasAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nftTransferToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class LazyCreateThroughPrecompileSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LazyCreateThroughPrecompileSuite.class);
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String MULTI_KEY = "purpose";
    private static final String OWNER = "owner";
    private static final String FIRST = "FIRST";
    public static final ByteString FIRST_META =
            ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    public static final ByteString SECOND_META =
            ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    private static final String TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT = "PrecompileAliasXfer";
    private static final String SPENDER = "spender";
    private static final String TRANSFER_TOKEN_REVERT_TXN = "transferTokenThanRevertTxn";
    private static final String TRANSFER_TOKENS_REVERT_TXN = "transferTokensThanRevertTxn";
    private static final String TRANSFER_NFT_REVERT_TXN = "transferNFTThanRevertTxn";
    private static final String TRANSFER_NFTS_REVERT_TXN = "transferNFTsThanRevertTxn";
    private static final String TRANSFER_TOKEN_TXN = "transferTokenTxn";
    private static final String TRANSFER_TOKENS_TXN = "transferTokensTxn";
    private static final String TRANSFER_NFT_TXN = "transferNFTTxn";
    private static final String TRANSFER_NFTS_TXN = "transferNFTsTxn";
    private static final String TRANSFER_TOKEN = "transferTokenCall";
    private static final String TRANSFER_TOKENS = "transferTokensCall";
    private static final String TRANSFER_NFT = "transferNFTCall";
    private static final String TRANSFER_NFTS = "transferNFTsCall";
    private static final String TRANSFER_NFT_THAN_REVERT = "transferNFTThanRevertCall";
    private static final String TRANSFER_NFTS_THAN_REVERT = "transferNFTsThanRevertCall";
    private static final String TRANSFER_TOKEN_THAN_REVERT = "transferTokenThanRevertCall";
    private static final String TRANSFER_TOKENS_THAN_REVERT = "transferTokensThanRevertCall";
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
    private static final String NFT_TOKEN2 = "Token_NFT2";
    private static final ByteString META1 = ByteStringUtils.wrapUnsafely("meta1".getBytes());
    private static final ByteString META2 = ByteStringUtils.wrapUnsafely("meta2".getBytes());
    private static final String TOKEN_TREASURY = "treasury";
    private static final String RECEIVER = "receiver";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);
    private static final String DELEGATE_KEY = "contractKey";
    private static final String CONTRACT = "CryptoTransfer";
    private static final String HTS_TRANSFER_FROM = "htsTransferFrom";
    private static final String HTS_TRANSFER_FROM_NFT = "htsTransferFromNFT";
    private static final ByteString META3 = ByteStringUtils.wrapUnsafely("meta3".getBytes());
    private static final ByteString META4 = ByteStringUtils.wrapUnsafely("meta4".getBytes());
    private static final String BASE_APPROVE_TXN = "baseApproveTxn";
    private static final String TRANSFER_MULTIPLE_TOKENS = "transferMultipleTokens";

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
                transferFungibleToNonExistingEvmAddressViaCryptoTransfer(),
                transferNftToNonExistingEvmAddressViaCryptoTransfer(),
                transferTokenToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                transferNftToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                transferNftsToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                transferFromForFungibleTokenToEVMAddressAlias(),
                transferFromForNFTToEVMAddressAlias(),
                transferErc20TokenToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                erc721TransferFromWithApprovalToEVMAddressAliasRevertAndTransferFromAgainSuccessfully(),
                hollowAccountSigningReqsStillEnforced(),
                resourceLimitExceededRevertsAllRecords(),
                autoCreationFailsWithMirrorAddress(),
                revertedAutoCreationRollsBackEvenIfTopLevelSucceeds(),
                canCreateMultipleHollows()
                // canCreateViaFungibleWithFractionalFee()  // FIXED BY ANOTHER PR
                );
    }

    private HapiSpec
            erc721TransferFromWithApprovalToEVMAddressAliasRevertAndTransferFromAgainSuccessfully() {
        return defaultHapiSpec(
                        "erc721TransferFromWithApprovalToEVMAddressAliasRevertAndTransferFromAgainSuccessfully")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
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
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                                    allRunFor(
                                            spec,
                                            cryptoApproveAllowance()
                                                    .payingWith(DEFAULT_PAYER)
                                                    .addNftAllowance(
                                                            OWNER,
                                                            NON_FUNGIBLE_TOKEN,
                                                            ERC_721_CONTRACT,
                                                            false,
                                                            List.of(1L))
                                                    .via(BASE_APPROVE_TXN)
                                                    .logged()
                                                    .signedBy(DEFAULT_PAYER, OWNER)
                                                    .fee(ONE_HBAR),
                                            getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L)
                                                    .hasSpenderID(ERC_721_CONTRACT),
                                            contractCall(
                                                            ERC_721_CONTRACT,
                                                            TRANSFER_FROM_THEN_REVERT,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            NON_FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            OWNER))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
                                                            BigInteger.valueOf(1))
                                                    .via(TRANSFER_FROM_ACCOUNT_REVERT_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            contractCall(
                                                            ERC_721_CONTRACT,
                                                            TRANSFER_FROM,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            NON_FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            OWNER))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
                                                            BigInteger.valueOf(1))
                                                    .via(TRANSFER_FROM_ACCOUNT_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(SUCCESS),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .has(
                                                            AccountInfoAsserts.accountWith()
                                                                    .hasEmptyKey()
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
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

    private HapiSpec transferErc20TokenToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec(
                        "transferErc20TokenToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id ->
                                                tokenAddr.set(
                                                        HapiPropertySource.asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(id)))),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(
                                moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            ERC_20_CONTRACT,
                                                            TRANSFER_THEN_REVERT,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
                                                            BigInteger.valueOf(2))
                                                    .via(TRANSFER_THEN_REVERT_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            contractCall(
                                                            ERC_20_CONTRACT,
                                                            TRANSFER,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
                                                            BigInteger.valueOf(2))
                                                    .via(TRANSFER_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(SUCCESS),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .has(
                                                            AccountInfoAsserts.accountWith()
                                                                    .hasEmptyKey()
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
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

    private HapiSpec transferFungibleToNonExistingEvmAddressViaCryptoTransfer() {
        final var FUNGIBLE_TOKEN_2 = "ftnt";
        return defaultHapiSpec("transferFungibleToNonExistingEvmAddressViaCryptoTransfer")
                .given(
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN_2)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN_2)),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(
                                moving(200, FUNGIBLE_TOKEN_2).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).maxAutomaticTokenAssociations(1),
                        getContractInfo(CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
                                .logged())
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                                    final var token2 = spec.registry().getTokenID(FUNGIBLE_TOKEN_2);
                                    final var sender = spec.registry().getAccountID(SENDER);
                                    final var amountToBeSent = 50L;

                                    allRunFor(
                                            spec,
                                            newKeyNamed(DELEGATE_KEY)
                                                    .shape(
                                                            DELEGATE_CONTRACT_KEY_SHAPE.signedWith(
                                                                    sigs(ON, CONTRACT))),
                                            cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                            contractCall(
                                                            CONTRACT,
                                                            TRANSFER_MULTIPLE_TOKENS,
                                                            tokenTransferLists()
                                                                    .withTokenTransferList(
                                                                            tokenTransferList()
                                                                                    .forToken(token)
                                                                                    .withAccountAmounts(
                                                                                            accountAmount(
                                                                                                    sender,
                                                                                                    -amountToBeSent),
                                                                                            accountAmountAlias(
                                                                                                    addressBytes,
                                                                                                    amountToBeSent))
                                                                                    .build(),
                                                                            tokenTransferList()
                                                                                    .forToken(
                                                                                            token2)
                                                                                    .withAccountAmounts(
                                                                                            accountAmount(
                                                                                                    sender,
                                                                                                    -amountToBeSent),
                                                                                            accountAmountAlias(
                                                                                                    addressBytes,
                                                                                                    amountToBeSent))
                                                                                    .build())
                                                                    .build())
                                                    .payingWith(GENESIS)
                                                    .via(TRANSFER_TXN)
                                                    .gas(GAS_TO_OFFER),
                                            contractCall(
                                                            CONTRACT,
                                                            TRANSFER_MULTIPLE_TOKENS,
                                                            tokenTransferLists()
                                                                    .withTokenTransferList(
                                                                            tokenTransferList()
                                                                                    .forToken(token)
                                                                                    .withAccountAmounts(
                                                                                            accountAmount(
                                                                                                    sender,
                                                                                                    -1L),
                                                                                            accountAmountAlias(
                                                                                                    addressBytes,
                                                                                                    1L))
                                                                                    .build())
                                                                    .build())
                                                    .payingWith(GENESIS)
                                                    .via(TRANSFER_TXN2)
                                                    .gas(GAS_TO_OFFER),
                                            childRecordsCheck(
                                                    TRANSFER_TXN,
                                                    SUCCESS,
                                                    recordWith().status(SUCCESS),
                                                    recordWith().status(SUCCESS)),
                                            childRecordsCheck(
                                                    TRANSFER_TXN2,
                                                    SUCCESS,
                                                    recordWith().status(SUCCESS)),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .has(
                                                            accountWith()
                                                                    .hasEmptyKey()
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO)),
                                            getAliasedAccountBalance(evmAddressBytes)
                                                    .hasTokenBalance(FUNGIBLE_TOKEN, 51)
                                                    .hasTokenBalance(FUNGIBLE_TOKEN_2, 50)
                                                    .logged());
                                }))
                .then();
    }

    private HapiSpec transferNftToNonExistingEvmAddressViaCryptoTransfer() {
        return defaultHapiSpec("transferNftToNonExistingEvmAddressViaCryptoTransfer")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER)
                                .balance(10 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5)
                                .key(MULTI_KEY),
                        cryptoCreate(RECEIVER)
                                .balance(2 * ONE_HUNDRED_HBARS)
                                .receiverSigRequired(true)
                                .maxAutomaticTokenAssociations(5),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(SENDER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY),
                        tokenCreate(NFT_TOKEN2)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(SENDER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY),
                        mintToken(NFT_TOKEN, List.of(META1, META2)),
                        mintToken(NFT_TOKEN2, List.of(META3, META4)),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).maxAutomaticTokenAssociations(1),
                        getContractInfo(CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
                                .logged())
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    final var token = spec.registry().getTokenID(NFT_TOKEN);
                                    final var token2 = spec.registry().getTokenID(NFT_TOKEN2);
                                    final var sender = spec.registry().getAccountID(SENDER);

                                    allRunFor(
                                            spec,
                                            newKeyNamed(DELEGATE_KEY)
                                                    .shape(
                                                            DELEGATE_CONTRACT_KEY_SHAPE.signedWith(
                                                                    sigs(ON, CONTRACT))),
                                            cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                            contractCall(
                                                            CONTRACT,
                                                            TRANSFER_MULTIPLE_TOKENS,
                                                            tokenTransferLists()
                                                                    .withTokenTransferList(
                                                                            tokenTransferList()
                                                                                    .forToken(token)
                                                                                    .withNftTransfers(
                                                                                            nftTransferToAlias(
                                                                                                    sender,
                                                                                                    addressBytes,
                                                                                                    1L))
                                                                                    .build(),
                                                                            tokenTransferList()
                                                                                    .forToken(
                                                                                            token2)
                                                                                    .withNftTransfers(
                                                                                            nftTransferToAlias(
                                                                                                    sender,
                                                                                                    addressBytes,
                                                                                                    1L))
                                                                                    .build())
                                                                    .build())
                                                    .payingWith(GENESIS)
                                                    .via(TRANSFER_TXN)
                                                    .gas(GAS_TO_OFFER),
                                            childRecordsCheck(
                                                    TRANSFER_TXN,
                                                    SUCCESS,
                                                    recordWith().status(SUCCESS),
                                                    recordWith().status(SUCCESS)),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .has(
                                                            accountWith()
                                                                    .hasEmptyKey()
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO)),
                                            getAliasedAccountBalance(evmAddressBytes)
                                                    .hasTokenBalance(NFT_TOKEN, 1)
                                                    .hasTokenBalance(NFT_TOKEN2, 1)
                                                    .logged());
                                }))
                .then();
    }

    private HapiSpec transferFromForFungibleTokenToEVMAddressAlias() {
        final var allowance = 10L;
        final var successfulTransferFromTxn = "txn";
        return defaultHapiSpec("transferFromForFungibleTokenToEVMAddressAlias")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5),
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
                                .addTokenAllowance(
                                        OWNER,
                                        FUNGIBLE_TOKEN,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        allowance)
                                .via(BASE_APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .tokenAllowancesContaining(
                                                        FUNGIBLE_TOKEN,
                                                        HTS_TRANSFER_FROM_CONTRACT,
                                                        allowance)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final ByteString alias =
                                            ByteStringUtils.wrapUnsafely(addressBytes);
                                    allRunFor(
                                            spec,
                                            // transfer allowance/2 amount
                                            contractCall(
                                                            HTS_TRANSFER_FROM_CONTRACT,
                                                            HTS_TRANSFER_FROM,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            OWNER))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
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
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contractCallResult(
                                                                                    htsPrecompileResult()
                                                                                            .forFunction(
                                                                                                    FunctionType
                                                                                                            .HAPI_TRANSFER_FROM)
                                                                                            .withStatus(
                                                                                                    SUCCESS)))),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .logged()
                                                    .has(
                                                            accountWith()
                                                                    .hasEmptyKey()
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO)),
                                            getAliasedAccountBalance(alias)
                                                    .hasTokenBalance(FUNGIBLE_TOKEN, allowance / 2)
                                                    .logged());
                                }))
                .then();
    }

    private HapiSpec transferFromForNFTToEVMAddressAlias() {
        return defaultHapiSpec("transferFromForNFTToEVMAddressAlias")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5),
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
                                .addNftAllowance(
                                        OWNER,
                                        NFT_TOKEN,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        false,
                                        List.of(2L))
                                .via(BASE_APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
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
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            NFT_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            OWNER))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
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
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contractCallResult(
                                                                                    htsPrecompileResult()
                                                                                            .forFunction(
                                                                                                    FunctionType
                                                                                                            .HAPI_TRANSFER_FROM)
                                                                                            .withStatus(
                                                                                                    SUCCESS)))),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .logged()
                                                    .has(
                                                            accountWith()
                                                                    .hasEmptyKey()
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO)),
                                            getAliasedAccountBalance(alias)
                                                    .hasTokenBalance(NFT_TOKEN, 1)
                                                    .logged());
                                }))
                .then();
    }

    private HapiSpec transferTokenToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec("transferTokenToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id ->
                                                tokenAddr.set(
                                                        HapiPropertySource.asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(id)))),
                        uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        tokenAssociate(
                                TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(
                                moving(5, FUNGIBLE_TOKEN)
                                        .between(
                                                TOKEN_TREASURY,
                                                TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            TRANSFER_TOKEN_THAN_REVERT,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
                                                            2L)
                                                    .via(TRANSFER_TOKEN_REVERT_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            TRANSFER_TOKEN,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
                                                            2L)
                                                    .via(TRANSFER_TOKEN_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(SUCCESS),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .has(
                                                            AccountInfoAsserts.accountWith()
                                                                    .key(EMPTY_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO)),
                                            getAliasedAccountBalance(alias)
                                                    .hasTokenBalance(FUNGIBLE_TOKEN, 2)
                                                    .logged(),
                                            childRecordsCheck(
                                                    TRANSFER_TOKEN_REVERT_TXN,
                                                    CONTRACT_REVERT_EXECUTED,
                                                    recordWith().status(REVERTED_SUCCESS)),
                                            childRecordsCheck(
                                                    TRANSFER_TOKEN_TXN,
                                                    SUCCESS,
                                                    recordWith().status(SUCCESS),
                                                    recordWith().status(SUCCESS)));
                                }))
                .then();
    }

    private HapiSpec transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec("transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id ->
                                                tokenAddr.set(
                                                        HapiPropertySource.asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(id)))),
                        uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        tokenAssociate(
                                TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(
                                moving(5, FUNGIBLE_TOKEN)
                                        .between(
                                                TOKEN_TREASURY,
                                                TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                                    assert addressBytes != null;
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            TRANSFER_TOKENS_THAN_REVERT,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            FUNGIBLE_TOKEN))),
                                                            new Address[] {
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        addressBytes)
                                                            },
                                                            new long[] {-2L, 2L})
                                                    .via(TRANSFER_TOKENS_REVERT_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            TRANSFER_TOKENS,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            FUNGIBLE_TOKEN))),
                                                            new Address[] {
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        addressBytes)
                                                            },
                                                            new long[] {-2L, 2L})
                                                    .via(TRANSFER_TOKENS_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(SUCCESS),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .has(
                                                            AccountInfoAsserts.accountWith()
                                                                    .key(EMPTY_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO)),
                                            getAliasedAccountBalance(alias)
                                                    .hasTokenBalance(FUNGIBLE_TOKEN, 2)
                                                    .logged(),
                                            childRecordsCheck(
                                                    TRANSFER_TOKENS_REVERT_TXN,
                                                    CONTRACT_REVERT_EXECUTED,
                                                    recordWith().status(REVERTED_SUCCESS)),
                                            childRecordsCheck(
                                                    TRANSFER_TOKENS_TXN,
                                                    SUCCESS,
                                                    recordWith().status(SUCCESS),
                                                    recordWith().status(SUCCESS)));
                                }))
                .then();
    }

    private HapiSpec transferNftToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        return defaultHapiSpec("transferNftToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
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
                        contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META)),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            TRANSFER_NFT_THAN_REVERT,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            NON_FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            OWNER))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
                                                            1L)
                                                    .via(TRANSFER_NFT_REVERT_TXN)
                                                    .alsoSigningWithFullPrefix(OWNER)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            TRANSFER_NFT,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            NON_FUNGIBLE_TOKEN))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            OWNER))),
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    addressBytes),
                                                            1L)
                                                    .via(TRANSFER_NFT_TXN)
                                                    .alsoSigningWithFullPrefix(OWNER)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(SUCCESS),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .has(
                                                            AccountInfoAsserts.accountWith()
                                                                    .key(EMPTY_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO)),
                                            getAliasedAccountBalance(alias)
                                                    .hasTokenBalance(NON_FUNGIBLE_TOKEN, 1)
                                                    .logged(),
                                            childRecordsCheck(
                                                    TRANSFER_NFT_REVERT_TXN,
                                                    CONTRACT_REVERT_EXECUTED,
                                                    recordWith().status(REVERTED_SUCCESS)),
                                            childRecordsCheck(
                                                    TRANSFER_NFT_TXN,
                                                    SUCCESS,
                                                    recordWith().status(SUCCESS),
                                                    recordWith().status(SUCCESS)));
                                }))
                .then();
    }

    private HapiSpec transferNftsToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        return defaultHapiSpec("transferNftsToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
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
                        contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META)),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var alias = ByteStringUtils.wrapUnsafely(addressBytes);
                                    assert addressBytes != null;
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            TRANSFER_NFTS_THAN_REVERT,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            NON_FUNGIBLE_TOKEN))),
                                                            new Address[] {
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER)))
                                                            },
                                                            new Address[] {
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        addressBytes)
                                                            },
                                                            new long[] {1L})
                                                    .via(TRANSFER_NFTS_REVERT_TXN)
                                                    .alsoSigningWithFullPrefix(OWNER)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            TRANSFER_NFTS,
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            NON_FUNGIBLE_TOKEN))),
                                                            new Address[] {
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER)))
                                                            },
                                                            new Address[] {
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        addressBytes)
                                                            },
                                                            new long[] {1L})
                                                    .via(TRANSFER_NFTS_TXN)
                                                    .alsoSigningWithFullPrefix(OWNER)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(SUCCESS),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .has(
                                                            AccountInfoAsserts.accountWith()
                                                                    .key(EMPTY_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO)),
                                            getAliasedAccountBalance(alias)
                                                    .hasTokenBalance(NON_FUNGIBLE_TOKEN, 1)
                                                    .logged(),
                                            childRecordsCheck(
                                                    TRANSFER_NFTS_REVERT_TXN,
                                                    CONTRACT_REVERT_EXECUTED,
                                                    recordWith().status(REVERTED_SUCCESS)),
                                            childRecordsCheck(
                                                    TRANSFER_NFTS_TXN,
                                                    SUCCESS,
                                                    recordWith().status(SUCCESS),
                                                    recordWith().status(SUCCESS)));
                                }))
                .then();
    }

    HapiSpec hollowAccountSigningReqsStillEnforced() {
        final var nft = "nft";
        final var nftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final var creationReversal = "creationReversal";
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("HollowAccountSigningReqsStillEnforced")
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
                                        idLit ->
                                                nftMirrorAddr.set(
                                                        asHexedSolidityAddress(asToken(idLit)))),
                        mintToken(nft, List.of(ByteString.copyFromUtf8(ONE_TIME))))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        AUTO_CREATION_MODES,
                                                        CREATE_DIRECTLY,
                                                        headlongFromHexed(nftMirrorAddr.get()),
                                                        mirrorAddrWith(civilianId.get()),
                                                        nonMirrorAddrWith(
                                                                civilianId.get() + 4_000_000),
                                                        1L,
                                                        false)
                                                .via(creationAttempt)
                                                .gas(GAS_TO_OFFER)
                                                .alsoSigningWithFullPrefix(CIVILIAN)))
                .then(
                        getTxnRecord(creationAttempt).andAllChildRecords().logged(),
                        sourcing(
                                () ->
                                        getLiteralAliasAccountInfo(
                                                        hex(
                                                                Bytes.fromHexString(
                                                                                nonMirrorAddrWith(
                                                                                                civilianId
                                                                                                                .get()
                                                                                                        + 4_000_000)
                                                                                        .toString())
                                                                        .toArray()))
                                                .logged()),
                        // Now try to reverse the transfer and take the hollow account's NFT
                        sourcing(
                                () ->
                                        contractCall(
                                                        AUTO_CREATION_MODES,
                                                        CREATE_DIRECTLY,
                                                        headlongFromHexed(nftMirrorAddr.get()),
                                                        nonMirrorAddrWith(
                                                                civilianId.get() + 4_000_000),
                                                        mirrorAddrWith(civilianId.get()),
                                                        1L,
                                                        false)
                                                .via(creationReversal)
                                                .gas(GAS_TO_OFFER)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(
                                () ->
                                        childRecordsCheck(
                                                creationReversal,
                                                CONTRACT_REVERT_EXECUTED,
                                                recordWith()
                                                        .status(
                                                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))));
    }

    HapiSpec resourceLimitExceededRevertsAllRecords() {
        final var n = 4; // preceding child record limit is 3
        final var nft = "nft";
        final var nftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("ResourceLimitExceededRevertsAllRecords")
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
                                        idLit ->
                                                nftMirrorAddr.set(
                                                        asHexedSolidityAddress(asToken(idLit)))),
                        mintToken(
                                nft,
                                IntStream.range(0, n)
                                        .mapToObj(i -> ByteString.copyFromUtf8(ONE_TIME + i))
                                        .toList()))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        AUTO_CREATION_MODES,
                                                        "createSeveralDirectly",
                                                        headlongFromHexed(nftMirrorAddr.get()),
                                                        nCopiesOfSender(
                                                                n,
                                                                mirrorAddrWith(civilianId.get())),
                                                        nNonMirrorAddressFrom(
                                                                n, civilianId.get() + 3_050_000),
                                                        LongStream.iterate(1L, l -> l + 1)
                                                                .limit(n)
                                                                .toArray())
                                                .via(creationAttempt)
                                                .gas(GAS_TO_OFFER)
                                                .alsoSigningWithFullPrefix(CIVILIAN)
                                                .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED)))
                .then(
                        emptyChildRecordsCheck(creationAttempt, MAX_CHILD_RECORDS_EXCEEDED),
                        inParallel(
                                IntStream.range(0, n)
                                        .mapToObj(
                                                i ->
                                                        sourcing(
                                                                () ->
                                                                        getLiteralAliasAccountInfo(
                                                                                        hex(
                                                                                                Bytes
                                                                                                        .fromHexString(
                                                                                                                nonMirrorAddrWith(
                                                                                                                                civilianId
                                                                                                                                                .get()
                                                                                                                                        + 3_050_000
                                                                                                                                        + n)
                                                                                                                        .toString())
                                                                                                        .toArray()))
                                                                                .hasCostAnswerPrecheck(
                                                                                        INVALID_ACCOUNT_ID)))
                                        .toArray(HapiSpecOperation[]::new)));
    }

    HapiSpec autoCreationFailsWithMirrorAddress() {
        final var nft = "nft";
        final var nftKey = "nftKeyHere";
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("AutoCreationFailsWithMirrorAddress")
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
                                        idLit ->
                                                nftMirrorAddr.set(
                                                        asHexedSolidityAddress(asToken(idLit)))),
                        mintToken(nft, List.of(ByteString.copyFromUtf8(ONE_TIME))))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        AUTO_CREATION_MODES,
                                                        CREATE_DIRECTLY,
                                                        headlongFromHexed(nftMirrorAddr.get()),
                                                        mirrorAddrWith(civilianId.get()),
                                                        mirrorAddrWith(
                                                                civilianId.get() + 1_000_001),
                                                        1L,
                                                        false)
                                                .via(creationAttempt)
                                                .gas(GAS_TO_OFFER)
                                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                creationAttempt,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ALIAS_KEY)));
    }

    HapiSpec revertedAutoCreationRollsBackEvenIfTopLevelSucceeds() {
        final var nft = "nft";
        final var nftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("RevertedAutoCreationRollsBackEvenIfTopLevelSucceeds")
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
                                        idLit ->
                                                nftMirrorAddr.set(
                                                        asHexedSolidityAddress(asToken(idLit)))),
                        mintToken(nft, List.of(ByteString.copyFromUtf8(ONE_TIME))))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        AUTO_CREATION_MODES,
                                                        "createIndirectlyRevertingAndRecover",
                                                        headlongFromHexed(nftMirrorAddr.get()),
                                                        mirrorAddrWith(civilianId.get()),
                                                        nonMirrorAddrWith(
                                                                civilianId.get() + 8_000_000),
                                                        1L)
                                                .via(creationAttempt)
                                                .gas(GAS_TO_OFFER)
                                                .alsoSigningWithFullPrefix(CIVILIAN)
                                                .hasKnownStatus(SUCCESS)))
                .then(
                        childRecordsCheck(
                                creationAttempt,
                                SUCCESS,
                                recordWith()
                                        .status(REVERTED_SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))));
    }

    HapiSpec canCreateViaFungibleWithFractionalFee() {
        final var ft = "ft";
        final var ftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> ftMirrorAddr = new AtomicReference<>();
        final long supply = 100_000_000;

        return defaultHapiSpec("CanCreateViaFungibleWithFractionalFee")
                .given(
                        newKeyNamed(ftKey),
                        uploadInitCode(AUTO_CREATION_MODES),
                        contractCreate(AUTO_CREATION_MODES),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(CIVILIAN)
                                .maxAutomaticTokenAssociations(1)
                                .keyShape(ED25519)
                                .exposingCreatedIdTo(id -> civilianId.set(id.getAccountNum())),
                        tokenCreate(ft)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyKey(ftKey)
                                .initialSupply(supply)
                                .withCustom(
                                        fractionalFee(
                                                1L, 20L, 0L, OptionalLong.of(0L), TOKEN_TREASURY))
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                ftMirrorAddr.set(
                                                        asHexedSolidityAddress(asToken(idLit)))),
                        cryptoTransfer(
                                TokenMovement.moving(supply, ft).between(TOKEN_TREASURY, CIVILIAN)))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        AUTO_CREATION_MODES,
                                                        "createDirectlyViaFungible",
                                                        headlongFromHexed(ftMirrorAddr.get()),
                                                        mirrorAddrWith(civilianId.get()),
                                                        nonMirrorAddrWith(civilianId.get() + 1),
                                                        supply)
                                                .via(creationAttempt)
                                                .gas(GAS_TO_OFFER)
                                                .alsoSigningWithFullPrefix(CIVILIAN)
                                                .hasKnownStatus(SUCCESS)))
                .then(getTxnRecord(creationAttempt).andAllChildRecords().logged());
    }

    HapiSpec canCreateMultipleHollows() {
        final var n = 3;
        final var nft = "nft";
        final var nftKey = NFT_KEY;
        final var creationAttempt = CREATION_ATTEMPT;
        final AtomicLong civilianId = new AtomicLong();
        final AtomicReference<String> nftMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("CanCreateMultipleHollows")
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
                                        idLit ->
                                                nftMirrorAddr.set(
                                                        asHexedSolidityAddress(asToken(idLit)))),
                        mintToken(
                                nft,
                                IntStream.range(0, n)
                                        .mapToObj(i -> ByteString.copyFromUtf8(ONE_TIME + i))
                                        .toList()))
                .when(
                        sourcing(
                                () ->
                                        contractCall(
                                                        AUTO_CREATION_MODES,
                                                        "createSeveralDirectly",
                                                        headlongFromHexed(nftMirrorAddr.get()),
                                                        nCopiesOfSender(
                                                                n,
                                                                mirrorAddrWith(civilianId.get())),
                                                        nNonMirrorAddressFrom(
                                                                n, civilianId.get() + 1),
                                                        LongStream.iterate(1L, l -> l + 1)
                                                                .limit(n)
                                                                .toArray())
                                                .via(creationAttempt)
                                                .gas(GAS_TO_OFFER)
                                                .alsoSigningWithFullPrefix(CIVILIAN)
                                                .hasKnownStatus(SUCCESS)))
                .then(getTxnRecord(creationAttempt).andAllChildRecords().logged());
    }

    private Address[] nCopiesOfSender(final int n, final Address mirrorAddr) {
        return Collections.nCopies(n, mirrorAddr).toArray(Address[]::new);
    }

    private Address[] nNonMirrorAddressFrom(final int n, final long m) {
        return LongStream.range(m, m + n).mapToObj(this::nonMirrorAddrWith).toArray(Address[]::new);
    }

    private Address headlongFromHexed(final String addr) {
        return Address.wrap(Address.toChecksumAddress("0x" + addr));
    }

    private Address mirrorAddrWith(final long num) {
        return Address.wrap(
                Address.toChecksumAddress(
                        new BigInteger(1, HapiPropertySource.asSolidityAddress(0, 0, num))));
    }

    private Address nonMirrorAddrWith(final long num) {
        return Address.wrap(
                Address.toChecksumAddress(
                        new BigInteger(1, HapiPropertySource.asSolidityAddress(666, 666, num))));
    }
}
