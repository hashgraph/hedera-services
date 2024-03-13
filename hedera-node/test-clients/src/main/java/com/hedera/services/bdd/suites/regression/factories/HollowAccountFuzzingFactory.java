/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression.factories;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomHollowAccount.ACCOUNT_SUFFIX;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.CRYPTO_TRANSFER_RECEIVER;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_CREATE_SPONSOR;
import static com.hedera.services.bdd.suites.regression.factories.AccountCompletionFuzzingFactory.CONTRACT;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomTransferFromHollowAccount;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomTransferToHollowAccount;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomToken;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenAssociation;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenDissociation;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenUpdate;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.Function;

public class HollowAccountFuzzingFactory {

    public static final String VANILLA_TOKEN = "TokenD";
    public static final String TOKEN_TREASURY = "treasury";
    public static final String HOLLOW_ACCOUNT = "hollowAccount";

    private HollowAccountFuzzingFactory() {
        throw new IllegalStateException("Static factory class");
    }

    public static HapiSpecOperation[] initOperations() {
        return new HapiSpecOperation[] {
            cryptoCreate(LAZY_CREATE_SPONSOR)
                    .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                    .withRecharging(),
            cryptoCreate(CRYPTO_TRANSFER_RECEIVER)
                    .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                    .withRecharging(),
            cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                    .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                    .withRecharging(),
            uploadInitCode(CONTRACT),
            cryptoCreate(TOKEN_TREASURY).balance(0L),
            tokenCreate(VANILLA_TOKEN).treasury(TOKEN_TREASURY),
            contractCreate(CONTRACT)
        };
    }

    public static Function<HapiSpec, OpProvider> hollowAccountFuzzingWithTransferFailedOperations(
            final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var accounts =
                    new RegistrySourcedNameProvider<>(AccountID.class, spec.registry(), new RandomSelector());
            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(
                            newKeyNamed(HOLLOW_ACCOUNT).shape(SigControl.SECP256K1_ON), generateHollowAccount())
                    .withOp(
                            new RandomTransferFromHollowAccount(spec.registry(), accounts),
                            intPropOrElse("randomTransferFromHollowAccount.bias", 0, props))
                    .withOp(
                            new RandomTransferToHollowAccount(spec.registry(), accounts),
                            intPropOrElse("randomTransfer.bias", 0, props));
        };
    }

    public static Function<HapiSpec, OpProvider> hollowAccountFuzzingWithTokenOperations(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var accounts =
                    new RegistrySourcedNameProvider<>(AccountID.class, spec.registry(), new RandomSelector());

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            final var tokens = new RegistrySourcedNameProvider<>(TokenID.class, spec.registry(), new RandomSelector());
            var tokenRels = new RegistrySourcedNameProvider<>(
                    TokenAccountRegistryRel.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(
                            newKeyNamed(HOLLOW_ACCOUNT).shape(SigControl.SECP256K1_ON), generateHollowAccount())
                    .withOp(new RandomToken(keys, tokens, accounts), intPropOrElse("randomToken.bias", 0, props))
                    .withOp(
                            new RandomTokenAssociation(tokens, accounts, tokenRels)
                                    .ceiling(intPropOrElse(
                                            "randomTokenAssociation.ceilingNum",
                                            RandomTokenAssociation.DEFAULT_CEILING_NUM,
                                            props)),
                            intPropOrElse("randomTokenAssociation.bias", 0, props))
                    .withOp(
                            new RandomTokenDissociation(tokenRels),
                            intPropOrElse("randomTokenDissociation.bias", 0, props))
                    //
                    //                    .withOp(new RandomTokenDeletion(tokens),
                    // intPropOrElse("randomTokenDeletion.bias", 0, props))
                    //                    .withOp(new RandomTokenTransfer(tokenRels),
                    // intPropOrElse("randomTokenTransfer.bias", 0, props))
                    //                    .withOp(new RandomTokenFreeze(tokenRels),
                    // intPropOrElse("randomTokenFreeze.bias", 0, props))
                    //                    .withOp(new RandomTokenUnfreeze(tokenRels),
                    // intPropOrElse("randomTokenUnfreeze.bias", 0, props))
                    //                    .withOp(new RandomTokenKycGrant(tokenRels),
                    // intPropOrElse("randomTokenKycGrant.bias", 0, props))
                    //                    .withOp(new RandomTokenKycRevoke(tokenRels),
                    // intPropOrElse("randomTokenKycRevoke.bias", 0, props));
                    //                    // sign with the hollow
                    //                    .withOp(new RandomTokenMint(tokens), intPropOrElse("randomTokenMint.bias", 0,
                    // props))
                    //                    // sign with the hollow
                    //                    .withOp(new RandomTokenBurn(tokens), intPropOrElse("randomTokenBurn.bias", 0,
                    // props))
                    .withOp(
                            new RandomTokenUpdate(keys, tokens, accounts),
                            intPropOrElse("randomTokenUpdate.bias", 0, props));
            //                    .withOp(
            //                            new RandomTokenAccountWipe(tokenRels),
            //                            intPropOrElse("randomTokenAccountWipe.bias", 0, props))
            //                    .withOp(new RandomTokenTransfer(tokenRels), intPropOrElse("randomTransfer.bias", 0,
            // props));
        };
    }

    public static HapiSpecOperation generateHollowAccount() {
        return withOpContext((spec, opLog) -> {
            final var ecdsaKey =
                    spec.registry().getKey(HOLLOW_ACCOUNT).getECDSASecp256K1().toByteArray();
            final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
            final var op = cryptoTransfer(tinyBarsFromTo(GENESIS, evmAddress, ONE_HUNDRED_HBARS))
                    .hasKnownStatusFrom(SUCCESS)
                    .via("txn" + HOLLOW_ACCOUNT);

            final HapiGetTxnRecord hapiGetTxnRecord =
                    getTxnRecord("txn" + HOLLOW_ACCOUNT).andAllChildRecords().assertingNothingAboutHashes();

            allRunFor(spec, op, hapiGetTxnRecord);

            final var newAccountID = hapiGetTxnRecord
                    .getFirstNonStakingChildRecord()
                    .getReceipt()
                    .getAccountID();
            spec.registry().saveAccountId(HOLLOW_ACCOUNT + ACCOUNT_SUFFIX, newAccountID);
        });
    }
}
