/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.utils;

import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Some utilities related to synthetic transaction bodies.
 */
public class SynthTxnUtils {
    public static final Key IMMUTABILITY_SENTINEL_KEY =
            Key.newBuilder().keyList(KeyList.DEFAULT).build();

    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
    public static final Duration DEFAULT_AUTO_RENEW_PERIOD =
            Duration.newBuilder().seconds(THREE_MONTHS_IN_SECONDS).build();
    public static final String LAZY_CREATION_MEMO = "lazy-created account";

    private SynthTxnUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Given a validated {@link ContractCreateTransactionBody} and its pending id, returns the
     * corresponding {@link CryptoCreateTransactionBody} to dispatch.
     *
     * @param pendingId the pending id
     * @param body the {@link ContractCreateTransactionBody}
     * @return the corresponding {@link CryptoCreateTransactionBody}
     */
    public static CryptoCreateTransactionBody synthAccountCreationFromHapi(
            @NonNull final ContractID pendingId,
            @Nullable final com.hedera.pbj.runtime.io.buffer.Bytes evmAddress,
            @NonNull final ContractCreateTransactionBody body) {
        requireNonNull(body);
        requireNonNull(pendingId);
        final var builder = CryptoCreateTransactionBody.newBuilder()
                .maxAutomaticTokenAssociations(body.maxAutomaticTokenAssociations())
                .declineReward(body.declineReward())
                .memo(body.memo());
        if (body.hasAutoRenewPeriod()) {
            builder.autoRenewPeriod(body.autoRenewPeriodOrThrow());
        }
        if (body.hasStakedNodeId()) {
            builder.stakedNodeId(body.stakedNodeIdOrThrow());
        } else if (body.hasStakedAccountId()) {
            builder.stakedAccountId(body.stakedAccountIdOrThrow());
        }
        if (body.hasAdminKey() && !isEmpty(body.adminKeyOrThrow())) {
            builder.key(body.adminKeyOrThrow());
        } else {
            builder.key(Key.newBuilder().contractID(pendingId));
        }
        if (evmAddress != null) {
            builder.alias(evmAddress);
        }
        return builder.build();
    }

    /**
     * Given the "parent" {@link Account} creating a contract and the contract's pending id,
     * returns the corresponding {@link ContractCreateTransactionBody} to dispatch.
     *
     * @param pendingId the pending id
     * @param parent the {@link Account} creating the contract
     * @return the corresponding {@link CryptoCreateTransactionBody}
     */
    public static ContractCreateTransactionBody synthContractCreationFromParent(
            @NonNull final ContractID pendingId, @NonNull final Account parent) {
        requireNonNull(parent);
        requireNonNull(pendingId);
        final var builder = ContractCreateTransactionBody.newBuilder()
                .maxAutomaticTokenAssociations(parent.maxAutoAssociations())
                .declineReward(parent.declineReward())
                .memo(parent.memo())
                .autoRenewPeriod(Duration.newBuilder().seconds(parent.autoRenewSeconds()));
        if (hasNonDegenerateAutoRenewAccountId(parent)) {
            builder.autoRenewAccountId(parent.autoRenewAccountIdOrThrow());
        }
        if (parent.hasStakedNodeId()) {
            builder.stakedNodeId(parent.stakedNodeIdOrThrow());
        } else if (parent.hasStakedAccountId()) {
            builder.stakedAccountId(parent.stakedAccountIdOrThrow());
        }
        final var parentAdminKey = parent.keyOrThrow();
        if (isSelfAdmin(parent)) {
            // The new contract will manage itself as well, which we indicate via self-referential admin key
            builder.adminKey(Key.newBuilder().contractID(pendingId));
        } else {
            builder.adminKey(parentAdminKey);
        }
        return builder.build();
    }

    private static boolean hasNonDegenerateAutoRenewAccountId(@NonNull final Account account) {
        return account.hasAutoRenewAccountId()
                && account.autoRenewAccountIdOrThrow().accountNumOrElse(0L) != 0L;
    }

    /**
     * Given an EVM address being lazy-created, returns the corresponding {@link CryptoCreateTransactionBody}
     * to dispatch.
     *
     * @param evmAddress the EVM address
     * @return the corresponding {@link CryptoCreateTransactionBody}
     */
    public static CryptoCreateTransactionBody synthHollowAccountCreation(@NonNull final Bytes evmAddress) {
        requireNonNull(evmAddress);
        // TODO - for mono-service equivalence, need to set the initial balance here
        return CryptoCreateTransactionBody.newBuilder()
                .initialBalance(0L)
                .alias(evmAddress)
                .key(IMMUTABILITY_SENTINEL_KEY)
                .memo(LAZY_CREATION_MEMO)
                .autoRenewPeriod(DEFAULT_AUTO_RENEW_PERIOD)
                .build();
    }

    /**
     * Given an {@link com.hedera.node.app.hapi.utils.ethereum.EthTxData} representing a {@code CONTRACT_CREATION}
     * call, returns the implied {@link ContractCreateTransactionBody} with the request auto-renew period.
     *
     * @param autoRenewPeriod the auto-renew period
     * @param ethTxData the {@link com.hedera.node.app.hapi.utils.ethereum.EthTxData}
     * @return the corresponding {@link CryptoCreateTransactionBody}
     */
    public static ContractCreateTransactionBody synthEthTxCreation(
            final long autoRenewPeriod, @NonNull final EthTxData ethTxData) {
        requireNonNull(ethTxData);
        return ContractCreateTransactionBody.newBuilder()
                .gas(ethTxData.gasLimit())
                .initialBalance(ethTxData.effectiveTinybarValue())
                .autoRenewPeriod(Duration.newBuilder().seconds(autoRenewPeriod))
                .initcode(Bytes.wrap(ethTxData.callData()))
                .build();
    }

    private static boolean isSelfAdmin(@NonNull final Account parent) {
        final long adminNum =
                parent.keyOrThrow().contractIDOrElse(ContractID.DEFAULT).contractNumOrElse(0L);
        return parent.accountIdOrThrow().accountNumOrThrow() == adminNum;
    }
}
