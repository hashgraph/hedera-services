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

package com.hedera.node.app.service.mono.state.virtual;

import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValueSerializer;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValueSerializer;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValueSerializer;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKeySerializer;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import java.nio.file.Path;

public class VirtualMapFactory {

    private static final short CURRENT_SERIALIZATION_VERSION = 1;

    private static final long MAX_BLOBS = 50_000_000;
    private static final long MAX_STORAGE_ENTRIES = 500_000_000;
    private static final long MAX_SCHEDULES = 1_000_000_000L;
    private static final long MAX_ACCOUNTS = 100_000_000L;
    private static final long MAX_TOKEN_RELS = 100_000_000L;
    private static final long MAX_SCHEDULE_SECONDS = 500_000_000;
    private static final long MAX_MINTABLE_NFTS = 500_000_000L;
    private static final boolean PREFER_DISK_BASED_INDICIES = false;

    private static final String BLOBS_VM_NAME = "fileStore";
    private static final String ITERABLE_STORAGE_VM_NAME = "smartContractIterableKvStore";
    private static final String SCHEDULE_LIST_STORAGE_VM_NAME = "scheduleListStore";
    private static final String SCHEDULE_TEMPORAL_STORAGE_VM_NAME = "scheduleTemporalStore";
    private static final String SCHEDULE_EQUALITY_STORAGE_VM_NAME = "scheduleEqualityStore";
    private static final String ON_DISK_ACCOUNT_STORAGE_VM_NAME = "accountStore";
    private static final String ON_DISK_TOKEN_RELS_STORAGE_VM_NAME = "tokenRelStore";
    private static final String UNIQUE_TOKENS_VM_NAME = "uniqueTokenStore";

    private final Path storageDir;

    public VirtualMapFactory() {
        this(null);
    }

    public VirtualMapFactory(final Path storageDir) {
        this.storageDir = storageDir;
    }

    public VirtualMap<VirtualBlobKey, VirtualBlobValue> newVirtualizedBlobs() {
        final VirtualDataSourceBuilder<VirtualBlobKey, VirtualBlobValue> dsBuilder;
        final var keySerializer = new VirtualBlobKeySerializer();
        final var valueSerializer = new VirtualBlobValueSerializer();
        final MerkleDbTableConfig<VirtualBlobKey, VirtualBlobValue> tableConfig = new MerkleDbTableConfig<>(
                CURRENT_SERIALIZATION_VERSION,
                DigestType.SHA_384,
                CURRENT_SERIALIZATION_VERSION,
                keySerializer,
                CURRENT_SERIALIZATION_VERSION,
                valueSerializer);
        tableConfig.maxNumberOfKeys(MAX_BLOBS);
        tableConfig.preferDiskIndices(PREFER_DISK_BASED_INDICIES);
        dsBuilder = new MerkleDbDataSourceBuilder<>(storageDir, keySerializer, valueSerializer, tableConfig);
        return new VirtualMap<>(BLOBS_VM_NAME, dsBuilder);
    }

    public VirtualMap<ContractKey, IterableContractValue> newVirtualizedIterableStorage() {
        final VirtualDataSourceBuilder<ContractKey, IterableContractValue> dsBuilder;
        final var keySerializer = new ContractKeySerializer();
        final var valueSerialzer = new IterableContractValueSerializer();
        final MerkleDbTableConfig<ContractKey, IterableContractValue> tableConfig = new MerkleDbTableConfig<>(
                CURRENT_SERIALIZATION_VERSION,
                DigestType.SHA_384,
                CURRENT_SERIALIZATION_VERSION,
                keySerializer,
                CURRENT_SERIALIZATION_VERSION,
                valueSerialzer);
        tableConfig.maxNumberOfKeys(MAX_STORAGE_ENTRIES);
        tableConfig.preferDiskIndices(PREFER_DISK_BASED_INDICIES);
        dsBuilder = new MerkleDbDataSourceBuilder<>(storageDir, keySerializer, valueSerialzer, tableConfig);
        return new VirtualMap<>(ITERABLE_STORAGE_VM_NAME, dsBuilder);
    }

    public VirtualMap<EntityNumVirtualKey, ScheduleVirtualValue> newScheduleListStorage() {
        final var keySerializer = new EntityNumVirtualKeySerializer();
        final var valueSerializer = new ScheduleVirtualValueSerializer();
        final var tableConfig = new MerkleDbTableConfig<>(
                CURRENT_SERIALIZATION_VERSION,
                DigestType.SHA_384,
                CURRENT_SERIALIZATION_VERSION,
                keySerializer,
                CURRENT_SERIALIZATION_VERSION,
                valueSerializer);
        tableConfig.maxNumberOfKeys(MAX_SCHEDULES);
        tableConfig.preferDiskIndices(PREFER_DISK_BASED_INDICIES);
        final MerkleDbDataSourceBuilder<EntityNumVirtualKey, ScheduleVirtualValue> dsBuilder =
                new MerkleDbDataSourceBuilder<>(storageDir, keySerializer, valueSerializer, tableConfig);
        return new VirtualMap<>(SCHEDULE_LIST_STORAGE_VM_NAME, dsBuilder);
    }

