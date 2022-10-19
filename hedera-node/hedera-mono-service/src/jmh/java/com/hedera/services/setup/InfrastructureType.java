/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.setup;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.RecordsStorageAdapter;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;

/**
 * An instance of this type facilitates the "bundling" of a particular Services infrastructure
 * component into a {@link InfrastructureBundle} that can be created, configured with well-known
 * entities, and saved to disk.
 */
public enum InfrastructureType {
    ACCOUNTS_LEDGER {
        @Override
        @SuppressWarnings("unchecked")
        public TransactionalLedger<AccountID, AccountProperty, HederaAccount> fromStorage(
                final String dir, final InfrastructureBundle bundle) {
            return abInitio(dir, bundle);
        }

        @Override
        @SuppressWarnings("unchecked")
        public TransactionalLedger<AccountID, AccountProperty, HederaAccount> abInitio(
                final String dir, final InfrastructureBundle bundle) {
            final var backingAccounts =
                    new BackingAccounts(
                            () ->
                                    AccountStorageAdapter.fromInMemory(
                                            (MerkleMap<EntityNum, MerkleAccount>)
                                                    bundle.getterFor(ACCOUNTS_MM).get()),
                            () ->
                                    RecordsStorageAdapter.fromLegacy(
                                            (MerkleMap<EntityNum, MerkleAccount>)
                                                    bundle.getterFor(ACCOUNTS_MM).get()));
            backingAccounts.rebuildFromSources();
            return new TransactionalLedger<>(
                    AccountProperty.class,
                    MerkleAccount::new,
                    backingAccounts,
                    new ChangeSummaryManager<>());
        }

        @Override
        public EnumSet<InfrastructureType> dependencies() {
            return EnumSet.of(InfrastructureType.ACCOUNTS_MM);
        }
    },
    STAKING_INFOS_MM {
        private static final String MM_FILE_NAME = "staking_infos.mmap";

        @Override
        public String locWithin(final String dir) {
            return dir + File.separator + MM_FILE_NAME;
        }

        @Override
        @SuppressWarnings("unchecked")
        public MerkleMap<EntityNum, MerkleStakingInfo> abInitio(
                final String dir, final InfrastructureBundle bundle) {
            return new MerkleMap<>();
        }

        @Override
        @SuppressWarnings("unchecked")
        public MerkleMap<EntityNum, MerkleStakingInfo> fromStorage(
                final String dir, final InfrastructureBundle bundle) {
            return mmFromStorage(dir);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void toStorage(
                final Object fromBundle, final String dir, final InfrastructureBundle bundle) {
            mmToStorage((MerkleMap<EntityNum, MerkleStakingInfo>) fromBundle, dir);
        }
    },
    ACCOUNTS_MM {
        private static final String MM_FILE_NAME = "accounts.mmap";

        @Override
        public String locWithin(final String dir) {
            return dir + File.separator + MM_FILE_NAME;
        }

        @Override
        @SuppressWarnings("unchecked")
        public MerkleMap<EntityNum, MerkleAccount> abInitio(
                final String dir, final InfrastructureBundle bundle) {
            return new MerkleMap<>();
        }

        @Override
        @SuppressWarnings("unchecked")
        public MerkleMap<EntityNum, MerkleAccount> fromStorage(
                final String dir, final InfrastructureBundle bundle) {
            return mmFromStorage(dir);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void toStorage(
                final Object fromBundle, final String dir, final InfrastructureBundle bundle) {
            mmToStorage((MerkleMap<EntityNum, MerkleAccount>) fromBundle, dir);
        }
    },
    CONTRACT_STORAGE_VM {
        private static final String VM_META_FILE_NAME = "smartContractKvStore.meta";

        @Override
        public String locWithin(final String dir) {
            return dir + File.separator + VM_META_FILE_NAME;
        }

        @Override
        @SuppressWarnings("unchecked")
        public VirtualMap<ContractKey, IterableContractValue> abInitio(
                final String dir, final InfrastructureBundle bundle) {
            final var vmFactory = InfrastructureManager.newVmFactory(dir);
            return vmFactory.newVirtualizedIterableStorage();
        }

        @Override
        @SuppressWarnings("unchecked")
        public VirtualMap<ContractKey, IterableContractValue> fromStorage(
                final String dir, final InfrastructureBundle bundle) {
            final var vMaploc = dir + File.separator + VM_META_FILE_NAME;
            final VirtualMap<ContractKey, IterableContractValue> kvStore = new VirtualMap<>();
            try (final var fin =
                    new MerkleDataInputStream(Files.newInputStream(Paths.get(vMaploc)))) {
                kvStore.deserialize(fin, Paths.get(dir), 1);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return kvStore;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void toStorage(
                final Object fromBundle, final String dir, final InfrastructureBundle bundle) {
            final var contractStorage = (VirtualMap<ContractKey, IterableContractValue>) fromBundle;
            final var metaLoc = locWithin(dir);
            final var newContractStorage = contractStorage.copy();
            MerkleCryptoFactory.getInstance().digestTreeSync(contractStorage);
            try (final var fout =
                    new SerializableDataOutputStream(Files.newOutputStream(Paths.get(metaLoc)))) {
                contractStorage.serialize(fout, Paths.get(dir));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            bundle.set(CONTRACT_STORAGE_VM, newContractStorage);
        }
    };

    public Set<InfrastructureType> dependencies() {
        return EnumSet.noneOf(InfrastructureType.class);
    }

    public void toStorage(
            final Object fromBundle, final String dir, final InfrastructureBundle bundle) {
        // No-op
    }

    public String locWithin(String dir) {
        return dir + File.separator;
    }

    public abstract <T> T abInitio(String dir, InfrastructureBundle bundle);

    public abstract <T> T fromStorage(String dir, InfrastructureBundle bundle);

    protected <K, V extends MerkleNode & Keyed<K>> MerkleMap<K, V> mmFromStorage(final String dir) {
        final var mMapLoc = locWithin(dir);
        try (final var fin = new MerkleDataInputStream(Files.newInputStream(Paths.get(mMapLoc)))) {
            fin.readProtocolVersion();
            return fin.readMerkleTree(Paths.get(dir), Integer.MAX_VALUE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected <K, V extends MerkleNode & Keyed<K>> void mmToStorage(
            final MerkleMap<K, V> fromBundle, final String dir) {
        final var mMapLoc = locWithin(dir);
        try (final var mMapOut =
                new MerkleDataOutputStream(Files.newOutputStream(Paths.get(mMapLoc)))) {
            mMapOut.writeProtocolVersion();
            mMapOut.writeMerkleTree(Paths.get(dir), fromBundle);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
