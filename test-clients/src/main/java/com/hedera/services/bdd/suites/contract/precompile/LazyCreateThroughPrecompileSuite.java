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
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resetToDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.LAZY_CREATION_ENABLED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LazyCreateThroughPrecompileSuite extends HapiApiSuite {
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
    private static final String TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT =
            "TransferToAliasPrecompileContract";
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

    public static void main(String... args) {
        new LazyCreateThroughPrecompileSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                transferTokenToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                transferNftToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                transferNftsToEVMAddressAliasRevertAndTransferAgainSuccessfully(),
                precompileTooManyLazyCreatesFail());
    }

    private HapiApiSpec transferTokenToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec("transferTokenToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        UtilVerbs.overriding(LAZY_CREATION_ENABLED, "true"),
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
                                                                    .evmAddressAlias(alias)
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
                                                    recordWith()
                                                            .status(SUCCESS)
                                                            .alias(
                                                                    ByteStringUtils.wrapUnsafely(
                                                                            addressBytes)),
                                                    recordWith().status(SUCCESS)));
                                }))
                .then(resetToDefault(LAZY_CREATION_ENABLED));
    }

    private HapiApiSpec transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec("transferTokensToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        UtilVerbs.overriding(LAZY_CREATION_ENABLED, "true"),
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
                                                                    .evmAddressAlias(alias)
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
                                                    recordWith()
                                                            .status(SUCCESS)
                                                            .alias(
                                                                    ByteStringUtils.wrapUnsafely(
                                                                            addressBytes)),
                                                    recordWith().status(SUCCESS)));
                                }))
                .then(resetToDefault(LAZY_CREATION_ENABLED));
    }

    private HapiApiSpec transferNftToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        return defaultHapiSpec("transferNftToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        UtilVerbs.overriding(LAZY_CREATION_ENABLED, "true"),
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
                                                                    .evmAddressAlias(alias)
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
                                                    recordWith()
                                                            .status(SUCCESS)
                                                            .alias(
                                                                    ByteStringUtils.wrapUnsafely(
                                                                            addressBytes)),
                                                    recordWith().status(SUCCESS)));
                                }))
                .then(resetToDefault(LAZY_CREATION_ENABLED));
    }

    private HapiApiSpec transferNftsToEVMAddressAliasRevertAndTransferAgainSuccessfully() {
        return defaultHapiSpec("transferNftsToEVMAddressAliasRevertAndTransferAgainSuccessfully")
                .given(
                        UtilVerbs.overriding(LAZY_CREATION_ENABLED, "true"),
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
                                                                    .evmAddressAlias(alias)
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
                                                    recordWith()
                                                            .status(SUCCESS)
                                                            .alias(
                                                                    ByteStringUtils.wrapUnsafely(
                                                                            addressBytes)),
                                                    recordWith().status(SUCCESS)));
                                }))
                .then(resetToDefault(LAZY_CREATION_ENABLED));
    }

    private HapiApiSpec precompileTooManyLazyCreatesFail() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();
        final var SECP_256K1_SOURCE_KEY2 = "secondECDSAKey";
        return defaultHapiSpec("precompileTooManyLazyCreatesFail")
                .given(
                        UtilVerbs.overriding(LAZY_CREATION_ENABLED, "true"),
                        UtilVerbs.overriding("consensus.handle.maxPrecedingRecords", "1"),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY2).shape(SECP_256K1_SHAPE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
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
                        tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    final var ecdsaKey2 =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY2);
                                    final var tmp2 = ecdsaKey2.getECDSASecp256K1().toByteArray();
                                    final var addressBytes2 = recoverAddressFromPubKey(tmp2);
                                    allRunFor(
                                            spec,
                                            contractCall(
                                                            TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                                                            "transferTokensCallNestedThenAgain",
                                                            HapiParserUtil.asHeadlongAddress(
                                                                    asAddress(
                                                                            spec.registry()
                                                                                    .getTokenID(
                                                                                            FUNGIBLE_TOKEN))),
                                                            new Address[] {
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                OWNER))),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        addressBytes),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        addressBytes2)
                                                            },
                                                            new long[] {-4L, 2L, 2L},
                                                            new long[] {-4L, 2L, 2L})
                                                    .via(TRANSFER_TOKENS_TXN)
                                                    .gas(GAS_TO_OFFER)
                                                    .alsoSigningWithFullPrefix(OWNER)
                                                    .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                                    .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY2)
                                                    .hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
                                            emptyChildRecordsCheck(
                                                    TRANSFER_TOKENS_TXN,
                                                    MAX_CHILD_RECORDS_EXCEEDED));
                                }))
                .then(
                        resetToDefault(
                                LAZY_CREATION_ENABLED, "consensus.handle.maxPrecedingRecords"));
    }
}