    public VirtualMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> newScheduleTemporalStorage() {
        final var keySerializer = new SecondSinceEpocVirtualKeySerializer();
        final var valueSerializer = new ScheduleSecondVirtualValueSerializer();
        final var tableConfig = new MerkleDbTableConfig<>(
                        CURRENT_SERIALIZATION_VERSION,
                        DigestType.SHA_384,
                        CURRENT_SERIALIZATION_VERSION,
                        keySerializer,
                        CURRENT_SERIALIZATION_VERSION,
                        valueSerializer)
                .maxNumberOfKeys(MAX_SCHEDULE_SECONDS)
                .preferDiskIndices(PREFER_DISK_BASED_INDICIES);
        final var dsBuilder = new MerkleDbDataSourceBuilder<>(storageDir, keySerializer, valueSerializer, tableConfig);
        return new VirtualMap<>(SCHEDULE_TEMPORAL_STORAGE_VM_NAME, dsBuilder);
    }

    public VirtualMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> newScheduleEqualityStorage() {
        final var keySerializer = new ScheduleEqualityVirtualKeySerializer();
        final var valueSerializer = new ScheduleEqualityVirtualValueSerializer();
        final var tableConfig = new MerkleDbTableConfig<>(
                        CURRENT_SERIALIZATION_VERSION,
                        DigestType.SHA_384,
                        CURRENT_SERIALIZATION_VERSION,
                        keySerializer,
                        CURRENT_SERIALIZATION_VERSION,
                        valueSerializer)
                .maxNumberOfKeys(MAX_SCHEDULES)
                .preferDiskIndices(PREFER_DISK_BASED_INDICIES);
        final MerkleDbDataSourceBuilder<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> dsBuilder =
                new MerkleDbDataSourceBuilder<>(storageDir, keySerializer, valueSerializer, tableConfig);
        return new VirtualMap<>(SCHEDULE_EQUALITY_STORAGE_VM_NAME, dsBuilder);
    }

    public VirtualMap<EntityNumVirtualKey, OnDiskAccount> newOnDiskAccountStorage() {
        final var keySerializer = new EntityNumVirtualKeySerializer();
        final var valueSerializer = new OnDiskAccountValueSerializer();
        final VirtualDataSourceBuilder<EntityNumVirtualKey, OnDiskAccount> dsBuilder;
        final MerkleDbTableConfig<EntityNumVirtualKey, OnDiskAccount> tableConfig = new MerkleDbTableConfig<>(
                CURRENT_SERIALIZATION_VERSION,
                DigestType.SHA_384,
                CURRENT_SERIALIZATION_VERSION,
                keySerializer,
                CURRENT_SERIALIZATION_VERSION,
                valueSerializer);
        tableConfig.maxNumberOfKeys(MAX_ACCOUNTS);
        tableConfig.preferDiskIndices(PREFER_DISK_BASED_INDICIES);
        dsBuilder = new MerkleDbDataSourceBuilder<>(storageDir, keySerializer, valueSerializer, tableConfig);
        return new VirtualMap<>(ON_DISK_ACCOUNT_STORAGE_VM_NAME, dsBuilder);
    }

    public VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> newOnDiskTokenRels() {
        final var keySerializer = new EntityNumVirtualKeySerializer();
        final var valueSerializer = new OnDiskTokenRelValueSerializer();
        final VirtualDataSourceBuilder<EntityNumVirtualKey, OnDiskTokenRel> dsBuilder;
        final MerkleDbTableConfig<EntityNumVirtualKey, OnDiskTokenRel> tableConfig = new MerkleDbTableConfig<>(
                CURRENT_SERIALIZATION_VERSION,
                DigestType.SHA_384,
                CURRENT_SERIALIZATION_VERSION,
                keySerializer,
                CURRENT_SERIALIZATION_VERSION,
                valueSerializer);
        tableConfig.maxNumberOfKeys(MAX_TOKEN_RELS);
        tableConfig.preferDiskIndices(PREFER_DISK_BASED_INDICIES);
        dsBuilder = new MerkleDbDataSourceBuilder<>(storageDir, keySerializer, valueSerializer, tableConfig);
        return new VirtualMap<>(ON_DISK_TOKEN_RELS_STORAGE_VM_NAME, dsBuilder);
    }

    public VirtualMap<UniqueTokenKey, UniqueTokenValue> newVirtualizedUniqueTokenStorage() {
        final var keySerializer = new UniqueTokenKeySerializer();
        final var valueSerializer = new UniqueTokenValueSerializer();
        final VirtualDataSourceBuilder<UniqueTokenKey, UniqueTokenValue> dsBuilder;
        final MerkleDbTableConfig<UniqueTokenKey, UniqueTokenValue> tableConfig = new MerkleDbTableConfig<>(
                CURRENT_SERIALIZATION_VERSION,
                DigestType.SHA_384,
                CURRENT_SERIALIZATION_VERSION,
                keySerializer,
                CURRENT_SERIALIZATION_VERSION,
                valueSerializer);
        tableConfig.maxNumberOfKeys(MAX_MINTABLE_NFTS);
        tableConfig.preferDiskIndices(false);
        dsBuilder = new MerkleDbDataSourceBuilder<>(storageDir, keySerializer, valueSerializer, tableConfig);
        return new VirtualMap<>(UNIQUE_TOKENS_VM_NAME, dsBuilder);
    }
}
