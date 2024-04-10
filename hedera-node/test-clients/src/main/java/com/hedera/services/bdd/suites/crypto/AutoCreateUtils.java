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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.CRYPTO_TRANSFER_RECEIVER;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_CREATE_SPONSOR;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_MEMO;
import static com.hedera.services.bdd.suites.crypto.AutoAccountUpdateSuite.INITIAL_BALANCE;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.RandomStringUtils;

public class AutoCreateUtils {
    public static ByteString randomValidEd25519Alias() {
        final var alias = RandomStringUtils.random(128, true, true);
        return Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8(alias))
                .build()
                .toByteString();
    }

    public static ByteString randomValidECDSAAlias() {
        final var alias = RandomStringUtils.random(128, true, true);
        return Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFromUtf8(alias))
                .build()
                .toByteString();
    }

    public static Key asKey(final ByteString alias) {
        Key aliasKey;
        try {
            aliasKey = Key.parseFrom(alias);
        } catch (InvalidProtocolBufferException ex) {
            return Key.newBuilder().build();
        }
        return aliasKey;
    }

    public static void updateSpecFor(HapiSpec spec, String alias) {
        var accountIDAtomicReference = new AtomicReference<AccountID>();
        allRunFor(spec, getAliasedAccountInfo(alias).exposingIdTo(accountIDAtomicReference::set));
        final var aliasKey = spec.registry().getKey(alias).toByteString().toStringUtf8();
        spec.registry().saveAccountAlias(aliasKey, accountIDAtomicReference.get());
        spec.registry().saveAccountId(alias, accountIDAtomicReference.get());
    }

    public static HapiSpecOperation[] createHollowAccountFrom(@NonNull final String key) {
        return new HapiSpecOperation[] {
                cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR),
                withOpContext((spec, opLog) -> {
                    final var ecdsaKey =
                            spec.registry().getKey(key).getECDSASecp256K1().toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);
                    final var op2 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith()
                                    .hasEmptyKey()
                                    .expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0, 0)
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false)
                                    .memo(LAZY_MEMO));
                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();
                    allRunFor(spec, op, op2, hapiGetTxnRecord);

                    final AccountID newAccountID = hapiGetTxnRecord
                            .getFirstNonStakingChildRecord()
                            .getReceipt()
                            .getAccountID();
                    spec.registry().saveAccountId(key, newAccountID);
                })
        };
    }

}
