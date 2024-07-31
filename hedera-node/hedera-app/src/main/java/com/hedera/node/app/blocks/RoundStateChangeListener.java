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

package com.hedera.node.app.blocks;

import static com.hedera.hapi.block.stream.output.StateChangesCause.STATE_CHANGE_CAUSE_END_OF_BLOCK;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
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
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.state.StateChangesListener;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.jetbrains.annotations.NotNull;

public class RoundStateChangeListener implements StateChangesListener {
    private static final Set<DataType> TARGET_DATA_TYPES = EnumSet.of(DataType.SINGLETON, DataType.QUEUE);

    private SortedMap<String, StateChange> singletonUpdates = new TreeMap<>();
    private SortedMap<String, List<StateChange>> queueUpdates = new TreeMap<>();
    private Instant lastUsedConsensusTime;

    public RoundStateChangeListener(@NonNull final Instant lastUsedConsensusTime) {
        this.lastUsedConsensusTime = requireNonNull(lastUsedConsensusTime);
    }

    @Override
    public Set<DataType> targetDataTypes() {
        return TARGET_DATA_TYPES;
    }

    @Override
    public <V> void queuePushChange(@NonNull final String stateName, @NonNull final V value) {
        requireNonNull(stateName);
        requireNonNull(value);
        final var stateChange = StateChange.newBuilder()
                .stateName(stateName)
                .queuePush(new QueuePushChange(queuePushChangeValueFor(value)))
                .build();
        queueUpdates.computeIfAbsent(stateName, k -> new LinkedList<>()).add(stateChange);
    }

    @Override
    public void queuePopChange(@NonNull final String stateName) {
        requireNonNull(stateName, "stateName must not be null");

        final var stateChange = StateChange.newBuilder()
                .stateName(stateName)
                .queuePop(new QueuePopChange())
                .build();
        queueUpdates.computeIfAbsent(stateName, k -> new LinkedList<>()).add(stateChange);
    }

    @Override
    public <V> void singletonUpdateChange(@NonNull final String stateName, @NonNull final V value) {
        requireNonNull(stateName, "stateName must not be null");
        requireNonNull(value, "value must not be null");

        final var stateChange = StateChange.newBuilder()
                .stateName(stateName)
                .singletonUpdate(new SingletonUpdateChange(singletonUpdateChangeValueFor(value)))
                .build();
        singletonUpdates.put(stateName, stateChange);
    }

    public BlockItem stateChanges() {
        final var allStateChanges = new LinkedList<StateChange>();
        for (final var entry : singletonUpdates.entrySet()) {
            allStateChanges.add(entry.getValue());
        }
        for (final var entry : queueUpdates.entrySet()) {
            allStateChanges.addAll(entry.getValue());
        }
        final var stateChanges = StateChanges.newBuilder()
                .stateChanges(allStateChanges)
                .consensusTimestamp(endOfBlockTimestamp())
                .cause(STATE_CHANGE_CAUSE_END_OF_BLOCK);
        return BlockItem.newBuilder().stateChanges(stateChanges).build();
    }

    public @NonNull Timestamp endOfBlockTimestamp() {
        return asTimestamp(lastUsedConsensusTime.plusNanos(1));
    }

    public void setLastUsedConsensusTime(@NonNull final Instant nextAvailableConsensusTime) {
        this.lastUsedConsensusTime = requireNonNull(nextAvailableConsensusTime);
    }

    private static <V> OneOf<QueuePushChange.ValueOneOfType> queuePushChangeValueFor(@NotNull V value) {
        switch (value) {
            case Bytes protoBytesElement -> {
                return new OneOf<>(QueuePushChange.ValueOneOfType.PROTO_BYTES_ELEMENT, protoBytesElement);
            }
            case String protoStringElement -> {
                return new OneOf<>(QueuePushChange.ValueOneOfType.PROTO_STRING_ELEMENT, protoStringElement);
            }
            case TransactionRecordEntry transactionRecordEntryElement -> {
                return new OneOf<>(
                        QueuePushChange.ValueOneOfType.TRANSACTION_RECORD_ENTRY_ELEMENT, transactionRecordEntryElement);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown value type " + value.getClass().getName());
        }
    }

    public static <V> @NotNull OneOf<SingletonUpdateChange.NewValueOneOfType> singletonUpdateChangeValueFor(
            @NotNull V value) {
        switch (value) {
            case BlockInfo blockInfo -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.BLOCK_INFO_VALUE, blockInfo);
            }
            case CongestionLevelStarts congestionLevelStarts -> {
                return new OneOf<>(
                        SingletonUpdateChange.NewValueOneOfType.CONGESTION_LEVEL_STARTS_VALUE, congestionLevelStarts);
            }
            case EntityNumber entityNumber -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.ENTITY_NUMBER_VALUE, entityNumber);
            }
            case ExchangeRateSet exchangeRateSet -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.EXCHANGE_RATE_SET_VALUE, exchangeRateSet);
            }
            case NetworkStakingRewards networkStakingRewards -> {
                return new OneOf<>(
                        SingletonUpdateChange.NewValueOneOfType.NETWORK_STAKING_REWARDS_VALUE, networkStakingRewards);
            }
            case Bytes protoBytes -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.BYTES_VALUE, protoBytes);
            }
            case String protoString -> {
                return new OneOf<>(SingletonUpdateChange.NewValueOneOfType.STRING_VALUE, protoString);
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
            default -> throw new IllegalArgumentException(
                    "Unknown value type " + value.getClass().getName());
        }
    }
}
