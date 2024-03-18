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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LIVE_HASH_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCallLocal;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountDeletion;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountUpdate;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomTransferFromHollowAccount;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomTransferToHollowAccount;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomToken;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenAssociation;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenDeletion;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenDissociation;
import com.hedera.services.bdd.spec.infrastructure.selectors.CustomSelector;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.Function;

public class HollowAccountFuzzingFactory {

    public static final String VANILLA_TOKEN = "TokenD";
    public static final String TOKEN_TREASURY = "treasury";
    public static final String HOLLOW_ACCOUNT = "hollowAccount";

    private static ResponseCodeEnum[] hollowAccountOutcomes = { INVALID_SIGNATURE };

    private static ResponseCodeEnum[] completeAccountOutcomes = {
            SUCCESS, LIVE_HASH_NOT_FOUND,
            INSUFFICIENT_PAYER_BALANCE, UNKNOWN,
            INVALID_AUTORENEW_ACCOUNT, INVALID_TREASURY_ACCOUNT_FOR_TOKEN
    };

    private HollowAccountFuzzingFactory() {
        throw new IllegalStateException("Static factory class");
    }

    public static HapiSpecOperation[] initOperations() {
        return new HapiSpecOperation[] {
                cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                        .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                        .withRecharging(),
        };
    }

    public static Function<HapiSpec, OpProvider> hollowAccountFuzzingWithTransferOperations(
            final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var accounts =
                    new RegistrySourcedNameProvider<>(AccountID.class, spec.registry(), new CustomSelector(HOLLOW_ACCOUNT));
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

            final var hollowAccounts = new RegistrySourcedNameProvider<>(
                    AccountID.class, spec.registry(), new CustomSelector(HOLLOW_ACCOUNT));
            final var tokenCreateAccounts  = new RegistrySourcedNameProvider<>(
                    AccountID.class, spec.registry(), new CustomSelector(UNIQUE_PAYER_ACCOUNT));

            final var tokens = new RegistrySourcedNameProvider<>(TokenID.class, spec.registry(), new RandomSelector());
            var tokenRels = new RegistrySourcedNameProvider<>(
                    TokenAccountRegistryRel.class, spec.registry(), new RandomSelector());
            var allSchedules =
                    new RegistrySourcedNameProvider<>(ScheduleID.class, spec.registry(), new RandomSelector());
            var contracts = new RegistrySourcedNameProvider<>(ContractID.class, spec.registry(), new RandomSelector());
            var calls = new RegistrySourcedNameProvider<>(
                    ActionableContractCall.class, spec.registry(), new RandomSelector());
            var localCalls = new RegistrySourcedNameProvider<>(
                    ActionableContractCallLocal.class, spec.registry(), new RandomSelector());

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(
                            newKeyNamed(HOLLOW_ACCOUNT).shape(SigControl.SECP256K1_ON),
                            generateHollowAccount())

                    // This token create op uses normal accounts to create the tokens because token create will
                    // fail if we use hollow account and not set any tokens in registry but we need them
                    .withOp(new RandomToken(keys, tokens, tokenCreateAccounts, completeAccountOutcomes),
                            intPropOrElse("randomToken.bias", 0, props))

                    .withOp(getRandomTokenAssociationOperation(tokens, hollowAccounts, tokenRels, hollowAccountOutcomes,
                                    props, UNIQUE_PAYER_ACCOUNT),
                            intPropOrElse("randomTokenAssociation.bias", 0, props))

                    .withOp(new RandomAccountUpdate(keys, hollowAccounts, hollowAccountOutcomes, UNIQUE_PAYER_ACCOUNT),
                    intPropOrElse("randomAccountUpdate.bias", 0, props));
            //withOp(
            //        new RandomAccountDeletion(unstableAccounts),
            //        intPropOrElse("randomAccountDeletion.bias", 0, props))
        };
    }

    private static OpProvider getRandomTokenAssociationOperation(
            RegistrySourcedNameProvider<TokenID> tokens,
            RegistrySourcedNameProvider<AccountID> accounts,
            RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels,
            ResponseCodeEnum[] outcomes,
            HapiPropertySource props,
            String... signers) {

        return new RandomTokenAssociation(tokens, accounts, tokenRels, outcomes, signers)
                .ceiling(intPropOrElse(
                        "randomTokenAssociation.ceilingNum",
                        RandomTokenAssociation.DEFAULT_CEILING_NUM,
                        props));
    }

    private static OpProvider getRandomTokenDissociationOperation(
            RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels) {

        return new RandomTokenDissociation(tokenRels, hollowAccountOutcomes, UNIQUE_PAYER_ACCOUNT);
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
            if (!hapiGetTxnRecord.getChildRecords().isEmpty()) {
                final var newAccountID = hapiGetTxnRecord
                        .getFirstNonStakingChildRecord()
                        .getReceipt()
                        .getAccountID();
                spec.registry().saveAccountId(HOLLOW_ACCOUNT, newAccountID);
            }
        });
    }
}
