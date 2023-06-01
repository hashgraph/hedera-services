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

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.ContractKeysStillWorkAsExpectedSuite.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.TBD_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
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

    public static void main(String... args) {
        new LeakySecurityModelV1TestsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                dissociatePrecompileHasExpectedSemanticsForDeletedTokens(),
                nestedDissociateWorksAsExpected(),
                multiplePrecompileDissociationWithSigsForFungibleWorks(),
                receiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    /* -- Moved from CreatePrecompileSuite because it requires property changes -- */
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

    /* -- Moved from DissociatePrecompileSuite because it requires property changes -- */
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

    /* -- Moved from DissociatePrecompileSuite because it requires property changes -- */
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

    /* --- Helpers --- */
    @NotNull
    private String getNestedContractAddress(final String outerContract, final HapiSpec spec) {
        return AssociatePrecompileSuite.getNestedContractAddress(outerContract, spec);
    }
}
