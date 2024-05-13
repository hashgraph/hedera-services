/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.getNestedContractAddress;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class AssociatePrecompileV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(AssociatePrecompileV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String TOKEN_TREASURY = "treasury";
    private static final String OUTER_CONTRACT = "NestedAssociateDissociate";
    private static final String INNER_CONTRACT = "AssociateDissociate";
    public static final String THE_CONTRACT = "AssociateDissociate";
    private static final String ACCOUNT = "anybody";
    private static final String FROZEN_TOKEN = "Frozen token";
    private static final String UNFROZEN_TOKEN = "Unfrozen token";
    private static final String KYC_TOKEN = "KYC token";
    private static final String FREEZE_KEY = "Freeze key";
    private static final String KYC_KEY = "KYC key";

    public static void main(String... args) {
        new AssociatePrecompileV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<Stream<DynamicTest>> negativeSpecs() {
        return List.of();
    }

    List<Stream<DynamicTest>> positiveSpecs() {
        return List.of(nestedAssociateWorksAsExpected(), multipleAssociatePrecompileWithSignatureWorksForFungible());
    }

    /* -- HSCS-PREC-006 from HTS Precompile Test Plan -- */
    final Stream<DynamicTest> multipleAssociatePrecompileWithSignatureWorksForFungible() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> unfrozenTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return propertyPreservingHapiSpec("multipleAssociatePrecompileWithSignatureWorksForFungible")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(FROZEN_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .freezeKey(FREEZE_KEY)
                                .freezeDefault(true)
                                .exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
                        tokenCreate(UNFROZEN_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .freezeDefault(false)
                                .exposingCreatedIdTo(id -> unfrozenTokenID.set(asToken(id))),
                        tokenCreate(KYC_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .kycKey(KYC_KEY)
                                .exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(THE_CONTRACT),
                        contractCreate(THE_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        THE_CONTRACT,
                                        "tokensAssociate",
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        new Address[] {
                                            HapiParserUtil.asHeadlongAddress(asAddress(frozenTokenID.get())),
                                            HapiParserUtil.asHeadlongAddress(asAddress(unfrozenTokenID.get())),
                                            HapiParserUtil.asHeadlongAddress(asAddress(kycTokenID.get())),
                                            HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get()))
                                        })
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("MultipleTokensAssociationsTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(
                        childRecordsCheck(
                                "MultipleTokensAssociationsTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        getAccountInfo(ACCOUNT)
                                .hasToken(relationshipWith(FROZEN_TOKEN)
                                        .kyc(KycNotApplicable)
                                        .freeze(Frozen))
                                .hasToken(relationshipWith(UNFROZEN_TOKEN)
                                        .kyc(KycNotApplicable)
                                        .freeze(Unfrozen))
                                .hasToken(
                                        relationshipWith(KYC_TOKEN).kyc(Revoked).freeze(FreezeNotApplicable))
                                .hasToken(relationshipWith(TokenAssociationSpecs.VANILLA_TOKEN)
                                        .kyc(KycNotApplicable)
                                        .freeze(FreezeNotApplicable)));
    }

    /* -- HSCS-PREC-010 from HTS Precompile Test Plan -- */
    final Stream<DynamicTest> nestedAssociateWorksAsExpected() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return propertyPreservingHapiSpec("nestedAssociateWorksAsExpected")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenDissociateFromAccount",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(INNER_CONTRACT, OUTER_CONTRACT),
                        contractCreate(INNER_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                OUTER_CONTRACT, asHeadlongAddress(getNestedContractAddress(INNER_CONTRACT, spec))),
                        contractCall(
                                        OUTER_CONTRACT,
                                        "associateDissociateContractCall",
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .alsoSigningWithFullPrefix(ACCOUNT)
                                .via("nestedAssociateTxn")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))))
                .then(
                        childRecordsCheck(
                                "nestedAssociateTxn",
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
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
