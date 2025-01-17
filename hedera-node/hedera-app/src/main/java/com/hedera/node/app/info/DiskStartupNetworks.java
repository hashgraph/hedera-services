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
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromByteString;
import static com.hedera.node.app.hapi.utils.keys.RSAUtils.parseIdFromPemLoc;
import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.hapi.utils.keys.RSAUtils;
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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    static final Logger log = LogManager.getLogger(DiskStartupNetworks.class);

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

    // (FUTURE) 🔥🔥 Once the roster lifecycle is fully implemented, we can remove the dependency on the config.txt
    // file. We should be able to delete this private class in its entirety. Let's make this a big eyesore so we remove
    // it as soon as possible 🔥🔥
    private static class ConfigTxtUtils {
        private ConfigTxtUtils() {
            // utility class
        }

        /**
         * Creates a network from the given <i>config.txt</i> file. Since config.txt alone is not
         * sufficient to fill out all the data needed in each roster entry, this code will also perform
         * two more steps: 1) attempt to read the existing public certificates for all other nodes
         * from disk, and 2) use this node's RSA key on disk, if present, to generate a certificate
         * for this node.
         *
         * @param configTxt the <b>contents</b> of the <i>config.txt</i> file
         * @param config the application's config
         * @return the network
         */
        static Optional<Network> networkFromDisk(@NonNull final String configTxt, @NonNull final Configuration config) {
            requireNonNull(configTxt);
            requireNonNull(config);

            final var networkCertsPath =
                    config.getConfigData(BootstrapConfig.class).networkCertsPath();

            final Map<Long, byte[]> certs = new HashMap<>();
            try {
                certs.putAll(PemCertsLoader.load(Paths.get(networkCertsPath)));
            } catch (CertificateEncodingException e) {
                log.warn("Couldn't load node certs", e);
            }
            if (certs.isEmpty()) {
                // There's no point in loading the rest of configTxt if we don't have the certs available
                return Optional.empty();
            }

            final var nodeMetadata = Arrays.stream(configTxt.split("\n"))
                    .filter(line -> line.contains("address, "))
                    .map(line -> {
                        final var parts = line.split(", ");
                        final long nodeId = Long.parseLong(parts[1]);
                        final long weight = Long.parseLong(parts[4]);
                        final var gossipEndpoints =
                                List.of(endpointFrom(parts[5], parts[6]), endpointFrom(parts[7], parts[8]));
                        final var nodeAcctId = asAccount(parts[9]);
                        if (!certs.containsKey(nodeId)) {
                            throw new IllegalStateException("Missing cert for node " + nodeId);
                        }
                        final var certBytes = Bytes.wrap(certs.get(nodeId));
                        final var metadata = NodeMetadata.newBuilder()
                                .rosterEntry(new RosterEntry(nodeId, weight, certBytes, gossipEndpoints));
                        metadata.node(new Node(
                                nodeId,
                                nodeAcctId,
                                "node" + (nodeId + 1),
                                gossipEndpoints,
                                List.of(),
                                certBytes,
                                // The gRPC certificate hash is irrelevant for PR checks
                                Bytes.EMPTY,
                                weight,
                                false,
                                null));

                        return metadata.build();
                    })
                    .toList();
            return Optional.of(Network.newBuilder().nodeMetadata(nodeMetadata).build());
        }

        static ServiceEndpoint asServiceEndpoint(String v) {
            String[] parts = v.split(":");
            return ServiceEndpoint.newBuilder()
                    .ipAddressV4(fromByteString(asOctets(parts[0])))
                    .port(Integer.parseInt(parts[1]))
                    .build();
        }

        static ByteString asOctets(final String ipAddressV4) {
            final byte[] octets = new byte[4];
            final String[] literals = ipAddressV4.split("[.]");
            for (int i = 0; i < 4; i++) {
                octets[i] = (byte) Integer.parseInt(literals[i]);
            }
            return ByteString.copyFrom(octets);
        }

        static AccountID asAccount(String v) {
            final long[] nativeParts = asDotDelimitedLongArray(v);
            return AccountID.newBuilder()
                    .shardNum(nativeParts[0])
                    .realmNum(nativeParts[1])
                    .accountNum(nativeParts[2])
                    .build();
        }

        static long[] asDotDelimitedLongArray(String s) {
            final String[] parts = s.split("[.]");
            return Stream.of(parts).mapToLong(Long::valueOf).toArray();
        }

        static ServiceEndpoint endpointFrom(@NonNull final String hostLiteral, @NonNull final String portLiteral) {
            return asServiceEndpoint(hostLiteral + ":" + portLiteral);
        }

        private static class PemCertsLoader {
            private PemCertsLoader() {
                // utility class
            }

            static Map<Long, byte[]> load(@NonNull final Path certsPath) throws CertificateEncodingException {
                final var pemCerts = maybeDiskCertFiles(certsPath);
                final var certsByNodeAcctId = new HashMap<Long, byte[]>();
                for (final var pemPair : pemCerts.entrySet()) {
                    final var pemAcctId = pemPair.getKey();
                    final var pemLoc = pemPair.getValue();
                    final Optional<X509Certificate> maybeCert = maybeLoadDiskCert(pemLoc);
                    Optional<RSAPrivateKey> maybeKey = Optional.empty();
                    if (maybeCert.isEmpty()) {
                        // We'll need to generate the cert for this node using its private key
                        maybeKey = maybeLoadPrivateKey(pemLoc);
                    }

                    Optional<X509Certificate> cert;
                    if (maybeCert.isPresent()) {
                        cert = maybeCert;
                    } else {
                        cert = maybeKey.flatMap(privateKey -> maybeGenerateCert(privateKey, 0));
                    }

                    final var certBytes = cert.map(a -> {
                                try {
                                    return a.getEncoded();
                                } catch (CertificateEncodingException e) {
                                    log.warn("Unable to encode cert", e);
                                }

                                return new byte[0];
                            })
                            .get();
                    certsByNodeAcctId.put(pemAcctId, certBytes);
                }

                return certsByNodeAcctId;
            }

            private static Map<Long, Path> maybeDiskCertFiles(@NonNull final Path certsPath) {
                final Map<Long, Path> certFiles = new HashMap<>();
                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(certsPath, "*.pem")) {
                    for (Path entry : stream) {
                        if (entry.getFileName().toString().contains("s-public-node")) {
                            final var pemAcctId = parseIdFromPemLoc(entry);
                            certFiles.put(pemAcctId, entry);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error locating pem file(s)", e);
                }

                return certFiles;
            }

            private static Optional<X509Certificate> maybeLoadDiskCert(Path pemLoc) {
                try {
                    return Optional.of(RSAUtils.parseCertificate(pemLoc.toString()));
                } catch (final Exception e) {
                    log.warn("Error loading PEM as certificate from {}", pemLoc, e);
                    return Optional.empty();
                }
            }

            private static Optional<RSAPrivateKey> maybeLoadPrivateKey(Path pemLoc) {
                try {
                    return Optional.of(RSAUtils.loadPrivateKey(pemLoc.toString(), "pass"));
                } catch (Exception e) {
                    log.warn("Error loading PEM as private key from {}", pemLoc, e);
                    return Optional.empty();
                }
            }

            private static Optional<X509Certificate> maybeGenerateCert(
                    @NonNull final RSAPrivateKey privateKey, final int nodeId) {
                try {
                    return Optional.of(RSAUtils.generateCertificate(privateKey, nodeId));
                } catch (Exception e) {
                    log.warn("Unable to generate certificate for node {}", nodeId, e);
                    return Optional.empty();
                }
            }
        }
    }

    public DiskStartupNetworks(@NonNull final ConfigProvider configProvider) {
        this.configProvider = requireNonNull(configProvider);
    }

    @Override
    public Network genesisNetworkOrThrow(@NonNull final Configuration platformConfig) {
        requireNonNull(platformConfig);
        return loadNetwork(AssetUse.GENESIS, configProvider.getConfiguration(), GENESIS_NETWORK_JSON)
                .or(() -> fromConfigTxt(platformConfig))
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

        return loadNetwork(AssetUse.OVERRIDE, config, "" + roundNumber, OVERRIDE_NETWORK_JSON)
                .or(() -> fromConfigTxt(config))
                .or(Optional::empty);
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
                .or(() -> fromConfigTxt(configProvider.getConfiguration()))
                .orElseThrow(() -> new IllegalStateException("Transplant network not found"));
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
    private Optional<Network> genesisNetworkFromConfigTxt(
            @NonNull final Configuration platformConfig, @NonNull final Path configTxtPath) {
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

    private static Optional<Network> fromConfigTxt(@NonNull final Configuration config) {
        final var configTxtPath =
                Paths.get(config.getConfigData(BootstrapConfig.class).configTxtPath());
        final var configTxt = LegacyConfigPropertiesLoader.loadConfigFile(configTxtPath)
                .getAddressBook()
                .toConfigText();
        return ConfigTxtUtils.networkFromDisk(configTxt, config);
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
