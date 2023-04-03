/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
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
import static com.hedera.services.bdd.suites.contract.Utils.asHexedAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite.THE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite.TOKEN_ASSOCIATE_FUNCTION;
import static com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSSuite.ALICE;
import static com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSSuite.BURN_TOKEN_WITH_EVENT;
import static com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSSuite.CREATION_TX;
import static com.hedera.services.bdd.suites.contract.precompile.ContractBurnHTSSuite.THE_BURN_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.ContractMintHTSSuite.MINT_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.ContractMintHTSSuite.MINT_FUNGIBLE_TOKEN_WITH_EVENT;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ACCOUNT_TO_ASSOCIATE;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ACCOUNT_TO_ASSOCIATE_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.DEFAULT_AMOUNT_TO_SEND;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ECDSA_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ED25519KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CryptoTransferHTSSuite.DELEGATE_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.DeleteTokenPrecompileSuite.DELETE_TOKEN_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.DeleteTokenPrecompileSuite.TOKEN_DELETE_FUNCTION;
import static com.hedera.services.bdd.suites.contract.precompile.FreezeUnfreezeTokenPrecompileSuite.FREEZE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.FreezeUnfreezeTokenPrecompileSuite.TOKEN_FREEZE_FUNC;
import static com.hedera.services.bdd.suites.contract.precompile.FreezeUnfreezeTokenPrecompileSuite.TOKEN_UNFREEZE_FUNC;
import static com.hedera.services.bdd.suites.contract.precompile.GrantRevokeKycSuite.GRANT_REVOKE_KYC_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.GrantRevokeKycSuite.SECOND_ACCOUNT;
import static com.hedera.services.bdd.suites.contract.precompile.GrantRevokeKycSuite.TOKEN_GRANT_KYC;
import static com.hedera.services.bdd.suites.contract.precompile.GrantRevokeKycSuite.TOKEN_REVOKE_KYC;
import static com.hedera.services.bdd.suites.contract.precompile.PauseUnpauseTokenAccountPrecompileSuite.PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.PauseUnpauseTokenAccountPrecompileSuite.PAUSE_UNPAUSE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.PauseUnpauseTokenAccountPrecompileSuite.UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.TokenUpdatePrecompileSuite.CUSTOM_MEMO;
import static com.hedera.services.bdd.suites.contract.precompile.TokenUpdatePrecompileSuite.CUSTOM_NAME;
import static com.hedera.services.bdd.suites.contract.precompile.TokenUpdatePrecompileSuite.CUSTOM_SYMBOL;
import static com.hedera.services.bdd.suites.contract.precompile.TokenUpdatePrecompileSuite.TOKEN_UPDATE_AS_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.TokenUpdatePrecompileSuite.TOKEN_UPDATE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.ADMIN_ACCOUNT;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.GAS_TO_OFFER;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.WIPE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.WIPE_FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.WIPE_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FREEZE_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.KYC_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.PAUSE_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TopLevelSigsCanBeToggledByPrecompileTypeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TopLevelSigsCanBeToggledByPrecompileTypeSuite.class);

    public static void main(String... args) {
        new TopLevelSigsCanBeToggledByPrecompileTypeSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                canToggleTopLevelSigUsageForAssociatePrecompile(),
                canToggleTopLevelSigUsageForBurnPrecompile(),
                canToggleTopLevelSigUsageForMintPrecompile(),
                canToggleTopLevelSigUsageForDeletePrecompile(),
                canToggleTopLevelSigUsageForFreezeAndUnfreezePrecompile(),
                canToggleTopLevelSigUsageForGrantKycAndRevokeKycPrecompile(),
                canToggleTopLevelSigUsageForPauseAndUnpausePrecompile(),
                canToggleTopLevelSigUsageForUpdatePrecompile(),
                canToggleTopLevelSigUsageForWipePrecompile());
    }

    private HapiSpec canToggleTopLevelSigUsageForWipePrecompile() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
        final var failedWipeTxn = "failedWipeTxn";
        final var succeededWipeTxn = "succeededWipeTxn";

        final AtomicReference<AccountID> adminAccountID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("CanToggleTopLevelSigUsageForWipePrecompile")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(ADMIN_ACCOUNT).exposingCreatedIdTo(adminAccountID::set),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .wipeKey(WIPE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(WIPE_CONTRACT),
                        contractCreate(WIPE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Trying to wipe token with top-level signatures should fail
                        sourcing(() -> contractCall(
                                        WIPE_CONTRACT,
                                        WIPE_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        10L)
                                .payingWith(ADMIN_ACCOUNT)
                                .alsoSigningWithFullPrefix(WIPE_KEY)
                                .via(failedWipeTxn)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // But now restore use of top-level signatures for the token wipe precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenAccountWipe"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        WIPE_CONTRACT,
                                        WIPE_FUNGIBLE_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(accountID.get())),
                                        10L)
                                .payingWith(ADMIN_ACCOUNT)
                                .alsoSigningWithFullPrefix(WIPE_KEY)
                                .via(succeededWipeTxn)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedWipeTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)));
    }

    private HapiSpec canToggleTopLevelSigUsageForUpdatePrecompile() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
        final var failedUpdateTxn = "failedUpdateTxn";
        final var succeededUpdateTxn = "succeededUpdateTxn";

        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("CanToggleTopLevelSigUsageForWipePrecompile")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY).shape(ED25519_ON),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(MULTI_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        grantTokenKyc(VANILLA_TOKEN, ACCOUNT),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Trying to update token with top-level signatures should fail
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                TOKEN_UPDATE_CONTRACT,
                                                "updateTokenWithAllFields",
                                                HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(ACCOUNT))),
                                                spec.registry()
                                                        .getKey(ED25519KEY)
                                                        .getEd25519()
                                                        .toByteArray(),
                                                spec.registry()
                                                        .getKey(ECDSA_KEY)
                                                        .getECDSASecp256K1()
                                                        .toByteArray(),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(TOKEN_UPDATE_CONTRACT))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(ACCOUNT))),
                                                AUTO_RENEW_PERIOD,
                                                CUSTOM_NAME,
                                                CUSTOM_SYMBOL,
                                                CUSTOM_MEMO)
                                        .via(failedUpdateTxn)
                                        .gas(GAS_TO_OFFER)
                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                        .payingWith(ACCOUNT)
                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                // But now restore use of top-level signatures for
                                // the token update precompile
                                overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenUpdate"),
                                contractCall(
                                                TOKEN_UPDATE_CONTRACT,
                                                "updateTokenWithAllFields",
                                                HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(ACCOUNT))),
                                                spec.registry()
                                                        .getKey(ED25519KEY)
                                                        .getEd25519()
                                                        .toByteArray(),
                                                spec.registry()
                                                        .getKey(ECDSA_KEY)
                                                        .getECDSASecp256K1()
                                                        .toByteArray(),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(TOKEN_UPDATE_CONTRACT))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(ACCOUNT))),
                                                AUTO_RENEW_PERIOD,
                                                CUSTOM_NAME,
                                                CUSTOM_SYMBOL,
                                                CUSTOM_MEMO)
                                        .via(succeededUpdateTxn)
                                        .gas(GAS_TO_OFFER)
                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                        .payingWith(ACCOUNT)
                                        .hasKnownStatus(SUCCESS))),
                        newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT.signedWith(TOKEN_UPDATE_CONTRACT)),
                        newKeyNamed(TOKEN_UPDATE_AS_KEY).shape(CONTRACT.signedWith(TOKEN_UPDATE_CONTRACT)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedUpdateTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)));
    }

    private HapiSpec canToggleTopLevelSigUsageForPauseAndUnpausePrecompile() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
        final var failedPauseTxn = "failedPauseTxn";
        final var failedUnpauseTxn = "failedUnpauseTxn";
        final var succeededPauseTxn = "succeededPauseTxn";
        final var succeededUnpauseTxn = "succeededUnpauseTxn";

        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();

        return propertyPreservingHapiSpec("CanToggleTopLevelSigUsageForPauseAndUnpausePrecompile")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(PAUSE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HBAR).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .pauseKey(PAUSE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Trying to pause with top-level signatures should fail
                        sourcing(() -> contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(PAUSE_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(failedPauseTxn)),
                        // But now restore use of top-level signatures for the pause precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenPause"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        PAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .hasKnownStatus(SUCCESS)
                                .alsoSigningWithFullPrefix(PAUSE_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(succeededPauseTxn)),
                        // revoke use of top-level signatures from all precompiles again
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(PAUSE_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(failedUnpauseTxn)),
                        // But now restore use of top-level signatures for the unpause precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenUnpause"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        PAUSE_UNPAUSE_CONTRACT,
                                        UNPAUSE_TOKEN_ACCOUNT_FUNCTION_NAME,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())))
                                .hasKnownStatus(SUCCESS)
                                .alsoSigningWithFullPrefix(PAUSE_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(succeededUnpauseTxn)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedPauseTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)),
                        childRecordsCheck(
                                failedUnpauseTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)));
    }

    private HapiSpec canToggleTopLevelSigUsageForAssociatePrecompile() {
        final var tokenToAssociate = "tokenToAssociate";
        final var accountToBeAssociated = "accountToBeAssociated";
        final var failedAssociateTxn = "failedAssociateTxn";
        final var succeededAssociateTxn = "succeededAssociateTxn";
        final AtomicReference<Address> accountAddress = new AtomicReference<>();
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        return propertyPreservingHapiSpec("CanToggleTopLevelSigUsageForAssociatePrecompile")
                .preserving("contracts.allowSystemUseOfHapiSigs")
                .given(
                        uploadInitCode(THE_CONTRACT),
                        contractCreate(THE_CONTRACT),
                        cryptoCreate(accountToBeAssociated)
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(accountAddress::set),
                        tokenCreate(tokenToAssociate).exposingAddressTo(tokenAddress::set),
                        // First revoke use of top-level signatures from all precompiles
                        overriding("contracts.allowSystemUseOfHapiSigs", ""))
                .when(
                        // Trying to associate with top-level signatures should fail
                        sourcing(() -> contractCall(
                                        THE_CONTRACT,
                                        TOKEN_ASSOCIATE_FUNCTION,
                                        accountAddress.get(),
                                        tokenAddress.get())
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(accountToBeAssociated)
                                .via(failedAssociateTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // But now restore use of top-level signatures for the associate precompile
                        overriding("contracts.allowSystemUseOfHapiSigs", "TokenAssociateToAccount"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        THE_CONTRACT,
                                        TOKEN_ASSOCIATE_FUNCTION,
                                        accountAddress.get(),
                                        tokenAddress.get())
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(accountToBeAssociated)
                                .via(succeededAssociateTxn)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedAssociateTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    private HapiSpec canToggleTopLevelSigUsageForBurnPrecompile() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
        final var failedBurnTxn = "failedBurnTxn";
        final var succeededBurnTxn = "succeededBurnTxn";

        return propertyPreservingHapiSpec("CanToggleTopLevelSigUsageForBurnPrecompile")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(SUPPLY_KEY)
                                .adminKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(THE_BURN_CONTRACT),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                                THE_BURN_CONTRACT,
                                                asHeadlongAddress(asHexedAddress(
                                                        spec.registry().getTokenID(TOKEN))))
                                        .payingWith(ALICE)
                                        .via(CREATION_TX)
                                        .gas(GAS_TO_OFFER))),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Trying to burn with top-level signatures should fail
                        sourcing(() -> contractCall(
                                        THE_BURN_CONTRACT, BURN_TOKEN_WITH_EVENT, BigInteger.valueOf(10L), new long[0])
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(SUPPLY_KEY)
                                .gas(GAS_TO_OFFER)
                                .via(failedBurnTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // But now restore use of top-level signatures for the burn precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenBurn"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        THE_BURN_CONTRACT, BURN_TOKEN_WITH_EVENT, BigInteger.valueOf(10L), new long[0])
                                .payingWith(ALICE)
                                .alsoSigningWithFullPrefix(SUPPLY_KEY)
                                .gas(GAS_TO_OFFER)
                                .via(succeededBurnTxn)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedBurnTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    private HapiSpec canToggleTopLevelSigUsageForMintPrecompile() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
        final var tokenToMint = "tokenToMint";
        final var failedMintTxn = "failedMintTxn";
        final var succeededMintTxn = "succeededMintTxn";

        final AtomicReference<Address> accountAddress = new AtomicReference<>();
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        return propertyPreservingHapiSpec("CanToggleTopLevelSigUsageForMintPrecompile")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate("mint account")
                                .keyShape(SECP256K1_ON)
                                .exposingEvmAddressTo(accountAddress::set)
                                .balance(ONE_MILLION_HBARS)
                                .payingWith(GENESIS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(tokenToMint)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(HapiPropertySource.asToken(idLit))),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Trying to mint with top-level signatures should fail
                        sourcing(() -> contractCall(
                                        MINT_CONTRACT, MINT_FUNGIBLE_TOKEN_WITH_EVENT, BigInteger.valueOf(10L))
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(SUPPLY_KEY)
                                .via(failedMintTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // But now restore use of top-level signatures for the mint precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenMint"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        MINT_CONTRACT, MINT_FUNGIBLE_TOKEN_WITH_EVENT, BigInteger.valueOf(10L))
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(SUPPLY_KEY)
                                .via(succeededMintTxn)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedMintTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    private HapiSpec canToggleTopLevelSigUsageForDeletePrecompile() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
        final var failedDeleteTxn = "failedDeleteTxn";
        final var succeededDeleteTxn = "succeededDeleteTxn";
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return propertyPreservingHapiSpec("CanToggleTopLevelSigUsageForDeletePrecompile")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .key(MULTI_KEY)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(HapiPropertySource.asToken(id)))
                                .initialSupply(1110),
                        uploadInitCode(DELETE_TOKEN_CONTRACT),
                        contractCreate(DELETE_TOKEN_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Trying to delete with top-level signatures should fail
                        sourcing(() -> contractCall(
                                        DELETE_TOKEN_CONTRACT,
                                        TOKEN_DELETE_FUNCTION,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .via(failedDeleteTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // But now restore use of top-level signatures for the delete precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenDelete"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        DELETE_TOKEN_CONTRACT,
                                        TOKEN_DELETE_FUNCTION,
                                        asHeadlongAddress(asHexedAddress(vanillaTokenID.get())))
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .via(succeededDeleteTxn)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedDeleteTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)));
    }

    private HapiSpec canToggleTopLevelSigUsageForFreezeAndUnfreezePrecompile() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
        final var failedFreezeTxn = "failedFreezeTxn";
        final var failedUnfreezeTxn = "failedUnfreezeTxn";
        final var succeededFreezeTxn = "succeededFreezeTxn";
        final var succeededUnfreezeTxn = "succeededUnfreezeTxn";

        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();

        return propertyPreservingHapiSpec("CanToggleTopLevelSigUsageForFreezeAndUnfreezePrecompile")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HBAR).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .freezeKey(FREEZE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Trying to freezing with top-level signatures should fail
                        sourcing(() -> contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_FREEZE_FUNC,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        asHeadlongAddress(asAddress(accountID.get())))
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(FREEZE_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(failedFreezeTxn)),
                        // But now restore use of top-level signatures for the freeze precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenFreezeAccount"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_FREEZE_FUNC,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        asHeadlongAddress(asAddress(accountID.get())))
                                .hasKnownStatus(SUCCESS)
                                .alsoSigningWithFullPrefix(FREEZE_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(succeededFreezeTxn)),
                        // revoke use of top-level signatures from all precompiles again
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_UNFREEZE_FUNC,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        asHeadlongAddress(asAddress(accountID.get())))
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(FREEZE_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(failedUnfreezeTxn)),
                        // But now restore use of top-level signatures for the unfreeze precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenUnfreezeAccount"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        FREEZE_CONTRACT,
                                        TOKEN_UNFREEZE_FUNC,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        asHeadlongAddress(asAddress(accountID.get())))
                                .hasKnownStatus(SUCCESS)
                                .alsoSigningWithFullPrefix(FREEZE_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(succeededUnfreezeTxn)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedFreezeTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)),
                        childRecordsCheck(
                                failedUnfreezeTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)));
    }

    private HapiSpec canToggleTopLevelSigUsageForGrantKycAndRevokeKycPrecompile() {
        final String ALLOW_SYSTEM_USE_OF_HAPI_SIGS = "contracts.allowSystemUseOfHapiSigs";
        final var failedGrantTxn = "failedGrantTxn";
        final var failedRevokeTxn = "failedRevokeTxn";
        final var succeededGrantTxn = "succeededGrantTxn";
        final var succeededRevokeTxn = "succeededRevokeTxn";

        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> secondAccountID = new AtomicReference<>();

        return propertyPreservingHapiSpec("canToggleTopLevelSigUsageForGrantKycAndRevokeKycPrecompile")
                .preserving(ALLOW_SYSTEM_USE_OF_HAPI_SIGS)
                .given(
                        newKeyNamed(KYC_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .key(KYC_KEY)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(SECOND_ACCOUNT).exposingCreatedIdTo(secondAccountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .kycKey(KYC_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(GRANT_REVOKE_KYC_CONTRACT),
                        contractCreate(GRANT_REVOKE_KYC_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(SECOND_ACCOUNT, VANILLA_TOKEN),
                        // First revoke use of top-level signatures from all precompiles
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""))
                .when(
                        // Trying to grant kyc with top-level signatures should fail
                        sourcing(() -> contractCall(
                                        GRANT_REVOKE_KYC_CONTRACT,
                                        TOKEN_GRANT_KYC,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        asHeadlongAddress(asAddress(secondAccountID.get())))
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(KYC_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(failedGrantTxn)),
                        // But now restore use of top-level signatures for the grant kyc precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenGrantKycToAccount"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        GRANT_REVOKE_KYC_CONTRACT,
                                        TOKEN_GRANT_KYC,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        asHeadlongAddress(asAddress(secondAccountID.get())))
                                .hasKnownStatus(SUCCESS)
                                .alsoSigningWithFullPrefix(KYC_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(succeededGrantTxn)),
                        // revoke use of top-level signatures from all precompiles again
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, ""),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        GRANT_REVOKE_KYC_CONTRACT,
                                        TOKEN_REVOKE_KYC,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        asHeadlongAddress(asAddress(secondAccountID.get())))
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .alsoSigningWithFullPrefix(KYC_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(failedRevokeTxn)),
                        // But now restore use of top-level signatures for the revoke kyc precompile
                        overriding(ALLOW_SYSTEM_USE_OF_HAPI_SIGS, "TokenRevokeKycToAccount"),
                        // Now the same call should succeed
                        sourcing(() -> contractCall(
                                        GRANT_REVOKE_KYC_CONTRACT,
                                        TOKEN_REVOKE_KYC,
                                        asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        asHeadlongAddress(asAddress(secondAccountID.get())))
                                .hasKnownStatus(SUCCESS)
                                .alsoSigningWithFullPrefix(KYC_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .via(succeededRevokeTxn)))
                .then(
                        // Confirm the failure was due to the top-level signature being unavailable
                        childRecordsCheck(
                                failedGrantTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)),
                        childRecordsCheck(
                                failedRevokeTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
