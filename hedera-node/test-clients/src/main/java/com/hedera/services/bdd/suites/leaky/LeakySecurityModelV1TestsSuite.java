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

package com.hedera.services.bdd.suites.leaky;

import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nftTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.ContractKeysStillWorkAsExpectedSuite.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.TBD_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class LeakySecurityModelV1TestsSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(LeakySecurityModelV1TestsSuite.class);
    private static final String OUTER_CONTRACT = "NestedAssociateDissociate";
    private static final String NESTED_CONTRACT = "AssociateDissociate";
    private static final String ASSOCIATE_DISSOCIATE_CONTRACT = "AssociateDissociate";
    private static final String ACCOUNT = "anybody";
    private static final long GAS_TO_OFFER = 2_000_000L;
    private static final String MULTI_KEY = "Multi key";
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    public static final String ACCOUNT_TO_ASSOCIATE = "account3";
    public static final String ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
    public static final String CONTRACT_ADMIN_KEY = "contractAdminKey";
    public static final String ED25519KEY = "ed25519key";
    public static final String ECDSA_KEY = "ecdsa";
    public static final String TOKEN_CREATE_CONTRACT = "TokenCreateContract";
    public static final String FIRST_CREATE_TXN = "firstCreateTxn";
    private static final String CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION = "createNFTTokenWithKeysAndExpiry";
    public static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    public static final long AUTO_RENEW_PERIOD = 8_000_000L;
    public static final String TOKEN_CREATE_CONTRACT_AS_KEY = "tokenCreateContractAsKey";
    public static final String EXPLICIT_CREATE_RESULT = "Explicit create result is {}";
    public static final String TOKEN_SYMBOL = "tokenSymbol";
    public static final String TOKEN_NAME = "tokenName";
    public static final String MEMO = "memo";
    public static final String ACCOUNT_2 = "account2";
    private static final String ACCOUNT_BALANCE = "ACCOUNT_BALANCE";
    private static final String FUNGIBLE_TOKEN = "TokenA";
    private static final String NFT_TOKEN = "Token_NFT";

    private static final String RECEIVER = "receiver";
    private static final String SENDER = "sender";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);

    public static final String DELEGATE_KEY = "contractKey";
    private static final String CRYPTO_TRANSFER_CONTRACT = "CryptoTransfer";
    private static final String HTS_TRANSFER_FROM_CONTRACT = "HtsTransferFrom";
    private static final String OWNER = "Owner";
    private static final String HTS_TRANSFER_FROM_NFT = "htsTransferFromNFT";
    public static final String TRANSFER_MULTIPLE_TOKENS = "transferMultipleTokens";
    private static final ByteString META1 = ByteStringUtils.wrapUnsafely("meta1".getBytes());
    private static final ByteString META2 = ByteStringUtils.wrapUnsafely("meta2".getBytes());
    private static final ByteString META3 = ByteStringUtils.wrapUnsafely("meta3".getBytes());
    private static final ByteString META4 = ByteStringUtils.wrapUnsafely("meta4".getBytes());
    private static final ByteString META5 = ByteStringUtils.wrapUnsafely("meta5".getBytes());
    private static final ByteString META6 = ByteStringUtils.wrapUnsafely("meta6".getBytes());
    private static final ByteString META7 = ByteStringUtils.wrapUnsafely("meta7".getBytes());
    private static final ByteString META8 = ByteStringUtils.wrapUnsafely("meta8".getBytes());
    private static final String NFT_TOKEN_WITH_FIXED_HBAR_FEE = "nftTokenWithFixedHbarFee";
    private static final String NFT_TOKEN_WITH_FIXED_TOKEN_FEE = "nftTokenWithFixedTokenFee";
    private static final String NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK =
            "nftTokenWithRoyaltyFeeWithHbarFallback";
    private static final String NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK =
            "nftTokenWithRoyaltyFeeWithTokenFallback";
    private static final String FUNGIBLE_TOKEN_FEE = "fungibleTokenFee";
    private static final String RECEIVER_SIGNATURE = "receiverSignature";
    private static final String APPROVE_TXN = "approveTxn";
    private static final String FIRST_MEMO = "firstMemo";
    private static final String SECOND_MEMO = "secondMemo";
    private static final String CRYPTO_TRANSFER_TXN = "cryptoTransferTxn";

    public static void main(String... args) {
        new LeakySecurityModelV1TestsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                /* -- Tests moved from DissociatePrecompileSuite because they require property changes -- */
                dissociatePrecompileHasExpectedSemanticsForDeletedTokens(),
                nestedDissociateWorksAsExpected(),
                multiplePrecompileDissociationWithSigsForFungibleWorks(),
                /* -- Tests moved from CreatePrecompileSuite because they require property changes -- */
                receiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix(),
                fungibleTokenCreateHappyPath(),
                nonFungibleTokenCreateHappyPath(),
                fungibleTokenCreateThenQueryAndTransfer(),
                nonFungibleTokenCreateThenQuery(),
                inheritsSenderAutoRenewAccountIfAnyForNftCreate(),
                inheritsSenderAutoRenewAccountForTokenCreate(),
                createTokenWithDefaultExpiryAndEmptyKeys(),
                /* -- Tests moved from CryptoTransferHTSSuite because they require property changes -- */
                nonNestedCryptoTransferForFungibleToken(),
                activeContractInFrameIsVerifiedWithoutNeedForSignature(),
                cryptoTransferNFTsWithCustomFeesMixedScenario(),
                hapiTransferFromForNFTWithCustomFeesWithApproveForAll(),
                hapiTransferFromForNFTWithCustomFeesWithBothApproveForAllAndAssignedSpender());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    public HapiSpec dissociatePrecompileHasExpectedSemanticsForDeletedTokens() {
        final var tbdUniqToken = "UniqToBeDeleted";
        final var zeroBalanceFrozen = "0bFrozen";
        final var zeroBalanceUnfrozen = "0bUnfrozen";
        final var nonZeroBalanceFrozen = "1bFrozen";
        final var nonZeroBalanceUnfrozen = "1bUnfrozen";
        final var initialSupply = 100L;
        final var nonZeroXfer = 10L;
        final var firstMeta = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
        final var secondMeta = ByteString.copyFrom("SECOND".getBytes(StandardCharsets.UTF_8));
        final var thirdMeta = ByteString.copyFrom("THIRD".getBytes(StandardCharsets.UTF_8));

        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
        final AtomicReference<AccountID> zeroBalanceFrozenID = new AtomicReference<>();
        final AtomicReference<AccountID> zeroBalanceUnfrozenID = new AtomicReference<>();
        final AtomicReference<AccountID> nonZeroBalanceFrozenID = new AtomicReference<>();
        final AtomicReference<AccountID> nonZeroBalanceUnfrozenID = new AtomicReference<>();
        final AtomicReference<TokenID> tbdTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> tbdUniqueTokenID = new AtomicReference<>();

        return propertyPreservingHapiSpec("dissociatePrecompileHasExpectedSemanticsForDeletedTokens")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(10 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(treasuryID::set),
                        tokenCreate(TBD_TOKEN)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(initialSupply)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(MULTI_KEY)
                                .freezeDefault(true)
                                .exposingCreatedIdTo(id -> tbdTokenID.set(asToken(id))),
                        tokenCreate(tbdUniqToken)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> tbdUniqueTokenID.set(asToken(id))),
                        cryptoCreate(zeroBalanceFrozen)
                                .balance(10 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(zeroBalanceFrozenID::set),
                        cryptoCreate(zeroBalanceUnfrozen)
                                .balance(10 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(zeroBalanceUnfrozenID::set),
                        cryptoCreate(nonZeroBalanceFrozen)
                                .balance(10 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(nonZeroBalanceFrozenID::set),
                        cryptoCreate(nonZeroBalanceUnfrozen)
                                .balance(10 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(nonZeroBalanceUnfrozenID::set),
                        uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
                        contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        tokenAssociate(zeroBalanceFrozen, TBD_TOKEN),
                        tokenAssociate(zeroBalanceUnfrozen, TBD_TOKEN),
                        tokenAssociate(nonZeroBalanceFrozen, TBD_TOKEN),
                        tokenAssociate(nonZeroBalanceUnfrozen, TBD_TOKEN),
                        mintToken(tbdUniqToken, List.of(firstMeta, secondMeta, thirdMeta)),
                        getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(3),
                        tokenUnfreeze(TBD_TOKEN, zeroBalanceUnfrozen),
                        tokenUnfreeze(TBD_TOKEN, nonZeroBalanceUnfrozen),
                        tokenUnfreeze(TBD_TOKEN, nonZeroBalanceFrozen),
                        cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY, nonZeroBalanceFrozen)),
                        cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY, nonZeroBalanceUnfrozen)),
                        tokenFreeze(TBD_TOKEN, nonZeroBalanceFrozen),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer),
                        tokenDelete(TBD_TOKEN),
                        tokenDelete(tbdUniqToken),
                        contractCall(
                                        ASSOCIATE_DISSOCIATE_CONTRACT,
                                        "tokenDissociate",
                                        asHeadlongAddress(asAddress(zeroBalanceFrozenID.get())),
                                        asHeadlongAddress(asAddress(tbdTokenID.get())))
                                .alsoSigningWithFullPrefix(zeroBalanceFrozen)
                                .gas(GAS_TO_OFFER)
                                .via("dissociateZeroBalanceFrozenTxn"),
                        getTxnRecord("dissociateZeroBalanceFrozenTxn")
                                .andAllChildRecords()
                                .logged(),
                        contractCall(
                                        ASSOCIATE_DISSOCIATE_CONTRACT,
                                        "tokenDissociate",
                                        asHeadlongAddress(asAddress(zeroBalanceUnfrozenID.get())),
                                        asHeadlongAddress(asAddress(tbdTokenID.get())))
                                .alsoSigningWithFullPrefix(zeroBalanceUnfrozen)
                                .gas(GAS_TO_OFFER)
                                .via("dissociateZeroBalanceUnfrozenTxn"),
                        getTxnRecord("dissociateZeroBalanceUnfrozenTxn")
                                .andAllChildRecords()
                                .logged(),
                        contractCall(
                                        ASSOCIATE_DISSOCIATE_CONTRACT,
                                        "tokenDissociate",
                                        asHeadlongAddress(asAddress(nonZeroBalanceFrozenID.get())),
                                        asHeadlongAddress(asAddress(tbdTokenID.get())))
                                .alsoSigningWithFullPrefix(nonZeroBalanceFrozen)
                                .gas(GAS_TO_OFFER)
                                .via("dissociateNonZeroBalanceFrozenTxn"),
                        getTxnRecord("dissociateNonZeroBalanceFrozenTxn")
                                .andAllChildRecords()
                                .logged(),
                        contractCall(
                                        ASSOCIATE_DISSOCIATE_CONTRACT,
                                        "tokenDissociate",
                                        asHeadlongAddress(asAddress(nonZeroBalanceUnfrozenID.get())),
                                        asHeadlongAddress(asAddress(tbdTokenID.get())))
                                .alsoSigningWithFullPrefix(nonZeroBalanceUnfrozen)
                                .gas(GAS_TO_OFFER)
                                .via("dissociateNonZeroBalanceUnfrozenTxn"),
                        getTxnRecord("dissociateNonZeroBalanceUnfrozenTxn")
                                .andAllChildRecords()
                                .logged(),
                        contractCall(
                                        ASSOCIATE_DISSOCIATE_CONTRACT,
                                        "tokenDissociate",
                                        asHeadlongAddress(asAddress(treasuryID.get())),
                                        asHeadlongAddress(asAddress(tbdUniqueTokenID.get())))
                                .alsoSigningWithFullPrefix(TOKEN_TREASURY)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                "dissociateZeroBalanceFrozenTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "dissociateZeroBalanceUnfrozenTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "dissociateNonZeroBalanceFrozenTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                "dissociateNonZeroBalanceUnfrozenTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        getAccountInfo(zeroBalanceFrozen).hasNoTokenRelationship(TBD_TOKEN),
                        getAccountInfo(zeroBalanceUnfrozen).hasNoTokenRelationship(TBD_TOKEN),
                        getAccountInfo(nonZeroBalanceFrozen).hasNoTokenRelationship(TBD_TOKEN),
                        getAccountInfo(nonZeroBalanceUnfrozen).hasNoTokenRelationship(TBD_TOKEN),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(TBD_TOKEN))
                                .hasNoTokenRelationship(tbdUniqToken)
                                .hasOwnedNfts(0),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer));
    }

    private HapiSpec nestedDissociateWorksAsExpected() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return propertyPreservingHapiSpec("nestedDissociateWorksAsExpected")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(OUTER_CONTRACT, NESTED_CONTRACT),
                        contractCreate(NESTED_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                OUTER_CONTRACT, asHeadlongAddress(getNestedContractAddress(NESTED_CONTRACT, spec))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        contractCall(
                                        OUTER_CONTRACT,
                                        "dissociateAssociateContractCall",
                                        asHeadlongAddress(asAddress(accountID.get())),
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("nestedDissociateTxn")
                                .gas(3_000_000L)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                        getTxnRecord("nestedDissociateTxn").andAllChildRecords().logged())))
                .then(
                        childRecordsCheck(
                                "nestedDissociateTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS))),
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)));
    }

    /* -- HSCS-PREC-007 from HTS Precompile Test Plan -- */
    public HapiSpec multiplePrecompileDissociationWithSigsForFungibleWorks() {
        final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

        return propertyPreservingHapiSpec("multiplePrecompileDissociationWithSigsForFungibleWorks")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY).balance(0L).exposingCreatedIdTo(treasuryID::set),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
                        uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
                        contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(KNOWABLE_TOKEN)),
                        contractCall(
                                        ASSOCIATE_DISSOCIATE_CONTRACT,
                                        "tokensDissociate",
                                        asHeadlongAddress(asAddress(accountID.get())),
                                        new Address[] {
                                            asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                            asHeadlongAddress(asAddress(knowableTokenTokenID.get()))
                                        })
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("multipleDissociationTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord("multipleDissociationTxn")
                                .andAllChildRecords()
                                .logged())))
                .then(
                        childRecordsCheck(
                                "multipleDissociationTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(KNOWABLE_TOKEN));
    }

    private HapiSpec receiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix() {
        final var sendInternalAndDelegateContract = "SendInternalAndDelegate";
        final var justSendContract = "JustSend";
        final var beneficiary = "civilian";
        final var balanceToDistribute = 1_000L;

        final AtomicLong justSendContractNum = new AtomicLong();
        final AtomicLong beneficiaryAccountNum = new AtomicLong();

        return propertyPreservingHapiSpec("ReceiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        cryptoCreate(beneficiary)
                                .balance(0L)
                                .receiverSigRequired(true)
                                .exposingCreatedIdTo(id -> beneficiaryAccountNum.set(id.getAccountNum())),
                        uploadInitCode(sendInternalAndDelegateContract, justSendContract))
                .when(
                        contractCreate(justSendContract).gas(300_000L).exposingNumTo(justSendContractNum::set),
                        contractCreate(sendInternalAndDelegateContract)
                                .gas(300_000L)
                                .balance(balanceToDistribute))
                .then(
                        /* Sending requires receiver signature */
                        sourcing(() -> contractCall(
                                        sendInternalAndDelegateContract,
                                        "sendRepeatedlyTo",
                                        BigInteger.valueOf(justSendContractNum.get()),
                                        BigInteger.valueOf(beneficiaryAccountNum.get()),
                                        BigInteger.valueOf(balanceToDistribute / 2))
                                .hasKnownStatus(INVALID_SIGNATURE)),
                        /* But it's not enough to just sign using an incomplete prefix */
                        sourcing(() -> contractCall(
                                        sendInternalAndDelegateContract,
                                        "sendRepeatedlyTo",
                                        BigInteger.valueOf(justSendContractNum.get()),
                                        BigInteger.valueOf(beneficiaryAccountNum.get()),
                                        BigInteger.valueOf(balanceToDistribute / 2))
                                .signedBy(DEFAULT_PAYER, beneficiary)
                                .hasKnownStatus(INVALID_SIGNATURE)),
                        /* We have to specify the full prefix so the sig can be verified async */
                        getAccountInfo(beneficiary).logged(),
                        sourcing(() -> contractCall(
                                        sendInternalAndDelegateContract,
                                        "sendRepeatedlyTo",
                                        BigInteger.valueOf(justSendContractNum.get()),
                                        BigInteger.valueOf(beneficiaryAccountNum.get()),
                                        BigInteger.valueOf(balanceToDistribute / 2))
                                .alsoSigningWithFullPrefix(beneficiary)),
                        getAccountBalance(beneficiary).logged());
    }

    // TEST-001
    private HapiSpec fungibleTokenCreateHappyPath() {
        final var tokenCreateContractAsKeyDelegate = "tokenCreateContractAsKeyDelegate";
        final var createTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("fungibleTokenCreateHappyPath")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        cryptoCreate(ACCOUNT_2).balance(ONE_HUNDRED_HBARS).key(ECDSA_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .adminKey(CONTRACT_ADMIN_KEY)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .signedBy(CONTRACT_ADMIN_KEY, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenWithKeysAndExpiry",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT_TO_ASSOCIATE))))
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
                                .refusingEthConversion()
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)),
                        newKeyNamed(tokenCreateContractAsKeyDelegate)
                                .shape(DELEGATE_CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS),
                                TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS),
                                TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS)),
                        sourcing(() ->
                                getAccountInfo(ACCOUNT_TO_ASSOCIATE).logged().hasTokenRelationShipCount(1)),
                        sourcing(() -> getTokenInfo(asTokenString(TokenID.newBuilder()
                                        .setTokenNum(createTokenNum.get())
                                        .build()))
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(TOKEN_SYMBOL)
                                .hasName(TOKEN_NAME)
                                .hasDecimals(8)
                                .hasTotalSupply(100)
                                .hasEntityMemo(MEMO)
                                .hasTreasury(ACCOUNT)
                                // Token doesn't inherit contract's auto-renew
                                // account if set in tokenCreate
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(ED25519KEY)
                                .hasKycKey(ED25519KEY)
                                .hasFreezeKey(ECDSA_KEY)
                                .hasWipeKey(ECDSA_KEY)
                                .hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasFeeScheduleKey(tokenCreateContractAsKeyDelegate)
                                .hasPauseKey(CONTRACT_ADMIN_KEY)
                                .hasPauseStatus(TokenPauseStatus.Unpaused)),
                        cryptoDelete(ACCOUNT).hasKnownStatus(ACCOUNT_IS_TREASURY));
    }

    // TEST-002
    private HapiSpec inheritsSenderAutoRenewAccountIfAnyForNftCreate() {
        final var createdNftTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("inheritsSenderAutoRenewAccountIfAnyForNftCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(TOKEN_CREATE_CONTRACT)
                                        .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                        .gas(GAS_TO_OFFER))),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .then(withOpContext((spec, ignore) -> {
                    final var subop1 = balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
                    final var subop2 = contractCall(
                                    TOKEN_CREATE_CONTRACT,
                                    CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION,
                                    HapiParserUtil.asHeadlongAddress(
                                            asAddress(spec.registry().getAccountID(ACCOUNT))),
                                    spec.registry()
                                            .getKey(ED25519KEY)
                                            .getEd25519()
                                            .toByteArray(),
                                    HapiParserUtil.asHeadlongAddress(new byte[20]),
                                    AUTO_RENEW_PERIOD)
                            .via(FIRST_CREATE_TXN)
                            .gas(GAS_TO_OFFER)
                            .payingWith(ACCOUNT)
                            .sending(DEFAULT_AMOUNT_TO_SEND)
                            .refusingEthConversion()
                            .exposingResultTo(result -> {
                                log.info("Explicit create result is" + " {}", result[0]);
                                final var res = (Address) result[0];
                                createdNftTokenNum.set(res.value().longValueExact());
                            })
                            .hasKnownStatus(SUCCESS);

                    allRunFor(
                            spec,
                            subop1,
                            subop2,
                            childRecordsCheck(
                                    FIRST_CREATE_TXN,
                                    SUCCESS,
                                    TransactionRecordAsserts.recordWith().status(SUCCESS)));

                    final var nftInfo = getTokenInfo(asTokenString(TokenID.newBuilder()
                                    .setTokenNum(createdNftTokenNum.get())
                                    .build()))
                            .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                            .logged();

                    allRunFor(spec, nftInfo);
                }));
    }

    private HapiSpec inheritsSenderAutoRenewAccountForTokenCreate() {
        final var createTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("inheritsSenderAutoRenewAccountForTokenCreate")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .adminKey(CONTRACT_ADMIN_KEY)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT) // inherits if the tokenCreateOp doesn't
                                // have
                                // autoRenewAccount
                                .signedBy(CONTRACT_ADMIN_KEY, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenWithKeysAndExpiry",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(new byte[20]), // set empty
                                        // autoRenewAccount
                                        AUTO_RENEW_PERIOD,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT_TO_ASSOCIATE))))
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
                                .refusingEthConversion()
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createTokenNum.set(res.value().longValueExact());
                                }))))
                .then(sourcing(() -> getTokenInfo(asTokenString(TokenID.newBuilder()
                                .setTokenNum(createTokenNum.get())
                                .build()))
                        .logged()
                        .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                        .hasPauseStatus(TokenPauseStatus.Unpaused)));
    }

    // TEST-003 & TEST-019
    private HapiSpec nonFungibleTokenCreateHappyPath() {
        final var createdTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("nonFungibleTokenCreateHappyPath")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER))
                .when(withOpContext((spec, opLog) ->
                        allRunFor(spec, contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))))
                .then(withOpContext((spec, ignore) -> {
                    final var subop1 = balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
                    final var subop2 = contractCall(
                                    TOKEN_CREATE_CONTRACT,
                                    CREATE_NFT_WITH_KEYS_AND_EXPIRY_FUNCTION,
                                    HapiParserUtil.asHeadlongAddress(
                                            asAddress(spec.registry().getAccountID(ACCOUNT))),
                                    spec.registry()
                                            .getKey(ED25519KEY)
                                            .getEd25519()
                                            .toByteArray(),
                                    HapiParserUtil.asHeadlongAddress(
                                            asAddress(spec.registry().getAccountID(ACCOUNT))),
                                    AUTO_RENEW_PERIOD)
                            .via(FIRST_CREATE_TXN)
                            .gas(GAS_TO_OFFER)
                            .payingWith(ACCOUNT)
                            .sending(DEFAULT_AMOUNT_TO_SEND)
                            .exposingResultTo(result -> {
                                log.info("Explicit create result is" + " {}", result[0]);
                                final var res = (Address) result[0];
                                createdTokenNum.set(res.value().longValueExact());
                            })
                            .refusingEthConversion()
                            .hasKnownStatus(SUCCESS);
                    final var subop3 = getTxnRecord(FIRST_CREATE_TXN);
                    allRunFor(
                            spec,
                            subop1,
                            subop2,
                            subop3,
                            childRecordsCheck(
                                    FIRST_CREATE_TXN,
                                    SUCCESS,
                                    TransactionRecordAsserts.recordWith().status(SUCCESS)));

                    final var delta = subop3.getResponseRecord().getTransactionFee();
                    final var subop4 = getAccountBalance(ACCOUNT)
                            .hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta + DEFAULT_AMOUNT_TO_SEND)));
                    final var contractBalanceCheck = getContractInfo(TOKEN_CREATE_CONTRACT)
                            .has(ContractInfoAsserts.contractWith()
                                    .balanceGreaterThan(0L)
                                    .balanceLessThan(DEFAULT_AMOUNT_TO_SEND));
                    final var getAccountTokenBalance = getAccountBalance(ACCOUNT)
                            .hasTokenBalance(
                                    asTokenString(TokenID.newBuilder()
                                            .setTokenNum(createdTokenNum.get())
                                            .build()),
                                    0);
                    final var tokenInfo = getTokenInfo(asTokenString(TokenID.newBuilder()
                                    .setTokenNum(createdTokenNum.get())
                                    .build()))
                            .hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                            .hasSymbol(TOKEN_SYMBOL)
                            .hasName(TOKEN_NAME)
                            .hasDecimals(0)
                            .hasTotalSupply(0)
                            .hasEntityMemo(MEMO)
                            .hasTreasury(ACCOUNT)
                            .hasAutoRenewAccount(ACCOUNT)
                            .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                            .hasSupplyType(TokenSupplyType.FINITE)
                            .hasFreezeDefault(TokenFreezeStatus.Frozen)
                            .hasMaxSupply(10)
                            .searchKeysGlobally()
                            .hasAdminKey(ED25519KEY)
                            .hasSupplyKey(ED25519KEY)
                            .hasPauseKey(ED25519KEY)
                            .hasFreezeKey(ED25519KEY)
                            .hasKycKey(ED25519KEY)
                            .hasFeeScheduleKey(ED25519KEY)
                            .hasWipeKey(ED25519KEY)
                            .hasPauseStatus(TokenPauseStatus.Unpaused)
                            .logged();
                    allRunFor(spec, subop4, getAccountTokenBalance, tokenInfo, contractBalanceCheck);
                }));
    }

    // TEST-005
    private HapiSpec fungibleTokenCreateThenQueryAndTransfer() {
        final var createdTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("fungibleTokenCreateThenQueryAndTransfer")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        cryptoCreate(ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(ED25519KEY)
                                .maxAutomaticTokenAssociations(1),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createTokenThenQueryAndTransfer",
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createdTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS)),
                        sourcing(() -> getAccountBalance(ACCOUNT)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createdTokenNum.get())
                                                .build()),
                                        20)),
                        sourcing(() -> getAccountBalance(TOKEN_CREATE_CONTRACT)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createdTokenNum.get())
                                                .build()),
                                        10)),
                        sourcing(() -> getTokenInfo(asTokenString(TokenID.newBuilder()
                                        .setTokenNum(createdTokenNum.get())
                                        .build()))
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(TOKEN_SYMBOL)
                                .hasName(TOKEN_NAME)
                                .hasDecimals(8)
                                .hasTotalSupply(30)
                                .hasEntityMemo(MEMO)
                                .hasTreasury(TOKEN_CREATE_CONTRACT)
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(ED25519KEY)
                                .hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasPauseKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasPauseStatus(TokenPauseStatus.Unpaused)
                                .logged()));
    }

    // TEST-006
    private HapiSpec nonFungibleTokenCreateThenQuery() {
        final var createdTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("nonFungibleTokenCreateThenQuery")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        "createNonFungibleTokenThenQuery",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createdTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                        getAccountBalance(ACCOUNT).logged(),
                        getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                        getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS),
                                TransactionRecordAsserts.recordWith().status(SUCCESS)),
                        sourcing(() -> getAccountBalance(TOKEN_CREATE_CONTRACT)
                                .hasTokenBalance(
                                        asTokenString(TokenID.newBuilder()
                                                .setTokenNum(createdTokenNum.get())
                                                .build()),
                                        0)),
                        sourcing(() -> getTokenInfo(asTokenString(TokenID.newBuilder()
                                        .setTokenNum(createdTokenNum.get())
                                        .build()))
                                .hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .hasSymbol(TOKEN_SYMBOL)
                                .hasName(TOKEN_NAME)
                                .hasDecimals(0)
                                .hasTotalSupply(0)
                                .hasEntityMemo(MEMO)
                                .hasTreasury(TOKEN_CREATE_CONTRACT)
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
                                .hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
                                .logged()));
    }

    private HapiSpec createTokenWithDefaultExpiryAndEmptyKeys() {
        final var tokenCreateContractAsKeyDelegate = "createTokenWithDefaultExpiryAndEmptyKeys";
        final var createTokenNum = new AtomicLong();
        return propertyPreservingHapiSpec("createTokenWithDefaultExpiryAndEmptyKeys")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(CONTRACT_ADMIN_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ED25519KEY),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_HUNDRED_HBARS)
                                .key(ED25519KEY),
                        cryptoCreate(ACCOUNT_2).balance(ONE_HUNDRED_HBARS).key(ECDSA_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_CREATE_CONTRACT),
                        contractCreate(TOKEN_CREATE_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .adminKey(CONTRACT_ADMIN_KEY)
                                .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                                .signedBy(CONTRACT_ADMIN_KEY, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
                        getContractInfo(TOKEN_CREATE_CONTRACT)
                                .has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
                                .logged())
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(TOKEN_CREATE_CONTRACT, tokenCreateContractAsKeyDelegate)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
                                .exposingResultTo(result -> {
                                    log.info(EXPLICIT_CREATE_RESULT, result[0]);
                                    final var res = (Address) result[0];
                                    createTokenNum.set(res.value().longValueExact());
                                }),
                        newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY).shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)),
                        newKeyNamed(tokenCreateContractAsKeyDelegate)
                                .shape(DELEGATE_CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)))))
                .then(getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged());
    }

    private HapiSpec nonNestedCryptoTransferForFungibleToken() {
        final var cryptoTransferTxn = CRYPTO_TRANSFER_TXN;

        return propertyPreservingHapiSpec("nonNestedCryptoTransferForFungibleToken")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(CRYPTO_TRANSFER_CONTRACT),
                        contractCreate(CRYPTO_TRANSFER_CONTRACT))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                            final var sender = spec.registry().getAccountID(SENDER);
                            final var receiver = spec.registry().getAccountID(RECEIVER);
                            final var amountToBeSent = 50L;

                            allRunFor(
                                    spec,
                                    newKeyNamed(DELEGATE_KEY)
                                            .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, CONTRACT))),
                                    cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                    cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                    contractCall(CRYPTO_TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object)
                                                    new Tuple[] {
                                                        tokenTransferList()
                                                                .forToken(token)
                                                                .withAccountAmounts(
                                                                        accountAmount(sender, -amountToBeSent),
                                                                        accountAmount(receiver, amountToBeSent))
                                                                .build()
                                                    })
                                            .payingWith(GENESIS)
                                            .via(cryptoTransferTxn)
                                            .gas(GAS_TO_OFFER),
                                    contractCall(CRYPTO_TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object)
                                                    new Tuple[] {
                                                        tokenTransferList()
                                                                .forToken(token)
                                                                .withAccountAmounts(
                                                                        accountAmount(sender, -0L),
                                                                        accountAmount(receiver, 0L))
                                                                .build()
                                                    })
                                            .payingWith(GENESIS)
                                            .via("cryptoTransferZero")
                                            .gas(GAS_TO_OFFER));
                        }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged(),
                        getTxnRecord("cryptoTransferZero").andAllChildRecords().logged())
                .then(
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 50),
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 150),
                        getTokenInfo(FUNGIBLE_TOKEN).logged(),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS))
                                                .gasUsed(14085L))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -50)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, 50))));
    }

    private HapiSpec activeContractInFrameIsVerifiedWithoutNeedForSignature() {
        final var revertedFungibleTransferTxn = "revertedFungibleTransferTxn";
        final var successfulFungibleTransferTxn = "successfulFungibleTransferTxn";
        final var revertedNftTransferTxn = "revertedNftTransferTxn";
        final var successfulNftTransferTxn = "successfulNftTransferTxn";
        final var senderStartBalance = 200L;
        final var receiverStartBalance = 0L;
        final var toSendEachTuple = 50L;
        final var multiKey = MULTI_KEY;
        final var senderKey = "senderKey";
        final var contractKey = "contractAdminKey";

        return propertyPreservingHapiSpec("activeContractInFrameIsVerifiedWithoutNeedForSignature")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(multiKey),
                        newKeyNamed(senderKey),
                        newKeyNamed(contractKey),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS).key(senderKey),
                        cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(CRYPTO_TRANSFER_CONTRACT),
                        contractCreate(CRYPTO_TRANSFER_CONTRACT)
                                .payingWith(GENESIS)
                                .adminKey(contractKey)
                                .gas(GAS_TO_OFFER),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(multiKey)
                                .supplyKey(multiKey)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        mintToken(NFT_TOKEN, List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO))),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        tokenAssociate(CRYPTO_TRANSFER_CONTRACT, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
                        cryptoTransfer(
                                moving(senderStartBalance, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN, 1L).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(
                                moving(senderStartBalance, FUNGIBLE_TOKEN)
                                        .between(TOKEN_TREASURY, CRYPTO_TRANSFER_CONTRACT),
                                movingUnique(NFT_TOKEN, 2L).between(TOKEN_TREASURY, CRYPTO_TRANSFER_CONTRACT)))
                .when(withOpContext((spec, opLog) -> {
                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                    final var nftToken = spec.registry().getTokenID(NFT_TOKEN);
                    final var sender = spec.registry().getAccountID(SENDER);
                    final var receiver = spec.registry().getAccountID(RECEIVER);
                    final var contractId = spec.registry().getAccountID(CRYPTO_TRANSFER_CONTRACT);
                    allRunFor(
                            spec,
                            contractCall(CRYPTO_TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                        tokenTransferList()
                                                .forToken(token)
                                                .withAccountAmounts(
                                                        accountAmount(contractId, -toSendEachTuple),
                                                        accountAmount(receiver, toSendEachTuple))
                                                .build(),
                                        tokenTransferList()
                                                .forToken(token)
                                                .withAccountAmounts(
                                                        accountAmount(sender, -toSendEachTuple),
                                                        accountAmount(receiver, toSendEachTuple))
                                                .build()
                                    })
                                    .payingWith(GENESIS)
                                    .via(revertedFungibleTransferTxn)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                            contractCall(CRYPTO_TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                        tokenTransferList()
                                                .forToken(token)
                                                .withAccountAmounts(
                                                        accountAmount(contractId, -toSendEachTuple),
                                                        accountAmount(receiver, toSendEachTuple))
                                                .build(),
                                        tokenTransferList()
                                                .forToken(token)
                                                .withAccountAmounts(
                                                        accountAmount(sender, -toSendEachTuple),
                                                        accountAmount(receiver, toSendEachTuple))
                                                .build()
                                    })
                                    .payingWith(GENESIS)
                                    .alsoSigningWithFullPrefix(senderKey)
                                    .via(successfulFungibleTransferTxn)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(SUCCESS),
                            contractCall(CRYPTO_TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                        tokenTransferList()
                                                .forToken(nftToken)
                                                .withNftTransfers(nftTransfer(contractId, receiver, 2L))
                                                .build(),
                                        tokenTransferList()
                                                .forToken(nftToken)
                                                .withNftTransfers(nftTransfer(sender, receiver, 1L))
                                                .build()
                                    })
                                    .payingWith(GENESIS)
                                    .via(revertedNftTransferTxn)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                            contractCall(CRYPTO_TRANSFER_CONTRACT, TRANSFER_MULTIPLE_TOKENS, (Object) new Tuple[] {
                                        tokenTransferList()
                                                .forToken(nftToken)
                                                .withNftTransfers(nftTransfer(contractId, receiver, 2L))
                                                .build(),
                                        tokenTransferList()
                                                .forToken(nftToken)
                                                .withNftTransfers(nftTransfer(sender, receiver, 1L))
                                                .build()
                                    })
                                    .payingWith(GENESIS)
                                    .via(successfulNftTransferTxn)
                                    .alsoSigningWithFullPrefix(senderKey)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(SUCCESS));
                }))
                .then(
                        getAccountBalance(RECEIVER)
                                .hasTokenBalance(FUNGIBLE_TOKEN, receiverStartBalance + 2 * toSendEachTuple)
                                .hasTokenBalance(NFT_TOKEN, 2L),
                        getAccountBalance(SENDER)
                                .hasTokenBalance(FUNGIBLE_TOKEN, senderStartBalance - toSendEachTuple)
                                .hasTokenBalance(NFT_TOKEN, 0L),
                        getAccountBalance(CRYPTO_TRANSFER_CONTRACT)
                                .hasTokenBalance(FUNGIBLE_TOKEN, senderStartBalance - toSendEachTuple)
                                .hasTokenBalance(NFT_TOKEN, 0L),
                        childRecordsCheck(
                                revertedFungibleTransferTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                successfulFungibleTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(SomeFungibleTransfers.changingFungibleBalances()
                                                .including(FUNGIBLE_TOKEN, SENDER, -toSendEachTuple)
                                                .including(FUNGIBLE_TOKEN, CRYPTO_TRANSFER_CONTRACT, -toSendEachTuple)
                                                .including(FUNGIBLE_TOKEN, RECEIVER, 2 * toSendEachTuple))),
                        childRecordsCheck(
                                revertedNftTransferTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                successfulNftTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))
                                        .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT_TOKEN, SENDER, RECEIVER, 1L)
                                                .including(NFT_TOKEN, CRYPTO_TRANSFER_CONTRACT, RECEIVER, 2L))));
    }

    private HapiSpec cryptoTransferNFTsWithCustomFeesMixedScenario() {
        final var SPENDER_SIGNATURE = "spenderSignature";
        return propertyPreservingHapiSpec("cryptoTransferNFTsWithCustomFeesMixedScenario")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RECEIVER_SIGNATURE),
                        newKeyNamed(SPENDER_SIGNATURE),
                        uploadInitCode(CRYPTO_TRANSFER_CONTRACT),
                        contractCreate(CRYPTO_TRANSFER_CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5)
                                .key(MULTI_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(CRYPTO_TRANSFER_CONTRACT, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(
                                        royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, FUNGIBLE_TOKEN_FEE), OWNER)),
                        tokenAssociate(
                                CRYPTO_TRANSFER_CONTRACT,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        mintToken(NFT_TOKEN_WITH_FIXED_HBAR_FEE, List.of(META1, META2)),
                        mintToken(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, List.of(META3, META4)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, List.of(META5, META6)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, List.of(META7, META8)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L)
                                .between(OWNER, CRYPTO_TRANSFER_CONTRACT)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L)
                                .between(OWNER, CRYPTO_TRANSFER_CONTRACT)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                                .between(OWNER, CRYPTO_TRANSFER_CONTRACT)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                                .between(OWNER, CRYPTO_TRANSFER_CONTRACT)),
                        cryptoTransfer(
                                moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, CRYPTO_TRANSFER_CONTRACT)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, RECEIVER)),
                        cryptoTransfer(TokenMovement.movingHbar(100L).between(OWNER, CRYPTO_TRANSFER_CONTRACT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        CRYPTO_TRANSFER_CONTRACT,
                                        TRANSFER_MULTIPLE_TOKENS,
                                        tokenTransferLists()
                                                .withTokenTransferList(
                                                        tokenTransferList()
                                                                .forToken(spec.registry()
                                                                        .getTokenID(NFT_TOKEN_WITH_FIXED_HBAR_FEE))
                                                                .withNftTransfers(nftTransfer(
                                                                        spec.registry()
                                                                                .getAccountID(CRYPTO_TRANSFER_CONTRACT),
                                                                        spec.registry()
                                                                                .getAccountID(RECEIVER),
                                                                        1L))
                                                                .build(),
                                                        tokenTransferList()
                                                                .forToken(spec.registry()
                                                                        .getTokenID(NFT_TOKEN_WITH_FIXED_TOKEN_FEE))
                                                                .withNftTransfers(nftTransfer(
                                                                        spec.registry()
                                                                                .getAccountID(CRYPTO_TRANSFER_CONTRACT),
                                                                        spec.registry()
                                                                                .getAccountID(RECEIVER),
                                                                        1L))
                                                                .build(),
                                                        tokenTransferList()
                                                                .forToken(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK))
                                                                .withNftTransfers(nftTransfer(
                                                                        spec.registry()
                                                                                .getAccountID(CRYPTO_TRANSFER_CONTRACT),
                                                                        spec.registry()
                                                                                .getAccountID(RECEIVER),
                                                                        1L))
                                                                .build(),
                                                        tokenTransferList()
                                                                .forToken(
                                                                        spec.registry()
                                                                                .getTokenID(
                                                                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK))
                                                                .withNftTransfers(nftTransfer(
                                                                        spec.registry()
                                                                                .getAccountID(CRYPTO_TRANSFER_CONTRACT),
                                                                        spec.registry()
                                                                                .getAccountID(RECEIVER),
                                                                        1L))
                                                                .build())
                                                .build())
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE)
                                .gas(1_000_000L))))
                .then();
    }

    private HapiSpec hapiTransferFromForNFTWithCustomFeesWithApproveForAll() {
        return propertyPreservingHapiSpec("hapiTransferFromForNFTWithCustomFeesWithApproveForAll")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RECEIVER_SIGNATURE),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5)
                                .key(MULTI_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(SENDER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(
                                        royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, FUNGIBLE_TOKEN_FEE), OWNER)),
                        tokenAssociate(
                                SENDER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        mintToken(NFT_TOKEN_WITH_FIXED_HBAR_FEE, List.of(META1, META2)),
                        mintToken(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, List.of(META3, META4)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, List.of(META5, META6)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, List.of(META7, META8)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, RECEIVER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of())
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of())
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of())
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of())
                                .via(APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, SENDER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_HBAR_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_TOKEN_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE))))
                .then();
    }

    private HapiSpec hapiTransferFromForNFTWithCustomFeesWithBothApproveForAllAndAssignedSpender() {
        return propertyPreservingHapiSpec("hapiTransferFromForNFTWithCustomFeesWithBothApproveForAllAndAssignedSpender")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, "10_000_000"),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RECEIVER_SIGNATURE),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5)
                                .key(MULTI_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).key(RECEIVER_SIGNATURE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_HBAR_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHbarFee(1, OWNER)),
                        tokenCreate(FUNGIBLE_TOKEN_FEE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1000L),
                        tokenAssociate(SENDER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN_FEE),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN_FEE),
                        tokenCreate(NFT_TOKEN_WITH_FIXED_TOKEN_FEE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(fixedHtsFee(1, FUNGIBLE_TOKEN_FEE, OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(
                                        royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)),
                        tokenCreate(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(1, FUNGIBLE_TOKEN_FEE), OWNER)),
                        tokenAssociate(
                                SENDER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        tokenAssociate(
                                RECEIVER,
                                List.of(
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK)),
                        mintToken(NFT_TOKEN_WITH_FIXED_HBAR_FEE, List.of(META1, META2)),
                        mintToken(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, List.of(META3, META4)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, List.of(META5, META6)),
                        mintToken(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, List.of(META7, META8)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_HBAR_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(
                                movingUnique(NFT_TOKEN_WITH_FIXED_TOKEN_FEE, 1L).between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        cryptoTransfer(movingUnique(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK, 1L)
                                .between(OWNER, SENDER)),
                        uploadInitCode(HTS_TRANSFER_FROM_CONTRACT),
                        contractCreate(HTS_TRANSFER_FROM_CONTRACT),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, SENDER)),
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN_FEE).between(TOKEN_TREASURY, RECEIVER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_FIXED_HBAR_FEE,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of(1L))
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_FIXED_TOKEN_FEE,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of(1L))
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of(1L))
                                .addNftAllowance(
                                        SENDER,
                                        NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK,
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        true,
                                        List.of(1L))
                                .via(APPROVE_TXN)
                                .signedBy(DEFAULT_PAYER, SENDER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_HBAR_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NFT_TOKEN_WITH_FIXED_TOKEN_FEE))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_HBAR_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE),
                        contractCall(
                                        HTS_TRANSFER_FROM_CONTRACT,
                                        HTS_TRANSFER_FROM_NFT,
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getTokenID(NFT_TOKEN_WITH_ROYALTY_FEE_WITH_TOKEN_FALLBACK))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SENDER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECEIVER))),
                                        BigInteger.valueOf(1L))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(RECEIVER_SIGNATURE))))
                .then();
    }

    /* --- Helpers --- */
    @NotNull
    private String getNestedContractAddress(final String outerContract, final HapiSpec spec) {
        return AssociatePrecompileSuite.getNestedContractAddress(outerContract, spec);
    }
}
