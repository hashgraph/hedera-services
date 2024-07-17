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

package com.swirlds.platform.state.merkle.disk;

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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Observer watches a Record and a Stack. It ties the changes in a stack to the transactions that created the state
 * changes. When a stack is committed we record all the changes that are a result of that commit.
 *
 * <p>What we'd like is a List of StateChanges that are tied to a SingleTransactionRecord, but right now this ties state
 *    changes to a {@code stream<SingleTransactionRecord>} which is not ideal but will give us a good estimate of data.
 *
 * <p>To do that, we need an order of operations such that when a stack is committed, for a given transaction, we can let
 *    the RecordListBuilder know what the changes are for that transaction. We don't want to include the changes for the
 *    child transactions as those should be included in the RecordListBuilder for the child transactions.
 *
 * <p>Any time we add a new protobuf type to be serialized to state, we need to add it to the relevant methods below
 *    that map the protobuf type to the OneOf type. It would be desirable if in the future PBJ generated these methods.
 *    That would ensure we would only need to add the type to the protobuf definition.
 */
public class StateChangesObserverImpl implements StateChangesObserver {
    private List<StateChange> stateChanges;
    private LinkedList<StateChange> endOfRoundStateChanges;
    private HashMap<String, StateChange> endOfRoundSingletonStateChanges;

    /**
     * Implementation of the StateChangesObserver for Block Streams. This should only ever be instantiated by
     * SingletonStateChangesObserver.
     */
    protected StateChangesObserverImpl() {
        resetStateChanges();
        resetEndOfRoundStateChanges();
    }

    public void resetStateChanges() {
        stateChanges = new ArrayList<>();
    }

    public void resetEndOfRoundStateChanges() {
        endOfRoundStateChanges = new LinkedList<>();
        endOfRoundSingletonStateChanges = new HashMap<String, StateChange>();
    }

    public boolean hasRecordedStateChanges() {
        return !stateChanges.isEmpty();
    }

    public boolean hasRecordedEndOfRoundStateChanges() {
        return !endOfRoundStateChanges.isEmpty();
    }

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
        // We may want to ignore certain state changes if their data is captured elswhere in the block stream.
        if (isIgnoredStateChange(stateChange)) {
            return;
        }

        // If the state change is an end of round state change we need to add it to the end of round state changes so
        // writing it will be delayed until the end of the round. The state change type for these will have to be
        // end-of-round or system for now as it doesn't make sense if on one hand BlockInfo is updated because of a
        // transaction and then overwritten by another BlockInfo update for the end of the block.
        if (isEndOfRoundStateChange(stateChange)) {
            // We need to add it to the end of round state changes.
            addEndOfRoundStateChange(stateChange);
            return;
        }

