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

package com.hedera.node.app.info;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.hedera.hapi.node.state.Network;
import com.hedera.hapi.node.state.NodeMetadata;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.handlers.TssUtils;
import com.hedera.node.app.tss.stores.ReadableTssStore;
import com.hedera.node.app.tss.stores.ReadableTssStoreImpl;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.platform.state.service.ReadableRosterStoreImpl;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.StartupNetworks;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongUnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link StartupNetworks} implementation that loads {@link Network} information from a
 * working directory on disk.
 */
public class DiskStartupNetworks implements StartupNetworks {
    private static final Logger log = LogManager.getLogger(DiskStartupNetworks.class);

    private static final Pattern ROUND_DIR_PATTERN = Pattern.compile("\\d+");

    public static final String ARCHIVE = ".archive";
    public static final String GENESIS_NETWORK_JSON = "genesis-network.json";
    public static final String OVERRIDE_NETWORK_JSON = "override-network.json";

    private final long selfNodeId;
    private final ConfigProvider configProvider;
    private final TssBaseService tssBaseService;

    public DiskStartupNetworks(
            final long selfNodeId,
            @NonNull final ConfigProvider configProvider,
            @NonNull final TssBaseService tssBaseService) {
        this.selfNodeId = selfNodeId;
        this.configProvider = requireNonNull(configProvider);
        this.tssBaseService = tssBaseService;
    }

    @Override
    public Network genesisNetworkOrThrow() {
        return loadNetwork(configProvider.getConfiguration(), GENESIS_NETWORK_JSON)
                .orElseThrow(() -> new IllegalStateException("Genesis network not found"));
    }

    @Override
    public Optional<Network> overrideNetworkFor(final long roundNumber) {
        final var config = configProvider.getConfiguration();
        final var unscopedNetwork = loadNetwork(config, OVERRIDE_NETWORK_JSON);
        if (unscopedNetwork.isPresent()) {
            return unscopedNetwork;
        }
        return loadNetwork(config, "" + roundNumber, OVERRIDE_NETWORK_JSON);
    }

    @Override
    public void setOverrideRound(final long roundNumber) {
        final var config = configProvider.getConfiguration();
        final var path = networksPath(config, OVERRIDE_NETWORK_JSON);
        if (Files.exists(path)) {
            final var roundDir = networksPath(config, "" + roundNumber);
            final var scopedPath = roundDir.resolve(OVERRIDE_NETWORK_JSON);
            try {
                Files.createDirectories(roundDir);
                Files.move(path, scopedPath);
                log.info("Moved override network file to {}", scopedPath);
            } catch (IOException e) {
                log.warn("Failed to move override network file", e);
            }
        }
    }

    @Override
    public void archiveJsonFiles() {
        final var config = configProvider.getConfiguration();
        try {
            ensureArchiveDir(config);
        } catch (IOException e) {
            log.warn("Failed to create archive directory", e);
            return;
        }
        archiveIfPresent(config, GENESIS_NETWORK_JSON);
        archiveIfPresent(config, OVERRIDE_NETWORK_JSON);
        try (final var dirStream = Files.list(networksPath(config))) {
            dirStream
                    .filter(Files::isDirectory)
                    .filter(dir -> ROUND_DIR_PATTERN
                            .matcher(dir.getFileName().toString())
                            .matches())
                    .forEach(dir -> archiveIfPresent(config, dir.getFileName().toString(), OVERRIDE_NETWORK_JSON));
        } catch (IOException e) {
            log.warn("Failed to list round override network files", e);
        }
    }

    @Override
    public Network migrationNetworkOrThrow() {
        // FUTURE - look into sourcing this from a config.txt and public.pfx to ease migration
        return loadNetwork(configProvider.getConfiguration(), OVERRIDE_NETWORK_JSON)
                .orElseThrow(() -> new IllegalStateException("Transplant network not found"));
    }

    /**
     * Writes a JSON representation of the {@link Network} information in the given state to a given path.
     * @param state the state to write network information from.
     * @param path the path to write the JSON network information to.
     */
    public static void writeNetworkInfo(@NonNull final State state, @NonNull final Path path) {
        requireNonNull(state);
        writeNetworkInfo(
                new ReadableTssStoreImpl(state.getReadableStates(TssBaseService.NAME)),
                new ReadableNodeStoreImpl(state.getReadableStates(AddressBookService.NAME)),
                new ReadableRosterStoreImpl(state.getReadableStates(RosterService.NAME)),
                path);
    }

