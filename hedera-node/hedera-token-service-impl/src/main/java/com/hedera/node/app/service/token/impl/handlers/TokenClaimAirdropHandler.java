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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_PENDING_AIRDROP_ID_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_PENDING_AIRDROP_ID_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_NFT_AIRDROP_ALREADY_EXISTS;
import static java.util.Objects.requireNonNull;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_CLAIM_AIRDROP}.
 */
@Singleton
public class TokenClaimAirdropHandler implements TransactionHandler {

    @Inject
    public TokenClaimAirdropHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenClaimAirdrop();
        requireNonNull(op);
        final var pendingAirdrops = op.pendingAirdrops();

        final var accountStore = context.createStore(ReadableAccountStore.class);

        for (final var pendingAirdrop : pendingAirdrops) {
            AccountID receiverId = pendingAirdrop.receiverIdOrThrow();
            Account account = accountStore.getAccountById(receiverId);
            requireNonNull(account);
            Key key = account.key();
            requireNonNull(key);
            context.requireKey(key);
        }
    }

    @Override
    public void pureChecks(@NonNull TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);

        final var op = txn.tokenClaimAirdrop();
        requireNonNull(op);

        final List<PendingAirdropId> pendingAirdrops = op.pendingAirdrops();
        if (pendingAirdrops.isEmpty()) {
            throw new PreCheckException(EMPTY_PENDING_AIRDROP_ID_LIST);
        }

        if (pendingAirdrops.size() > 10) {
            throw new PreCheckException(MAX_PENDING_AIRDROP_ID_EXCEEDED);
        }

        final int numAirdrops = pendingAirdrops.size();
        final Set<PendingAirdropId> uniqueAirdrops = Set.copyOf(pendingAirdrops);
        if (numAirdrops != uniqueAirdrops.size()) {
            throw new PreCheckException(PENDING_NFT_AIRDROP_ALREADY_EXISTS);
        }
    }

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {}

    @Override
    public Fees calculateFees(@NonNull FeeContext feeContext) {
        // Always throwing an unsupported exception until we merge the story that will implement the feature flag
        throw new HandleException(NOT_SUPPORTED);
    }
}
