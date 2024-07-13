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

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.util.HapiUtils.functionOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link ThrottleAdviser}.
 */
public class AppThrottleAdviser implements ThrottleAdviser {

    private final NetworkUtilizationManager networkUtilizationManager;
    private final Instant consensusNow;
    private final SavepointStackImpl stack;

    public AppThrottleAdviser(
            @NonNull final NetworkUtilizationManager networkUtilizationManager,
            @NonNull final Instant consensusNow,
            @NonNull final SavepointStackImpl stack) {
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
        this.consensusNow = requireNonNull(consensusNow);
        this.stack = requireNonNull(stack);
    }

    @Override
    public boolean shouldThrottleNOfUnscaled(int n, @NonNull final HederaFunctionality function) {
        requireNonNull(function);
        return networkUtilizationManager.shouldThrottleNOfUnscaled(n, function, consensusNow);
    }

    @Override
    public boolean hasThrottleCapacityForChildTransactions() {
        var isAllowed = true;
        final var childRecords = stack.getChildBuilders();
        @Nullable List<ThrottleUsageSnapshot> snapshotsIfNeeded = null;

        for (int i = 0, n = childRecords.size(); i < n && isAllowed; i++) {
            final var childRecord = childRecords.get(i);
            if (Objects.equals(childRecord.status(), SUCCESS)) {
                final var childTx = childRecord.transaction();
                final var childTxBody = childRecord.transactionBody();
                HederaFunctionality childTxFunctionality;
                try {
                    childTxFunctionality = functionOf(childTxBody);
                } catch (UnknownHederaFunctionality e) {
                    throw new IllegalStateException("Invalid transaction body " + childTxBody, e);
                }

                if (childTxFunctionality == CONTRACT_CREATE || childTxFunctionality == CONTRACT_CALL) {
                    continue;
                }
                if (snapshotsIfNeeded == null) {
                    snapshotsIfNeeded = networkUtilizationManager.getUsageSnapshots();
                }

                final var childTxInfo = TransactionInfo.from(
                        childTx,
                        childTxBody,
                        childTx.sigMapOrElse(SignatureMap.DEFAULT),
                        childTx.signedTransactionBytes(),
                        childTxFunctionality);
                final var shouldThrottleTxn =
                        networkUtilizationManager.shouldThrottle(childTxInfo, stack, consensusNow);
                if (shouldThrottleTxn) {
                    isAllowed = false;
                }
            }
        }
        if (!isAllowed) {
            networkUtilizationManager.resetUsageThrottlesTo(snapshotsIfNeeded);
        }
        return isAllowed;
    }
}