    /**
     * Writes a JSON representation of the {@link Network} information in the given state to a given path.
     * @param path the path to write the JSON network information to.
     */
    public static void writeNetworkInfo(
            @NonNull final ReadableTssStore tssStore,
            @NonNull final ReadableNodeStore nodeStore,
            @NonNull final ReadableRosterStore rosterStore,
            @NonNull final Path path) {
        requireNonNull(tssStore);
        requireNonNull(nodeStore);
        requireNonNull(rosterStore);
        requireNonNull(path);
        Optional.ofNullable(rosterStore.getActiveRoster()).ifPresent(activeRoster -> {
            final var network = Network.newBuilder();
            final List<NodeMetadata> nodeMetadata = new ArrayList<>();
            rosterStore.getActiveRoster().rosterEntries().forEach(entry -> {
                final var node = requireNonNull(nodeStore.get(entry.nodeId()));
                nodeMetadata.add(new NodeMetadata(entry, node, Bytes.EMPTY));
            });
            network.nodeMetadata(nodeMetadata);
            final var sourceRosterHash =
                    Optional.ofNullable(rosterStore.getPreviousRosterHash()).orElse(Bytes.EMPTY);
            final long sourceRosterWeight;
            final LongUnaryOperator nodeWeightFn;
            if (Bytes.EMPTY.equals(sourceRosterHash)) {
                // For the genesis roster, we give all "source" nodes equal weight of 1
                sourceRosterWeight = activeRoster.rosterEntries().size();
                nodeWeightFn = nodeId -> 1;
            } else {
                final var entries =
                        requireNonNull(rosterStore.get(sourceRosterHash)).rosterEntries();
                sourceRosterWeight =
                        entries.stream().mapToLong(RosterEntry::weight).sum();
                final var weights = entries.stream().collect(toMap(RosterEntry::nodeId, RosterEntry::weight));
                nodeWeightFn = weights::get;
            }
            tssStore.consensusRosterKeys(
                            sourceRosterHash,
                            requireNonNull(rosterStore.getCurrentRosterHash()),
                            sourceRosterWeight,
                            nodeWeightFn)
                    .ifPresent(rosterKeys ->
                            network.ledgerId(rosterKeys.ledgerId()).tssMessages(rosterKeys.tssMessages()));
            try (final var fout = Files.newOutputStream(path)) {
                Network.JSON.write(network.build(), new WritableStreamingData(fout));
            } catch (IOException e) {
                log.warn("Failed to write network info", e);
            }
        });
    }

    /**
     * Attempts to load a {@link Network} from a given file in the directory whose relative path is given
     * by the provided {@link Configuration}.
     * @param config the configuration to use to determine the location of the network file
     * @param segments the path segments of the file to load the network from
     * @return the loaded network, if it was found and successfully loaded
     */
    private Optional<Network> loadNetwork(@NonNull final Configuration config, @NonNull final String... segments) {
        final var path = networksPath(config, segments);
        if (Files.exists(path)) {
            try (final var fin = Files.newInputStream(path)) {
                final var network = Network.JSON.parse(new ReadableStreamingData(fin));
                assertValidTssKeys(network);
                return Optional.of(network);
            } catch (Exception e) {
                log.warn("Failed to load network info from {}", path.toAbsolutePath(), e);
            }
        }
        return Optional.empty();
    }

    /**
     * If the given network has a ledger id, then it asserts that the TSS keys in the network are valid.
     * @param network the network to assert the TSS keys of
     * @throws IllegalArgumentException if the TSS keys are invalid
     */
    private void assertValidTssKeys(@NonNull final Network network) {
        final var expectedLedgerId = network.ledgerId();
        if (!Bytes.EMPTY.equals(expectedLedgerId)) {
            final var roster = new Roster(network.nodeMetadata().stream()
                    .map(metadata -> new RosterEntry(
                            metadata.nodeOrThrow().nodeId(),
                            metadata.nodeOrThrow().weight(),
                            metadata.nodeOrThrow().gossipCaCertificate(),
                            metadata.tssEncryptionKey(),
                            metadata.nodeOrThrow().gossipEndpoint()))
                    .toList());
            final var maxSharesPerNode = configProvider
                    .getConfiguration()
                    .getConfigData(TssConfig.class)
                    .maxSharesPerNode();
            final var directory = TssUtils.computeParticipantDirectory(roster, maxSharesPerNode, (int) selfNodeId);
            final var tssMessages = network.tssMessages().stream()
                    .map(TssMessageTransactionBody::tssMessage)
                    .map(Bytes::toByteArray)
                    .map(TssMessage::new)
                    .toList();
            final var actualLedgerId = tssBaseService.ledgerIdFrom(directory, tssMessages);
            if (!expectedLedgerId.equals(actualLedgerId)) {
                throw new IllegalArgumentException("Ledger id '" + actualLedgerId.toHex()
                        + "' does not match expected '" + expectedLedgerId.toHex() + "'");
            }
        }
    }

    /**
     * Attempts to archive the given segments in the given configuration.
     * @param segments the segments to archive
     */
    private static void archiveIfPresent(@NonNull final Configuration config, @NonNull final String... segments) {
        try {
            final var path = networksPath(config, segments);
            if (Files.exists(path)) {
                final var archiveSegments =
                        Stream.concat(Stream.of(ARCHIVE), Stream.of(segments)).toArray(String[]::new);
                final var dest = networksPath(config, archiveSegments);
                createIfAbsent(dest.getParent());
                Files.move(path, dest);
            }
        } catch (IOException e) {
            log.warn("Failed to archive {}", segments, e);
        }
    }

    /**
     * Ensures that the archive directory exists in the given configuration.
     * @param config the configuration to ensure the archive directory exists in
     */
    private static void ensureArchiveDir(@NonNull final Configuration config) throws IOException {
        createIfAbsent(networksPath(config, ARCHIVE));
    }

    /**
     * Creates the given path as a directory if it does not already exist.
     * @param path the path to the directory create if it does not already exist
     */
    private static void createIfAbsent(@NonNull final Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectory(path);
        }
    }

    /**
     * Gets the path to the directory containing network files.
     * @param config the configuration to use to determine the location of the network files
     * @return the path to the directory containing network files
     */
    private static Path networksPath(@NonNull final Configuration config, @NonNull final String... segments) {
        return Paths.get(config.getConfigData(NetworkAdminConfig.class).upgradeSysFilesLoc(), segments);
    }
}
