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

package com.hedera.node.app.info;

import static com.hedera.hapi.util.HapiUtils.parseAccount;
import static com.swirlds.platform.builder.PlatformBuildConstants.DEFAULT_CONFIG_FILE_NAME;
import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.node.app.hapi.utils.keys.Ed25519Utils;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.StartupNetworks;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    public static final String ARCHIVE = ".archive";
    public static final String GENESIS_NETWORK_JSON = "genesis-network.json";
    public static final String OVERRIDE_NETWORK_JSON = "override-network.json";
    public static final Pattern ROUND_DIR_PATTERN = Pattern.compile("\\d+");

    private final ConfigProvider configProvider;

    private boolean isArchived = false;

    /**
     * The types of network information that could be exported to disk.
     */
    public enum InfoType {
        ROSTER,
        NODE_DETAILS,
    }

    /**
     * The types of network information that could be exported to disk.
     */
    private enum AssetUse {
        GENESIS,
        OVERRIDE,
        MIGRATION,
    }

    public DiskStartupNetworks(@NonNull final ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider);
    }

    @Override
    public Network genesisNetworkOrThrow(@NonNull final Configuration platformConfig) {
        requireNonNull(platformConfig);
        return loadNetwork(AssetUse.GENESIS, configProvider.getConfiguration(), GENESIS_NETWORK_JSON)
                .or(() -> loadFromConfigTxt(platformConfig))
                .orElseThrow(() -> new IllegalStateException("Genesis network not found"));
    }

    @Override
    public Optional<Network> overrideNetworkFor(final long roundNumber) {
        if (roundNumber == 0) {
            return Optional.empty();
        }
        final var config = configProvider.getConfiguration();
        final var unscopedNetwork = loadNetwork(AssetUse.OVERRIDE, config, OVERRIDE_NETWORK_JSON);
        if (unscopedNetwork.isPresent()) {
            return unscopedNetwork;
        }

        return loadNetwork(AssetUse.OVERRIDE, config, "" + roundNumber, OVERRIDE_NETWORK_JSON).or(() -> loadFromConfigTxt(config)).or(Optional::empty);
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
    public void archiveStartupNetworks() {
        if (isArchived) {
            return;
        }
        // We only try to archive once, as it is unlikely any error here would be recoverable
        isArchived = true;
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
                    .forEach(dir -> {
                        archiveIfPresent(config, dir.getFileName().toString(), OVERRIDE_NETWORK_JSON);
                        if (!dir.toFile().delete()) {
                            log.warn("Failed to delete round override network directory {}", dir);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to list round override network files", e);
        }
    }

    @Override
    public Network migrationNetworkOrThrow() {
        return loadNetwork(AssetUse.MIGRATION, configProvider.getConfiguration(), OVERRIDE_NETWORK_JSON)
                .or(() -> loadFromConfigTxt(configProvider.getConfiguration()))
                .orElseThrow(() -> new IllegalStateException("Transplant network not found"));
    }


    private Optional<Network> loadFromConfigTxt(Configuration configuration) {
        var configTxtPath = Paths.get(configuration.getConfigData(BootstrapConfig.class).configTxtPath());
        Optional<Network> maybeNetwork = genesisNetworkFromConfigTxt(configuration, configTxtPath);
        Optional<Network> updatedNetwork = Optional.empty();
        if (maybeNetwork.isPresent()) {
            final var network = maybeNetwork.get();
            // first try node-admin-keys.json
            //todo add!

            // then try to load the PEM file itself
            var keysPath = configuration.getConfigData(BootstrapConfig.class).nodeDiskAdminKeyPath();
            String pemAcctStr = "";
            String pemLoc = "";
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(keysPath), "*.pem")) {
                for (Path entry : stream) {
                    if (entry.getFileName().toString().contains("account")) {
                        pemAcctStr = entry.getFileName().toString().replace(".pem", "").replace(
                                "account", "");
                        pemLoc = entry.toAbsolutePath().toString();
                        break;
                    }
                }
            } catch (IOException e) {
                log.warn("Couldn't find account pem file", e);
            }

            //load .pem
            if (!pemAcctStr.isEmpty()) {
                // first, find the appropriate metadata object
                int metadataIndex = -1;
                AccountID pemAccount = AccountID.newBuilder().accountNum(
                        Long.parseLong(pemAcctStr)).build();
                for (NodeMetadata metadata : network.nodeMetadata()) {
                    if (pemAccount.equals(
                            metadata.node().accountId())) {

                        metadataIndex = network.nodeMetadata().indexOf(metadata);
                        break;
                    }
                }
                if (metadataIndex == -1) {
                    log.warn("No metadata found for pem account {}", pemAcctStr);
                    throw new IllegalStateException("No metadata found for node account " + pemAcctStr);
                }
                var nodeMetadata = network.nodeMetadata().get(metadataIndex);

                // get passphrase:
                final var passFile = Path.of(
                        pemLoc.replace(".pem", ".pass"));
                String pass = "";
                if (Files.exists(passFile)) {
                    try {
                        pass = Files.readString(passFile);
                    } catch (IOException e) {
                        throw new IllegalStateException(".pass file at {} couldn't open", e);
                    }
                } else {
                    log.warn("No passphrase found for pem account {}", pemAcctStr);
                    // There's no chance of loading the private key without the passphrase, so return
                    return Optional.empty();
                }

                // now, load the private key and build its public key
                EdDSAPrivateKey privateKey = Ed25519Utils.readKeyFrom(Paths.get(pemLoc).toFile(), pass);
                var publicKey = publicKeyFromEd25519(privateKey);
                var pubKeyBytes = Bytes.wrap(publicKey);
                var key = Key.newBuilder().ed25519(pubKeyBytes).build();

                // finally, replace the old metadata with the new one (that has the updated admin key)
                List<NodeMetadata> newMetas = new ArrayList<>(network.nodeMetadata());
                Node newNode = nodeMetadata.nodeOrThrow().copyBuilder().adminKey(key).build();
                NodeMetadata newNodeMetadata = nodeMetadata.copyBuilder().node(newNode).build();
                newMetas.set(metadataIndex, newNodeMetadata);
                // replace the old metadata with the new one
                updatedNetwork = Optional.of(Network.newBuilder().ledgerId(network.ledgerId()).nodeMetadata(newMetas).build());
            }
        }

        return updatedNetwork;
    }

    private byte[] publicKeyFromEd25519(EdDSAPrivateKey privateKey) {
       return privateKey.getAbyte();
    }

    /**
     * Writes a JSON representation of the {@link Network} information in the given state to a given path.
     *
     * @param state the state to write network information from.
     * @param path the path to write the JSON network information to.
     */
    public static void writeNetworkInfo(
            @NonNull final State state, @NonNull final Path path, @NonNull final Set<InfoType> infoTypes) {
        requireNonNull(state);
        final var nodeStore = new ReadableNodeStoreImpl(state.getReadableStates(AddressBookService.NAME));
        Optional.ofNullable(RosterRetriever.retrieveActiveOrGenesisRoster(state))
                .ifPresent(activeRoster -> {
                    final var network = Network.newBuilder();
                    final List<NodeMetadata> nodeMetadata = new ArrayList<>();
                    activeRoster.rosterEntries().forEach(entry -> {
                        final var node = requireNonNull(nodeStore.get(entry.nodeId()));
                        nodeMetadata.add(new NodeMetadata(
                                infoTypes.contains(InfoType.ROSTER) ? entry : null,
                                infoTypes.contains(InfoType.NODE_DETAILS) ? node : null));
                    });
                    network.nodeMetadata(nodeMetadata);
                    tryToExport(network.build(), path);
                });
    }

    /**
     * Attempts to export the given {@link Network} to the given path.
     * @param network the network to export
     * @param path the path to export the network to
     */
    public static void tryToExport(@NonNull final Network network, @NonNull final Path path) {
        try (final var fout = Files.newOutputStream(path)) {
            Network.JSON.write(network, new WritableStreamingData(fout));
        } catch (IOException e) {
            log.warn("Failed to write network info", e);
        }
    }

    /**
     * Converts a {@link AddressBook} to a {@link Network}. The resulting network will have no TSS
     * keys of any kind.
     *
     * @param addressBook the address book to convert
     * @return the converted network
     */
    public static @NonNull Network fromLegacyAddressBook(@NonNull final AddressBook addressBook) {
        final var roster = buildRoster(addressBook);
        return Network.newBuilder()
                .nodeMetadata(roster.rosterEntries().stream()
                        .map(rosterEntry -> {
                            final var nodeId = rosterEntry.nodeId();
                            final var nodeAccountId = parseAccount(
                                    addressBook.getAddress(NodeId.of(nodeId)).getMemo());
                            // Currently the ReadableFreezeUpgradeActions.writeConfigLineAndPem()
                            // assumes that the gossip endpoints in the Node objects are in the order
                            // (Internal, External)...even though Roster format is the reverse :/
                            final var legacyGossipEndpoints = List.of(
                                    rosterEntry.gossipEndpoint().getLast(),
                                    rosterEntry.gossipEndpoint().getFirst());
                            return NodeMetadata.newBuilder()
                                    .rosterEntry(rosterEntry)
                                    .node(Node.newBuilder()
                                            .nodeId(nodeId)
                                            .accountId(nodeAccountId)
                                            .description("node" + (nodeId + 1))
                                            .gossipEndpoint(legacyGossipEndpoints)
                                            .serviceEndpoint(List.of())
                                            .gossipCaCertificate(rosterEntry.gossipCaCertificate())
                                            .grpcCertificateHash(Bytes.EMPTY)
                                            .weight(rosterEntry.weight())
                                            .deleted(false)
                                            .adminKey(Key.DEFAULT)
                                            .build())
                                    .build();
                        })
                        .toList())
                .build();
    }

    /**
     * Attempts to load a {@link Network} from a given file in the directory whose relative path is given
     * by the provided {@link Configuration}.
     *
     * @param use the use of the network file
     * @param config the configuration to use to determine the location of the network file
     * @param segments the path segments of the file to load the network from
     * @return the loaded network, if it was found and successfully loaded
     */
    private Optional<Network> loadNetwork(
            @NonNull final AssetUse use, @NonNull final Configuration config, @NonNull final String... segments) {
        final var path = networksPath(config, segments);
        log.info("Checking for {} network info at {}", use, path.toAbsolutePath());
        final var maybeNetwork = loadNetworkFrom(path);
        maybeNetwork.ifPresentOrElse(
                network -> log.info(
                        "  -> Parsed {} network info for N={} nodes from {}",
                        use,
                        network.nodeMetadata().size(),
                        path.toAbsolutePath()),
                () -> log.info("  -> N/A"));
        return maybeNetwork;
    }

    /**
     * Attempts to load a {@link Network} from a given file.
     *
     * @param path the path to the file to load the network from
     * @return the loaded network, if it was found and successfully loaded
     */
    public static Optional<Network> loadNetworkFrom(@NonNull final Path path) {
        if (Files.exists(path)) {
            try (final var fin = Files.newInputStream(path)) {
                return Optional.of(Network.JSON.parse(new ReadableStreamingData(fin)));
            } catch (Exception e) {
                log.warn("Failed to load {} network info from {}", path.toAbsolutePath(), e);
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to load the genesis network from the default <i>config.txt</i> file.
     * @return the loaded genesis network, if it was found and successfully loaded
     */
    @Deprecated(forRemoval = true)
    private Optional<Network> genesisNetworkFromConfigTxt(@NonNull final Configuration platformConfig, @NonNull final Path configTxtPath) {
        try {
            log.info("No genesis-network.json detected, falling back to config.txt and initNodeSecurity()");
            final AddressBook legacyBook;
            final var configFile = LegacyConfigPropertiesLoader.loadConfigFile(configTxtPath);
            try {
                legacyBook = configFile.getAddressBook();
                // Load the public keys into the address book. No private keys should be loaded!
                CryptoStatic.initNodeSecurity(legacyBook, platformConfig, Set.of());
            } catch (Exception e) {
                throw new IllegalStateException("Error generating keys and certs", e);
            }
            final var network = fromLegacyAddressBook(legacyBook);
            return Optional.of(network);
        } catch (Exception e) {
            log.warn("Fallback loading genesis network from config.txt also failed", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Attempts to archive the given segments in the given configuration.
     *
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
     *
     * @param config the configuration to ensure the archive directory exists in
     */
    private static void ensureArchiveDir(@NonNull final Configuration config) throws IOException {
        createIfAbsent(networksPath(config, ARCHIVE));
    }

    /**
     * Creates the given path as a directory if it does not already exist.
     *
     * @param path the path to the directory create if it does not already exist
     */
    private static void createIfAbsent(@NonNull final Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectory(path);
        }
    }

    /**
     * Gets the path to the directory containing network files.
     *
     * @param config the configuration to use to determine the location of the network files
     * @return the path to the directory containing network files
     */
    private static Path networksPath(@NonNull final Configuration config, @NonNull final String... segments) {
        return Paths.get(config.getConfigData(NetworkAdminConfig.class).upgradeSysFilesLoc(), segments);
    }
}
