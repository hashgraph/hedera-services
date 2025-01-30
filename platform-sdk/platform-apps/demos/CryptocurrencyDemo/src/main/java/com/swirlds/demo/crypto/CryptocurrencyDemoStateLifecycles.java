/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.crypto;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * This class handles the lifecycle events for the {@link CryptocurrencyDemoState}.
 */
public class CryptocurrencyDemoStateLifecycles implements StateLifecycles<CryptocurrencyDemoState> {

    @Override
    public void onStateInitialized(
            @NonNull CryptocurrencyDemoState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        if (trigger == InitTrigger.GENESIS) {
            state.genesisInit(platform);
        }
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull CryptocurrencyDemoState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        state.throwIfImmutable();
        round.forEachEventTransaction(
                (event, transaction) -> handleTransaction(event.getCreatorId(), transaction, state));
    }

    /**
     * The matching algorithm for any given stock is as follows. The first bid or ask for a stock is
     * remembered. Then, if there is a higher bid or lower ask, it is remembered, replacing the earlier one.
     * Eventually, there will be a bid that is equal to or greater than the ask. At that point, they are
     * matched, and a trade occurs, selling one share at the average of the bid and ask. Then the stored bid
     * and ask are erased, and it goes back to waiting for a bid or ask to remember.
     * <p>
     * If a member tries to sell a stock for which they own no shares, or if they try to buy a stock at a
     * price higher than the amount of money they currently have, then their bid/ask for that stock will not
     * be stored.
     * <p>
     * A transaction is 1 or 3 bytes:
     *
     * <pre>
     * {SLOW} = run slowly
     * {FAST} = run quickly
     * {BID,s,p} = bid to buy 1 share of stock s at p cents (where 0 &lt;= p &lt;= 127)
     * {ASK,s,p} = ask to sell 1 share of stock s at p cents (where 1 &lt;= p &lt;= 127)
     * </pre>
     */
    private void handleTransaction(
            @NonNull final NodeId id,
            @NonNull final Transaction transaction,
            @NonNull final CryptocurrencyDemoState state) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(transaction, "transaction must not be null");
        if (transaction.isSystem()) {
            return;
        }
        final Bytes contents = transaction.getApplicationTransaction();
        if (contents.length() < 3) {
            return;
        }
        if (contents.getByte(0) == CryptocurrencyDemoState.TransType.slow.ordinal()
                || contents.getByte(0) == CryptocurrencyDemoState.TransType.fast.ordinal()) {
            return;
        }
        final int askBid = contents.getByte(0);
        final int tradeStock = contents.getByte(1);
        int tradePrice = contents.getByte(2);

        if (tradePrice < 1 || tradePrice > 127) {
            return; // all asks and bids must be in the range 1 to 127
        }

        state.handleTransaction(id, askBid, tradeStock, tradePrice);
    }

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull CryptocurrencyDemoState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        // no-op
    }

    @Override
    public boolean onSealConsensusRound(@NonNull Round round, @NonNull CryptocurrencyDemoState state) {
        // no-op
        return true;
    }

    @Override
    public void onUpdateWeight(
            @NonNull CryptocurrencyDemoState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no-op
    }

    @Override
    public void onNewRecoveredState(@NonNull CryptocurrencyDemoState recoveredState) {
        // no-op
    }
}
