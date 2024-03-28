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

package com.hedera.services.cli.signedstate;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey.Type;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.base.time.Time;
import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.LegacyFileConfigSource;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.platform.system.StaticSoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Navigates a signed state "file" and returns information from it
 *
 * <p>A "signed state" is actually a directory tree, at the top level of which is the serialized
 * merkle tree of the hashgraph state. That is in a file named `SignedState.swh` and file is called
 * the "signed state file". But the whole directory tree must be present.
 *
 * <p>This uses a `SignedStateFileReader` to suck that entire merkle tree into memory, plus the
 * indexes of the virtual maps ("vmap"s) - ~1Gb serialized (2022-11). Then you can traverse the
 * rematerialized hashgraph state.
 *
 * <p>Currently implements only operations needed for looking at contracts: - {@link
 * #getAllKnownContracts()} looks in all the accounts to get all the contract ids present, - {@link
 * #getAllContractContents(Collection,Collection)} returns a map, indexed by contract id, of the contract
 * bytecodes.
 */
@SuppressWarnings("java:S5738") // deprecated classes (several current platform classes have no replacement yet)
public class SignedStateHolder implements AutoCloseableNonThrowing {

    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 100_000;
    static final int ESTIMATED_NUMBER_OF_DELETED_CONTRACTS = 10_000;

    @NonNull
    private final Path swhPath;

    @NonNull
    private final ReservedSignedState reservedSignedState;

    @NonNull
    private final ServicesState servicesState;

    public SignedStateHolder(@NonNull final Path swhPath, @NonNull final List<Path> configurationPaths) {
        Objects.requireNonNull(swhPath, "swhPath");
        Objects.requireNonNull(configurationPaths, "configurationPaths");

        this.swhPath = swhPath;
        final var state = dehydrate(configurationPaths);
        reservedSignedState = state.getLeft();
        servicesState = state.getRight();
    }

    @Override
    public void close() {
        reservedSignedState.close();
    }

    public enum Validity {
        ACTIVE,
        DELETED
    }

    /**
     * A contract - some bytecode associated with its contract id(s)
     *
     * @param ids - direct from the signed state file there's one contract id for each bytecode, but
     *     there are duplicates which can be coalesced and then there's a set of ids for the single
     *     contract; kept in sorted order by the container `TreeSet` so it's easy to get the canonical
     *     id for the contract, and also you can't forget to process them in a deterministic order
     * @param bytecode - bytecode of the contract
     * @param validity - whether the contract is valid or note, aka active or deleted
     */
    public record Contract(
            @NonNull TreeSet</*@NonNull*/ Integer> ids, @NonNull byte[] bytecode, @NonNull Validity validity) {

        // For any set of contract ids with the same bytecode, the lowest contract id is used as the "canonical"
        // id for that bytecode (useful for ordering contracts deterministically)
        public int canonicalId() {
            return ids.first();
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) return false;
            if (o == this) return true;
            return o instanceof Contract other
                    && new EqualsBuilder()
                            .append(ids, other.ids)
                            .append(bytecode, other.bytecode)
                            .append(validity, other.validity)
                            .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(ids)
                    .append(bytecode)
                    .append(validity)
                    .toHashCode();
        }

        @Override
        public String toString() {
            var csvIds = ids.stream().map(Object::toString).collect(Collectors.joining(","));
            return "Contract{ids=(%s), %s, bytecode=%s}".formatted(csvIds, validity, Arrays.toString(bytecode));
        }
    }

    /**
     * All contracts extracted from a signed state file
     *
     * @param contracts - dictionary of contract bytecodes indexed by their contract id (as a Long)
     * @param deletedContracts - collection of ids of deleted contracts
     * @param registeredContractsCount - total #contracts known to the _accounts_ in the signed
     *     state file (not all actually have bytecodes in the file store, and of those, some have
     *     0-length bytecode files)
     */
    public record Contracts(
            @NonNull Collection</*@NonNull*/ Contract> contracts,
            @NonNull Collection<Integer> deletedContracts,
            int registeredContractsCount) {}

    /**
     * Return all the bytecodes for all the contracts in this state.
     */
    @NonNull
    public Contracts getContracts() {
        final var contractIds = getAllKnownContracts();
        final var deletedContractIds = getAllDeletedContracts();
        final var contractContents = getAllContractContents(contractIds, deletedContractIds);
        return new Contracts(contractContents, deletedContractIds, contractIds.size());
    }

    /**
     * Returns all contracts known via Hedera accounts, by their contract id (lowered to an Integer)
     */
    @NonNull
    public Set</*@NonNull*/ Integer> getAllKnownContracts() {
        final var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_CONTRACTS);
        getAccounts().forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract()) ids.add(k.intValue());
        });
        return ids;
    }

    /** Returns the ids of all deleted contracts ("self-destructed") */
    @NonNull
    public Set</*@NonNull*/ Integer> getAllDeletedContracts() {
        final var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_DELETED_CONTRACTS);
        getAccounts().forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract() && v.isDeleted()) ids.add(k.intValue());
        });
        return ids;
    }

    /** Returns the bytecodes for all the requested contracts */
    @NonNull
    public Collection</*@NonNull*/ Contract> getAllContractContents(
            @NonNull final Collection</*@NonNull*/ Integer> contractIds,
            @NonNull final Collection</*@NonNull*/ Integer> deletedContractIds) {
        Objects.requireNonNull(contractIds);
        Objects.requireNonNull(deletedContractIds);

        final var fileStore = getFileStore();
        final var codes = new ArrayList<Contract>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (final var cid : contractIds) {
            final var vbk = new VirtualBlobKey(Type.CONTRACT_BYTECODE, cid);
            if (fileStore.containsKey(vbk)) {
                final var blob = fileStore.get(vbk);
                if (null != blob) {
                    final var c = new Contract(
                            new TreeSet<>(),
                            blob.getData(),
                            deletedContractIds.contains(cid) ? Validity.DELETED : Validity.ACTIVE);
                    c.ids.add(cid);
                    codes.add(c);
                }
            }
        }
        return codes;
    }

    /** Gets all existing accounts */
    @NonNull
    public AccountStorageAdapter getAccounts() {
        final var accounts = servicesState.accounts();
        assertSignedStateComponentExists(accounts, "accounts");
        return accounts;
    }

    /** Get all fungible token types */
    @NonNull
    public MerkleMapLike<EntityNum, MerkleToken> getFungibleTokenTypes() {
        final var fungibleTokenTypes = servicesState.tokens();
        assertSignedStateComponentExists(fungibleTokenTypes, "(fungible) tokens");
        return fungibleTokenTypes;
    }

    /** Get all unique (serial number) issued NFT tokens */
    @NonNull
    public UniqueTokenMapAdapter getUniqueNFTTokens() {
        final var nftTypes = servicesState.uniqueTokens();
        assertSignedStateComponentExists(nftTypes, "(non-fungible) unique tokens");
        return nftTypes;
    }

    /** Get all topics */
    @NonNull
    public MerkleMapLike<EntityNum, MerkleTopic> getTopics() {
        final var topics = servicesState.topics();
        assertSignedStateComponentExists(topics, "topics");
        return topics;
    }

    /** Get all token associations (tokenrels) */
    @NonNull
    public TokenRelStorageAdapter getTokenAssociations() {
        final var tokenRels = servicesState.tokenAssociations();
        assertSignedStateComponentExists(tokenRels, "token associations (tokenrels)");
        return tokenRels;
    }

    /**
     * Returns the file store from the state
     *
     * <p>The file state contains, among other things, all the contracts' bytecodes.
     */
    @NonNull
    public VirtualMapLike<VirtualBlobKey, VirtualBlobValue> getFileStore() {
        final var fileStore = servicesState.storage();
        assertSignedStateComponentExists(fileStore, "fileStore");
        return fileStore;
    }

    /** Returns the special files store from the state
     *
     * The special files store contains, among other things, the system upgrade files.
     */
    @NonNull
    public MerkleSpecialFiles getSpecialFileStore() {
        final var specialFiles = servicesState.specialFiles();
        assertSignedStateComponentExists(specialFiles, "specialFiles");
        return specialFiles;
    }

    @NonNull
    public VirtualMapLike<ContractKey, IterableContractValue> getRawContractStorage() {
        final var rawContractStorage = servicesState.contractStorage();
        assertSignedStateComponentExists(rawContractStorage, "contractStorage");
        return rawContractStorage;
    }

    /** Get all scheduled transactions */
    @NonNull
    public MerkleScheduledTransactions getScheduledTransactions() {
        final var scheduledTransactions = servicesState.scheduleTxs();
        assertSignedStateComponentExists(scheduledTransactions, "scheduledTransactions");
        return scheduledTransactions;
    }

    // Returns the network context store from the state
    @NonNull
    public MerkleNetworkContext getNetworkContext() {
        final var networkContext = servicesState.networkCtx();
        assertSignedStateComponentExists(networkContext, "networkContext");
        return networkContext;
    }

    // Returns the staking info store from the state
    @NonNull
    public MerkleMapLike<EntityNum, MerkleStakingInfo> getStakingInfo() {
        final var stakingInfo = servicesState.stakingInfo();
        assertSignedStateComponentExists(stakingInfo, "stakingInfo");
        return stakingInfo;
    }

    @NonNull
    public RecordsRunningHashLeaf getRunningHashLeaf() {
        final var runningHashLeaf = servicesState.runningHashLeaf();
        assertSignedStateComponentExists(runningHashLeaf, "runningHashLeaf");
        return runningHashLeaf;
    }

    @NonNull
    public RecordsStorageAdapter getPayerRecords() {
        final var payerRecords = servicesState.payerRecords();
        assertSignedStateComponentExists(payerRecords, "payerRecords");
        return payerRecords;
    }

    /** Deserialize the signed state file into an in-memory data structure. */
    @NonNull
    private Pair<ReservedSignedState, ServicesState> dehydrate(@NonNull final List<Path> configurationPaths) {
        Objects.requireNonNull(configurationPaths, "configurationPaths");

        registerConstructables();

        final var platformContext = new DefaultPlatformContext(
                buildConfiguration(configurationPaths), new NoOpMetrics(), CryptographyHolder.get(), Time.getCurrent());

        ReservedSignedState rss;
        try {
            rss = SignedStateFileReader.readStateFile(platformContext, swhPath).reservedSignedState();
            StaticSoftwareVersion.setSoftwareVersion(
                    rss.get().getState().getPlatformState().getCreationSoftwareVersion());
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (null == rss) throw new MissingSignedStateComponent("ReservedSignedState", swhPath);

        final var swirldsState = rss.get().getSwirldState();
        if (!(swirldsState instanceof ServicesState)) { // Java booboo: precedence level of `instanceof` is way too low
            rss.close();
            throw new MissingSignedStateComponent("ServicesState", swhPath);
        }

        return Pair.of(rss, (ServicesState) swirldsState);
    }

    /** Build a configuration object from the provided configuration paths. */
    private Configuration buildConfiguration(@NonNull final List<Path> configurationPaths) {
        Objects.requireNonNull(configurationPaths, "configurationPaths");

        final var builder = ConfigurationBuilder.create().autoDiscoverExtensions();

        for (@NonNull final var path : configurationPaths) {
            Objects.requireNonNull(path, "path");
            try {
                builder.withSource(new LegacyFileConfigSource(path));
            } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        final var configuration = builder.build();
        ConfigurationHolder.getInstance().setConfiguration(configuration);
        return configuration;
    }

    /** register all applicable classes on classpath before deserializing signed state */
    private void registerConstructables() {
        try {
            final var registry = ConstructableRegistry.getInstance();
            registry.registerConstructables("com.hedera.node.app.service.mono");
            registry.registerConstructables("com.hedera.node.app.service.mono.*");
            registry.registerConstructables("com.swirlds.*");
        } catch (final ConstructableRegistryException ex) {
            throw new UncheckedConstructableRegistryException(ex);
        }
    }

    public static class UncheckedConstructableRegistryException extends RuntimeException {
        public UncheckedConstructableRegistryException(@NonNull final ConstructableRegistryException ex) {
            super(ex);
        }
    }

    public static class MissingSignedStateComponent extends NullPointerException {
        public MissingSignedStateComponent(@NonNull final String component, @NonNull final Path swhPath) {
            super("Expected non-null %s from signed state file %s".formatted(component, swhPath.toString()));
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(swhPath, "swhPath");
        }
    }

    private void assertSignedStateComponentExists(
            @Nullable final Object component, @NonNull final String componentName) {
        Objects.requireNonNull(componentName, "componentName");

        if (null == component) throw new MissingSignedStateComponent(componentName, swhPath);
    }
}
