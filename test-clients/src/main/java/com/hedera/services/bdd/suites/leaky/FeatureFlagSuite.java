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
package com.hedera.services.bdd.suites.leaky;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccount.INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiPrng;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.enableAllFeatureFlagsAndDisableContractThrottles;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.A_TOKEN;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_CREATE_SPONSOR;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.NFT_CREATE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.NFT_INFINITE_SUPPLY_TOKEN;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.TOKEN_A_CREATE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.VALID_ALIAS;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hedera.services.bdd.suites.util.UtilPrngSuite.BOB;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.FeatureFlags;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FeatureFlagSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FeatureFlagSuite.class);

    public static void main(String... args) {
        new FeatureFlagSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                disableAllFeatureFlagsAndConfirmNotSupported(),
                enableAllFeatureFlagsAndDisableThrottlesForFurtherCiTesting());
    }

    private HapiSpec disableAllFeatureFlagsAndConfirmNotSupported() {
        return defaultHapiSpec("DisablesAllFeatureFlagsAndConfirmsNotSupported")
                .given(overridingAllOf(FeatureFlags.FEATURE_FLAGS.allDisabled()))
                .when()
                .then(
                        inParallel(
                                confirmAutoCreationNotSupported(),
                                confirmUtilPrngNotSupported(),
                                confirmKeyAliasAutoCreationNotSupported(),
                                confirmHollowAccountCreationNotSupported()));
    }

    private HapiSpec enableAllFeatureFlagsAndDisableThrottlesForFurtherCiTesting() {
        return defaultHapiSpec("EnablesAllFeatureFlagsForFurtherCiTesting")
                .given()
                .when()
                .then(enableAllFeatureFlagsAndDisableContractThrottles());
    }

    private HapiSpecOperation confirmAutoCreationNotSupported() {
        final var aliasKey = "autoCreationKey";
        return UtilVerbs.blockingOrder(
                newKeyNamed(aliasKey),
                cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, aliasKey, ONE_HBAR))
                        .hasKnownStatus(NOT_SUPPORTED));
    }

    private HapiSpecOperation confirmUtilPrngNotSupported() {
        return UtilVerbs.blockingOrder(
                cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                hapiPrng().payingWith(BOB).via("baseTxn").blankMemo().logged(),
                getTxnRecord("baseTxn").hasNoPseudoRandomData(),
                hapiPrng(10).payingWith(BOB).via("plusRangeTxn").blankMemo().logged(),
                getTxnRecord("plusRangeTxn").hasNoPseudoRandomData());
    }

    private HapiSpecOperation confirmHollowAccountCreationNotSupported() {
        return UtilVerbs.blockingOrder(
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                withOpContext(
                        (spec, opLog) -> {
                            final var ecdsaKey =
                                    spec.registry()
                                            .getKey(SECP_256K1_SOURCE_KEY)
                                            .getECDSASecp256K1()
                                            .toByteArray();
                            final var evmAddress =
                                    ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                            final var op =
                                    cryptoTransfer(
                                                    tinyBarsFromTo(
                                                            LAZY_CREATE_SPONSOR,
                                                            evmAddress,
                                                            ONE_HUNDRED_HBARS))
                                            .hasKnownStatus(NOT_SUPPORTED)
                                            .via(TRANSFER_TXN);
                            allRunFor(spec, op);
                        }));
    }

    private HapiSpecOperation confirmKeyAliasAutoCreationNotSupported() {
        final var initialTokenSupply = 1000;
        final var fungibleTokenXfer = "fungibleTokenXfer";
        final var nftXfer = "nftXfer";

        return UtilVerbs.blockingOrder(
                newKeyNamed(VALID_ALIAS),
                newKeyNamed(MULTI_KEY),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(A_TOKEN)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(initialTokenSupply)
                        .treasury(TOKEN_TREASURY)
                        .via(TOKEN_A_CREATE),
                tokenCreate(NFT_INFINITE_SUPPLY_TOKEN)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .adminKey(MULTI_KEY)
                        .supplyKey(MULTI_KEY)
                        .supplyType(TokenSupplyType.INFINITE)
                        .initialSupply(0)
                        .treasury(TOKEN_TREASURY)
                        .via(NFT_CREATE),
                mintToken(
                        NFT_INFINITE_SUPPLY_TOKEN,
                        List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))),
                cryptoCreate(CIVILIAN).balance(10 * ONE_HBAR).maxAutomaticTokenAssociations(2),
                tokenAssociate(CIVILIAN, NFT_INFINITE_SUPPLY_TOKEN),
                cryptoTransfer(
                        moving(100, A_TOKEN).between(TOKEN_TREASURY, CIVILIAN),
                        movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1L, 2L)
                                .between(TOKEN_TREASURY, CIVILIAN)),
                getAccountInfo(CIVILIAN).hasToken(relationshipWith(A_TOKEN).balance(100)),
                getAccountInfo(CIVILIAN).hasToken(relationshipWith(NFT_INFINITE_SUPPLY_TOKEN)),
                cryptoTransfer(moving(10, A_TOKEN).between(CIVILIAN, VALID_ALIAS))
                        .via(fungibleTokenXfer)
                        .payingWith(CIVILIAN)
                        .hasKnownStatus(NOT_SUPPORTED)
                        .logged(),
                cryptoTransfer(
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1, 2)
                                        .between(CIVILIAN, VALID_ALIAS))
                        .via(nftXfer)
                        .payingWith(CIVILIAN)
                        .hasKnownStatus(NOT_SUPPORTED)
                        .logged(),
                getTxnRecord(fungibleTokenXfer)
                        .andAllChildRecords()
                        .hasNonStakingChildRecordCount(0),
                getTxnRecord(nftXfer).andAllChildRecords().hasNonStakingChildRecordCount(0),
                cryptoTransfer(tinyBarsFromToWithAlias(CIVILIAN, VALID_ALIAS, ONE_HBAR))
                        .payingWith(CIVILIAN)
                        .signedBy(CIVILIAN, VALID_ALIAS)
                        .via(TRANSFER_TXN)
                        .hasKnownStatus(NOT_SUPPORTED),
                getTxnRecord(TRANSFER_TXN).andAllChildRecords().hasNonStakingChildRecordCount(0));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
