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

package com.hedera.node.app.blocks.impl;

import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.swirlds.state.StateChangeListener.StateType.QUEUE;
import static com.swirlds.state.StateChangeListener.StateType.SINGLETON;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.QueuePopChange;
import com.hedera.hapi.block.stream.output.QueuePushChange;
import com.hedera.hapi.block.stream.output.SingletonUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.recordcache.TransactionReceiptEntries;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.pbj.runtime.OneOf;
import com.swirlds.state.StateChangeListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.inject.Singleton;

/**
 * A state change listener that accumulates state changes that are only reported at a block boundary (either
 * at the beginning or end of the block); either because all that affects the root hash is the latest value in
 * state, or it is simply more efficient to report them in bulk. In the current system, these are the singleton
 * and queue updates.
 */
@Singleton
public class BoundaryStateChangeListener implements StateChangeListener {
    private static final Set<StateType> TARGET_DATA_TYPES = EnumSet.of(SINGLETON, QUEUE);

    private final SortedMap<Integer, StateChange> singletonUpdates = new TreeMap<>();
    private final SortedMap<Integer, List<StateChange>> queueUpdates = new TreeMap<>();

    @Nullable
    private Instant lastUsedConsensusTime;

    /**
     * Returns a {@link BlockItem} containing all the state changes that have been accumulated.
     * @return the block item
     */
    public BlockItem flushChanges() {
        final var stateChanges = StateChanges.newBuilder()
                .stateChanges(allStateChanges())
                .consensusTimestamp(endOfBlockTimestamp())
                .build();
        lastUsedConsensusTime = null;
        singletonUpdates.clear();
        queueUpdates.clear();
        return BlockItem.newBuilder().stateChanges(stateChanges).build();
    }

    /**
     * Returns all the state changes that have been accumulated.
     * @return the state changes
     */
    public List<StateChange> allStateChanges() {
        final var allStateChanges = new LinkedList<StateChange>();
        for (final var entry : singletonUpdates.entrySet()) {
            allStateChanges.add(entry.getValue());
        }
        for (final var entry : queueUpdates.entrySet()) {
            allStateChanges.addAll(entry.getValue());
        }
        return allStateChanges;
    }

    /**
     * Returns the consensus timestamp at the end of the block.
     * @return the consensus timestamp
     */
    public @NonNull Timestamp endOfBlockTimestamp() {
        return asTimestamp(lastUsedConsensusTime.plusNanos(1));
    }

    /**
     * Sets the last used consensus time in the round.
     * @param lastUsedConsensusTime the last used consensus time
     */
    public void setLastUsedConsensusTime(@NonNull final Instant lastUsedConsensusTime) {
        this.lastUsedConsensusTime = requireNonNull(lastUsedConsensusTime);
    }

    @Override
    public Set<StateType> stateTypes() {
        return TARGET_DATA_TYPES;
    }

    @Override
    public int stateIdFor(@NonNull final String serviceName, @NonNull final String stateKey) {
        return BlockImplUtils.stateIdFor(serviceName, stateKey);
    }

    @Override
    public <V> void queuePushChange(final int stateId, @NonNull final V value) {
        requireNonNull(value);
        final var stateChange = StateChange.newBuilder()
                .stateId(stateId)
                .queuePush(new QueuePushChange(queuePushChangeValueFor(value)))
                .build();
        queueUpdates.computeIfAbsent(stateId, k -> new LinkedList<>()).add(stateChange);
    }

    @Override
    public void queuePopChange(final int stateId) {
        final var stateChange = StateChange.newBuilder()
                .stateId(stateId)
                .queuePop(new QueuePopChange())
                .build();
        queueUpdates.computeIfAbsent(stateId, k -> new LinkedList<>()).add(stateChange);
    }

    @Override
    public <V> void singletonUpdateChange(final int stateId, @NonNull final V value) {
        requireNonNull(value, "value must not be null");

        final var stateChange = StateChange.newBuilder()
                .stateId(stateId)
                .singletonUpdate(new SingletonUpdateChange(singletonUpdateChangeValueFor(value)))
                .build();
        singletonUpdates.put(stateId, stateChange);
    }

    private static <V> OneOf<QueuePushChange.ValueOneOfType> queuePushChangeValueFor(@NonNull final V value) {
        switch (value) {
            case ProtoBytes protoBytesElement -> {
                return new OneOf<>(QueuePushChange.ValueOneOfType.PROTO_BYTES_ELEMENT, protoBytesElement.value());
            }
            case TransactionReceiptEntries transactionReceiptEntriesElement -> {
                return new OneOf<>(
                        QueuePushChange.ValueOneOfType.TRANSACTION_RECEIPT_ENTRIES_ELEMENT,
                        transactionReceiptEntriesElement);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown value type " + value.getClass().getName());
        }
    }

    public static <V> @NonNull OneOf<SingletonUpdateChange.NewValueOneOfType> singletonUpdateChangeValueFor(
            @NonNull V value) {
        switch (value) {
            case BlockInfo blockInfo -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.BLOCK_INFO_VALUE, blockInfo);
            }
            case RosterState rosterState -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.ROSTER_STATE_VALUE, rosterState);
            }
            case CongestionLevelStarts congestionLevelStarts -> {
                return new OneOf<>(
                        SingletonUpdateChange.NewValueOneOfType.CONGESTION_LEVEL_STARTS_VALUE, congestionLevelStarts);
            }
            case EntityNumber entityNumber -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.ENTITY_NUMBER_VALUE, entityNumber.number());
            }
            case ExchangeRateSet exchangeRateSet -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.EXCHANGE_RATE_SET_VALUE, exchangeRateSet);
            }
            case NetworkStakingRewards networkStakingRewards -> {
                return new OneOf<>(
                        SingletonUpdateChange.NewValueOneOfType.NETWORK_STAKING_REWARDS_VALUE, networkStakingRewards);
            }
            case ProtoBytes protoBytes -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.BYTES_VALUE, protoBytes.value());
            }
            case ProtoString protoString -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.STRING_VALUE, protoString.value());
            }
            case RunningHashes runningHashes -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.RUNNING_HASHES_VALUE, runningHashes);
            }
            case ThrottleUsageSnapshots throttleUsageSnapshots -> {
                return new OneOf<>(
                        SingletonUpdateChange.NewValueOneOfType.THROTTLE_USAGE_SNAPSHOTS_VALUE, throttleUsageSnapshots);
            }
            case Timestamp timestamp -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.TIMESTAMP_VALUE, timestamp);
            }
            case BlockStreamInfo blockStreamInfo -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.BLOCK_STREAM_INFO_VALUE, blockStreamInfo);
            }
            case PlatformState platformState -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.PLATFORM_STATE_VALUE, platformState);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown value type " + value.getClass().getName());
        }
    }
}
