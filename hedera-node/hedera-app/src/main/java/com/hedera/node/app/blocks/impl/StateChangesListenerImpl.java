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

import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.MapDeleteChange;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.QueuePopChange;
import com.hedera.hapi.block.stream.output.QueuePushChange;
import com.hedera.hapi.block.stream.output.SingletonUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.state.token.*;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.StateChangesListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class StateChangesListenerImpl implements StateChangesListener {
    private final List<StateChange> stateChanges = new ArrayList<>();
    ;
    private final LinkedList<StateChange> endOfRoundStateChanges = new LinkedList<>();
    ;
    private final HashMap<String, StateChange> endOfRoundSingletonStateChanges = new HashMap<>();

    public StateChangesListenerImpl() {}

    /**
     * We don't want to write certain state changes to the block stream until we are at the end of a block. For example,
     * BlockInfo is updated after each transaction to advance the consensus time. It would be too much data to serialize
     * and write the BlockInfo every time it is updated. Instead, we will write the BlockInfo at the end of the block if
     * it has changed. TO do this we filter out any state changes based on where we are in producing the block. If we
     * are at the very end before closing a round, then we can write the BlockInfo and any other singleton or queue
     * changes that should be written using the same behavior.
     * @param stateChange the state change to add to be written to the block stream.
     */
    public void addStateChange(@NonNull final StateChange stateChange) {
        Objects.requireNonNull(stateChange, "stateChange must not be null");
        // TODO: We may want to ignore certain state changes if their data is captured elsewhere in the block stream.
        //        if (isIgnoredStateChange(stateChange)) {
        //            return;
        //        }

        // TODO: should we buffer the state change to the end of round state changes if it's a singleton?
        // If the state change is an end of round state change we need to add it to the end of round state changes so
        // writing it will be delayed until the end of the round. The state change type for these will have to be
        // end-of-round or system for now as it doesn't make sense if on one hand BlockInfo is updated because of a
        // transaction and then overwritten by another BlockInfo update for the end of the block.
        //        if (stateChange.hasSingletonUpdate()) {
        //            addEndOfRoundStateChange(stateChange);
        //            return;
        //        }

        stateChanges.add(stateChange);
    }

    private boolean isIgnoredStateChange(@NonNull final StateChange stateChange) {
        return Objects.equals(stateChange.stateName(), "RecordCache.TransactionRecordQueue");
    }

    /**
     * We maintain a list of stateChanges that are to be written at the end of the round. This is so we can delay
     * writing large objects like BlockInfo. We also maintain a map of singleton state changes, so we can remove the old
     * state change if it's a singleton and already exists in our change list.
     * @param stateChange the state change to be written at the end of the round
     */
    private void addEndOfRoundStateChange(@NonNull final StateChange stateChange) {
        // If it's a singleton and already exists, remove the old one.
        if (stateChange.hasSingletonUpdate() && endOfRoundSingletonStateChanges.containsKey(stateChange.stateName())) {
            // Remove the old stateChange from both the list and the map.
            endOfRoundStateChanges.remove(endOfRoundSingletonStateChanges.get(stateChange.stateName()));
        }

        // Add the new stateChange to the end of the list.
        endOfRoundStateChanges.add(stateChange);

        // If it's a singleton, add it to the map as well.
        if (stateChange.hasSingletonUpdate()) {
            endOfRoundSingletonStateChanges.put(stateChange.stateName(), stateChange);
        }
    }

    public <K, V> void mapUpdateChange(@NonNull final String stateKey, @NonNull final K key, @NonNull final V value) {
        Objects.requireNonNull(stateKey, "stateKey must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        final var change = MapUpdateChange.newBuilder()
                .key(mapChangeKeyFor(key))
                .value(mapChangeValueFor(value))
                .build();
        final var stateChange =
                StateChange.newBuilder().stateName(stateKey).mapUpdate(change).build();
        addStateChange(stateChange);
    }

    public <K> void mapDeleteChange(@NonNull final String stateKey, @NonNull final K key) {
        Objects.requireNonNull(stateKey, "stateKey must not be null");
        Objects.requireNonNull(key, "key must not be null");

        final var change =
                MapDeleteChange.newBuilder().key(mapChangeKeyFor(key)).build();
        final var stateChange =
                StateChange.newBuilder().stateName(stateKey).mapDelete(change).build();
        addStateChange(stateChange);
    }

    public <V> void queuePushChange(@NonNull final String stateKey, @NonNull final V value) {
        Objects.requireNonNull(stateKey, "stateKey must not be null");
        Objects.requireNonNull(value, "value must not be null");

        final var stateChange = StateChange.newBuilder()
                .stateName(stateKey)
                .queuePush(new QueuePushChange(queuePushChangeValueFor(value)))
                .build();
        addStateChange(stateChange);
    }

    public void queuePopChange(@NonNull final String stateKey) {
        Objects.requireNonNull(stateKey, "stateKey must not be null");

        final var stateChange = StateChange.newBuilder()
                .stateName(stateKey)
                .queuePop(new QueuePopChange())
                .build();
        addStateChange(stateChange);
    }

    public <V> void singletonUpdateChange(@NonNull final String stateKey, @NonNull final V value) {
        Objects.requireNonNull(stateKey, "stateKey must not be null");
        Objects.requireNonNull(value, "value must not be null");

        final var stateChange = StateChange.newBuilder()
                .stateName(stateKey)
                .singletonUpdate(new SingletonUpdateChange(singletonUpdateChangeValueFor(value)))
                .build();
        addStateChange(stateChange);
    }

    public List<StateChange> getStateChanges() {
        return stateChanges;
    }

    public LinkedList<StateChange> getEndOfRoundStateChanges() {
        return endOfRoundStateChanges;
    }

    private static <K> MapChangeKey mapChangeKeyFor(@NonNull final K key) {
        return switch (key) {
            case AccountID accountID -> MapChangeKey.newBuilder()
                    .accountIdKey(accountID)
                    .build();
            case EntityIDPair entityIDPair -> MapChangeKey.newBuilder()
                    .entityIdPairKey(entityIDPair)
                    .build();
            case EntityNumber entityNumber -> MapChangeKey.newBuilder()
                    .entityNumberKey(entityNumber)
                    .build();
            case FileID fileID -> MapChangeKey.newBuilder().fileIdKey(fileID).build();
            case NftID nftID -> MapChangeKey.newBuilder().nftIdKey(nftID).build();
            case ProtoBytes protoBytes -> MapChangeKey.newBuilder()
                    .protoBytesKey(protoBytes.value())
                    .build();
            case ProtoLong protoLong -> MapChangeKey.newBuilder()
                    .protoLongKey(protoLong.value())
                    .build();
            case ProtoString protoString -> MapChangeKey.newBuilder()
                    .protoStringKey(protoString.value())
                    .build();
            case ScheduleID scheduleID -> MapChangeKey.newBuilder()
                    .scheduleIdKey(scheduleID)
                    .build();
            case SlotKey slotKey -> MapChangeKey.newBuilder()
                    .slotKeyKey(slotKey)
                    .build();
            case TokenID tokenID -> MapChangeKey.newBuilder()
                    .tokenIdKey(tokenID)
                    .build();
            case TopicID topicID -> MapChangeKey.newBuilder()
                    .topicIdKey(topicID)
                    .build();
            case ContractID contractID -> MapChangeKey.newBuilder()
                    .contractIdKey(contractID)
                    .build();
            default -> throw new IllegalStateException(
                    "Unrecognized key type " + key.getClass().getSimpleName());
        };
    }

    private static <V> MapChangeValue mapChangeValueFor(@NonNull final V value) {
        return switch (value) {
            case Account account -> MapChangeValue.newBuilder()
                    .accountValue(account)
                    .build();
            case AccountID accountID -> MapChangeValue.newBuilder()
                    .accountIdValue(accountID)
                    .build();
            case Bytecode bytecode -> MapChangeValue.newBuilder()
                    .bytecodeValue(bytecode)
                    .build();
            case File file -> MapChangeValue.newBuilder().fileValue(file).build();
            case Nft nft -> MapChangeValue.newBuilder().nftValue(nft).build();
            case ProtoString protoString -> MapChangeValue.newBuilder()
                    .protoStringValue(protoString.value())
                    .build();
            case Schedule schedule -> MapChangeValue.newBuilder()
                    .scheduleValue(schedule)
                    .build();
            case ScheduleList scheduleList -> MapChangeValue.newBuilder()
                    .scheduleListValue(scheduleList)
                    .build();
            case SlotValue slotValue -> MapChangeValue.newBuilder()
                    .slotValueValue(slotValue)
                    .build();
            case StakingNodeInfo stakingNodeInfo -> MapChangeValue.newBuilder()
                    .stakingNodeInfoValue(stakingNodeInfo)
                    .build();
            case Token token -> MapChangeValue.newBuilder().tokenValue(token).build();
            case TokenRelation tokenRelation -> MapChangeValue.newBuilder()
                    .tokenRelationValue(tokenRelation)
                    .build();
            case Topic topic -> MapChangeValue.newBuilder().topicValue(topic).build();
            default -> throw new IllegalStateException(
                    "Unexpected value: " + value.getClass().getSimpleName());
        };
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

    private static <V> @NotNull OneOf<SingletonUpdateChange.NewValueOneOfType> singletonUpdateChangeValueFor(
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
            default -> throw new IllegalArgumentException(
                    "Unknown value type " + value.getClass().getName());
        }
    }
}