        stateChanges.add(stateChange);
    }

    public List<StateChange> getStateChanges() {
        return stateChanges;
    }

    public LinkedList<StateChange> getEndOfRoundStateChanges() {
        return endOfRoundStateChanges;
    }

    private boolean isIgnoredStateChange(@NonNull final StateChange stateChange) {
        return stateChange.stateName().equals("RecordCache.TransactionRecordQueue");
    }

    private boolean isEndOfRoundStateChange(@NonNull final StateChange stateChange) {
        return stateChange.hasSingletonUpdate();
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

    // Map (inMemory / onDisk) State -----------------------------------------------------------------------------------

    public <K, V> void mapUpdateChange(@NonNull final String stateKey, @NonNull final K key, @NonNull final V value) {
        final var builder = MapUpdateChange.newBuilder();
        setMapUpdateChangeKey(builder, key);
        setMapUpdateChangeValue(builder, value);
        final var change = builder.build();
        final var stateChange = StateChange.newBuilder()
                // .merkleTreePath() // TODO(nickpoorman): TBD if we need to include this
                .stateName(stateKey)
                .mapUpdate(change)
                .build();
        StateChangesObserverSingleton.getInstanceOrThrow().addStateChange(stateChange);
    }

    // TODO(nickpoorman): We should have PBJ generate these.
    private static <K> void setMapUpdateChangeKey(@NonNull final MapUpdateChange.Builder b, @NonNull final K key) {
        switch (key) {
            case AccountID accountID -> b.accountIdKey(accountID);
            case EntityIDPair entityIDPair -> b.entityIdPairKey(entityIDPair);
            case EntityNumber entityNumber -> b.entityNumberKey(entityNumber);
            case FileID fileID -> b.filedIdKey(fileID);
            case NftID nftID -> b.nftIdKey(nftID);
            case ProtoBytes protoBytes -> b.protoBytesKey(protoBytes);
            case ProtoLong protoLong -> b.protoLongKey(protoLong);
            case ProtoString protoString -> b.protoStringKey(protoString);
            case ScheduleID scheduleID -> b.scheduleIdKey(scheduleID);
            case SlotKey slotKey -> b.slotKeyKey(slotKey);
            case TokenID tokenID -> b.tokenIdKey(tokenID);
            case TopicID topicID -> b.topicIdKey(topicID);
            case String s -> b.protoStringKey(new ProtoString(s)); // used in tests
            case Long l -> b.protoLongKey(new ProtoLong(l)); // used in tests
            default -> throw new IllegalArgumentException(
                    "Unknown key type " + key.getClass().getName());
        }
    }

    private static <V> void setMapUpdateChangeValue(@NonNull final MapUpdateChange.Builder b, @NonNull final V value) {
        switch (value) {
            case Account account -> b.accountValue(account);
            case AccountID accountID -> b.accountIdValue(accountID);
            case Bytecode bytecode -> b.bytecodeValue(bytecode);
            case File file -> b.fileValue(file);
            case Nft nft -> b.nftValue(nft);
            case ProtoString protoString -> b.protoStringValue(protoString);
            case Schedule schedule -> b.scheduleValue(schedule);
            case ScheduleList scheduleList -> b.scheduleListValue(scheduleList);
            case SlotValue slotValue -> b.slotValueValue(slotValue);
            case StakingNodeInfo stakingNodeInfo -> b.stakingNodeInfoValue(stakingNodeInfo);
            case Token token -> b.tokenValue(token);
            case TokenRelation tokenRelation -> b.tokenRelationValue(tokenRelation);
            case Topic topic -> b.topicValue(topic);
            case String s -> b.protoStringKey(new ProtoString(s)); // used in tests
            default -> throw new IllegalArgumentException(
                    "Unknown value type " + value.getClass().getName());
        }
    }

    // Queue State -----------------------------------------------------------------------------------------------------

    public <V> void queuePushChange(@NonNull final String stateKey, @NonNull final V value) {
        final var builder = QueuePushChange.newBuilder();
        setQueuePushChangeElement(builder, value);
        final var change = builder.build();
        final var stateChange = StateChange.newBuilder()
                // .merkleTreePath() // TODO(nickpoorman): TBD if we need to include this
                .stateName(stateKey)
                .queuePush(change)
                .build();
        StateChangesObserverSingleton.getInstanceOrThrow().addStateChange(stateChange);
    }

    private static <V> void setQueuePushChangeElement(
            @NonNull final QueuePushChange.Builder b, @NonNull final V value) {
        switch (value) {
            case ProtoBytes protoBytesElement -> b.protoBytesElement(protoBytesElement);
            case ProtoString protoStringElement -> b.protoStringElement(protoStringElement);
            case TransactionRecordEntry transactionRecordEntryElement -> b.transactionRecordEntryElement(
                    transactionRecordEntryElement);
            case String s -> b.protoStringElement(new ProtoString(s)); // used in tests
            default -> throw new IllegalArgumentException(
                    "Unknown value type " + value.getClass().getName());
        }
    }

    public void queuePopChange(@NonNull final String stateKey) {
        final var stateChange = StateChange.newBuilder()
                // .merkleTreePath() // TODO(nickpoorman): TBD if we need to include this
                .stateName(stateKey)
                .queuePop(new QueuePopChange())
                .build();
        StateChangesObserverSingleton.getInstanceOrThrow().addStateChange(stateChange);
    }

    // Singleton State  ------------------------------------------------------------------------------------------------

    public <V> void singletonUpdateChange(@NonNull final String stateKey, @NonNull final V value) {
        final var builder = SingletonUpdateChange.newBuilder();
        setSingletonUpdateChangeValue(builder, value);
        final var change = builder.build();
        final var stateChange = StateChange.newBuilder()
                // .merkleTreePath() // TODO(nickpoorman): TBD if we need to include this
                .stateName(stateKey)
                .singletonUpdate(change)
                .build();
        StateChangesObserverSingleton.getInstanceOrThrow().addStateChange(stateChange);
    }

    private <V> void setSingletonUpdateChangeValue(
            @NonNull final SingletonUpdateChange.Builder b, @NonNull final V value) {
        switch (value) {
            case BlockInfo blockInfo -> b.blockInfoValue(blockInfo);
            case EntityNumber entityNumber -> b.entityNumberValue(entityNumber);
            case ExchangeRateSet exchangeRateSet -> b.exchangeRateSetValue(exchangeRateSet);
            case NetworkStakingRewards networkStakingRewards -> b.networkStakingRewardsValue(networkStakingRewards);
            case ProtoBytes protoBytes -> b.protoBytesValue(protoBytes);
            case ProtoString protoString -> b.protoStringValue(protoString);
            case RunningHashes runningHashes -> b.runningHashesValue(runningHashes);
            case Timestamp timestamp -> b.timestampValue(timestamp);
            case ThrottleUsageSnapshots throttleUsageSnapshots -> b.throttleUsageSnapshotsValue(throttleUsageSnapshots);
            case CongestionLevelStarts congestionLevelStarts -> b.congestionLevelStartsValue(congestionLevelStarts);
            case String s -> b.protoStringValue(new ProtoString(s)); // used in tests
            default -> throw new IllegalArgumentException(
                    "Unknown value type " + value.getClass().getName());
        }
    }

    /**
     * I don't think this is actually used today but if we ever delete a singleton, we'll want it.
     * @param stateKey the state key of the singleton to remove.
     */
    public void singletonDeleteChange(@NonNull final String stateKey) {
        final var stateChange = StateChange.newBuilder()
                // .merkleTreePath() // TODO(nickpoorman): TBD if we need to include this
                .stateName(stateKey)
                .singletonDelete(new SingletonDeleteChange())
                .build();
        StateChangesObserverSingleton.getInstanceOrThrow().addStateChange(stateChange);
    }
}
