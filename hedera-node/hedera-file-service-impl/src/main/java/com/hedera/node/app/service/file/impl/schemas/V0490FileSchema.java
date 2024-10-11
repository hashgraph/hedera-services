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

package com.hedera.node.app.service.file.impl.schemas;

import static com.hedera.hapi.node.base.HederaFunctionality.fromString;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.swirlds.common.utility.CommonUtils.hex;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.DISTINCT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.NodeAddress;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.base.Setting;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.base.TransactionFeeSchedule;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema;
import com.hedera.node.app.spi.workflows.SystemContext;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.types.LongPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The initial schema definition for the file service.
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47FileGenesisSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
@Singleton
public class V0490FileSchema extends Schema {
    private static final Logger logger = LogManager.getLogger(V0490FileSchema.class);

    private static final AtomicInteger NEXT_DISPATCH_NONCE = new AtomicInteger(1);

    public static final String BLOBS_KEY = "FILES";
    public static final String UPGRADE_FILE_KEY = "UPGRADE_FILE";
    public static final String UPGRADE_DATA_KEY = "UPGRADE_DATA[%s]";

    /**
     * A hint to the database system of the maximum number of files we will store. This MUST NOT BE CHANGED. If it is
     * changed, then the database has to be rebuilt.
     */
    private static final int MAX_FILES_HINT = 50_000_000;
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    /**
     * Constructs a new {@link V0490FileSchema} instance with the given {@link ConfigProvider}.
     */
    @Inject
    public V0490FileSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate(@NonNull final Configuration config) {
        final Set<StateDefinition> definitions = new LinkedHashSet<>();
        definitions.add(StateDefinition.onDisk(BLOBS_KEY, FileID.PROTOBUF, File.PROTOBUF, MAX_FILES_HINT));

        final FilesConfig filesConfig = config.getConfigData(FilesConfig.class);
        final HederaConfig hederaConfig = config.getConfigData(HederaConfig.class);
        final LongPair fileNums = filesConfig.softwareUpdateRange();
        final long firstUpdateNum = fileNums.left();
        final long lastUpdateNum = fileNums.right();

        // initializing the files 150 -159
        for (var updateNum = firstUpdateNum; updateNum <= lastUpdateNum; updateNum++) {
            final var fileId = FileID.newBuilder()
                    .shardNum(hederaConfig.shard())
                    .realmNum(hederaConfig.realm())
                    .fileNum(updateNum)
                    .build();
            definitions.add(StateDefinition.queue(UPGRADE_DATA_KEY.formatted(fileId), ProtoBytes.PROTOBUF));
        }

        return definitions;
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        // No-op, genesis system files are created via dispatch during the genesis transaction
    }

    // ================================================================================================================
    // Creates and loads the Address Book into state

    public void createGenesisAddressBookAndNodeDetails(@NonNull final SystemContext systemContext) {
        requireNonNull(systemContext);
        final var networkInfo = systemContext.networkInfo();
        final var filesConfig = systemContext.configuration().getConfigData(FilesConfig.class);
        final var bootstrapConfig = systemContext.configuration().getConfigData(BootstrapConfig.class);

        // Create the master key that will own both of these special files
        final var masterKey = KeyList.newBuilder()
                .keys(Key.newBuilder()
                        .ed25519(bootstrapConfig.genesisPublicKey())
                        .build())
                .build();

        // Create the address book file
        final var addressBookFileNum = filesConfig.addressBook();
        systemContext.dispatchCreation(
                TransactionBody.newBuilder()
                        .fileCreate(FileCreateTransactionBody.newBuilder()
                                .contents(genesisAddressBook(networkInfo))
                                .keys(masterKey)
                                .expirationTime(maxLifetimeExpiry(systemContext))
                                .build())
                        .build(),
                addressBookFileNum);

        final var nodeInfoFileNum = filesConfig.nodeDetails();
        systemContext.dispatchCreation(
                TransactionBody.newBuilder()
                        .fileCreate(FileCreateTransactionBody.newBuilder()
                                .contents(genesisNodeDetails(networkInfo))
                                .keys(masterKey)
                                .expirationTime(maxLifetimeExpiry(systemContext))
                                .build())
                        .build(),
                nodeInfoFileNum);
    }

    public Bytes genesisAddressBook(@NonNull final NetworkInfo networkInfo) {
        final var nodeAddresses = new ArrayList<NodeAddress>();
        for (final var nodeInfo : networkInfo.addressBook()) {
            nodeAddresses.add(NodeAddress.newBuilder()
                    .nodeId(nodeInfo.nodeId())
                    .rsaPubKey(nodeInfo.hexEncodedPublicKey())
                    .nodeAccountId(nodeInfo.accountId()) // don't use memo as it is deprecated.
                    .serviceEndpoint(
                            // we really don't have grpc proxy name and port for now. Temporary values are set.
                            // After Dynamic Address Book Phase 2 release, we will have the correct values.Then update
                            // here.
                            V053AddressBookSchema.endpointFor("1.0.0.0", 1))
                    .build());
        }
        return NodeAddressBook.PROTOBUF.toBytes(
                NodeAddressBook.newBuilder().nodeAddress(nodeAddresses).build());
    }

    public Bytes genesisNodeDetails(@NonNull final NetworkInfo networkInfo) {
        final var nodeDetails = new ArrayList<NodeAddress>();
        for (final var nodeInfo : networkInfo.addressBook()) {
            nodeDetails.add(NodeAddress.newBuilder()
                    .stake(nodeInfo.stake())
                    .nodeAccountId(nodeInfo.accountId())
                    .nodeId(nodeInfo.nodeId())
                    .rsaPubKey(nodeInfo.hexEncodedPublicKey())
                    // we really don't have grpc proxy name and port for now.Temporary values are set.
                    // After Dynamic Address Book Phase 2 release, we will have the correct values. Then update here.
                    .serviceEndpoint(V053AddressBookSchema.endpointFor("1.0.0.0", 1))
                    .build());
        }
        return NodeAddressBook.PROTOBUF.toBytes(
                NodeAddressBook.newBuilder().nodeAddress(nodeDetails).build());
    }

    public void updateAddressBookAndNodeDetailsAfterFreeze(
            @NonNull final SystemContext systemContext, @NonNull final ReadableNodeStore nodeStore) {
        requireNonNull(systemContext);
        final var config = systemContext.configuration();
        final var filesConfig = config.getConfigData(FilesConfig.class);
        // Create the nodeDetails for file 102
        dispatchSynthFileUpdate(
                systemContext, createFileID(filesConfig.nodeDetails(), config), nodeStoreNodeDetails(nodeStore));
        // Create the addressBook for file 101
        dispatchSynthFileUpdate(
                systemContext, createFileID(filesConfig.addressBook(), config), nodeStoreAddressBook(nodeStore));
    }

    /**
     * Given a {@link SystemContext}, dispatches a synthetic file update transaction for the given file ID and contents.
     *
     * @param systemContext the system context
     * @param fileId the file ID
     * @param contents the contents of the file
     */
    public static void dispatchSynthFileUpdate(
            @NonNull final SystemContext systemContext, @NonNull final FileID fileId, @NonNull final Bytes contents) {
        final var config = systemContext.configuration();
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        final var sysAdminId = AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(config.getConfigData(AccountsConfig.class).systemAdmin())
                .build();
        systemContext.dispatchUpdate(TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(sysAdminId)
                        .transactionValidStart(asTimestamp(systemContext.now()))
                        .nonce(NEXT_DISPATCH_NONCE.getAndIncrement())
                        .build())
                .fileUpdate(FileUpdateTransactionBody.newBuilder()
                        .fileID(fileId)
                        .contents(contents)
                        .expirationTime(maxLifetimeExpiry(systemContext))
                        .build())
                .build());
    }

    private Bytes nodeStoreNodeDetails(@NonNull final ReadableNodeStore nodeStore) {
        final var nodeDetails = new ArrayList<NodeAddress>();
        StreamSupport.stream(Spliterators.spliterator(nodeStore.keys(), nodeStore.sizeOfState(), DISTINCT), false)
                .mapToLong(EntityNumber::number)
                .mapToObj(nodeStore::get)
                .filter(node -> node != null && !node.deleted())
                .forEach(node -> nodeDetails.add(NodeAddress.newBuilder()
                        .nodeId(node.nodeId())
                        .nodeAccountId(node.accountId())
                        .nodeCertHash(node.grpcCertificateHash())
                        .description(node.description())
                        .stake(node.weight())
                        .rsaPubKey(readableKey(getPublicKeyFromCertBytes(
                                node.gossipCaCertificate().toByteArray(), node.nodeId())))
                        .serviceEndpoint(node.serviceEndpoint())
                        .build()));
        return NodeAddressBook.PROTOBUF.toBytes(
                NodeAddressBook.newBuilder().nodeAddress(nodeDetails).build());
    }

    private Bytes nodeStoreAddressBook(@NonNull final ReadableNodeStore nodeStore) {
        final var nodeAddresses = new ArrayList<NodeAddress>();
        StreamSupport.stream(Spliterators.spliterator(nodeStore.keys(), nodeStore.sizeOfState(), DISTINCT), false)
                .mapToLong(EntityNumber::number)
                .mapToObj(nodeStore::get)
                .filter(node -> node != null && !node.deleted())
                .forEach(node -> nodeAddresses.add(NodeAddress.newBuilder()
                        .nodeId(node.nodeId())
                        .nodeCertHash(node.grpcCertificateHash())
                        .nodeAccountId(node.accountId())
                        .serviceEndpoint(node.serviceEndpoint())
                        .build()));
        return NodeAddressBook.PROTOBUF.toBytes(
                NodeAddressBook.newBuilder().nodeAddress(nodeAddresses).build());
    }

    // ================================================================================================================
    // Creates and loads the initial Fee Schedule into state

    public void createGenesisFeeSchedule(@NonNull final SystemContext systemContext) {
        requireNonNull(systemContext);
        final var config = systemContext.configuration();
        final var bootstrapConfig = config.getConfigData(BootstrapConfig.class);
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        systemContext.dispatchCreation(
                TransactionBody.newBuilder()
                        .fileCreate(FileCreateTransactionBody.newBuilder()
                                .contents(genesisFeeSchedules(config))
                                .keys(KeyList.newBuilder().keys(masterKey))
                                .expirationTime(maxLifetimeExpiry(systemContext))
                                .build())
                        .build(),
                config.getConfigData(FilesConfig.class).feeSchedules());
    }

    /**
     * Returns the genesis fee schedules for the given configuration.
     *
     * @param config the configuration
     * @return the genesis fee schedules
     */
    public Bytes genesisFeeSchedules(@NonNull final Configuration config) {
        final var resourceName = config.getConfigData(BootstrapConfig.class).feeSchedulesJsonResource();
        try (final var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            final var feeScheduleJsonBytes = requireNonNull(in).readAllBytes();
            final var feeSchedule = parseFeeSchedules(feeScheduleJsonBytes);
            return CurrentAndNextFeeSchedule.PROTOBUF.toBytes(feeSchedule);
        } catch (IOException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Fee schedule (" + resourceName + ") " + "could not be found in the class path", e);
        }
    }

    /**
     * Deserializes a JSON object representing a {@link CurrentAndNextFeeSchedule} message from the given bytes,
     * returning the equivalent protobuf message.
     *
     * @param feeScheduleJsonBytes the bytes of the JSON object representing the fee schedules
     * @return the {@link CurrentAndNextFeeSchedule} message
     */
    public static CurrentAndNextFeeSchedule parseFeeSchedules(@NonNull final byte[] feeScheduleJsonBytes) {
        try {
            final var json = new ObjectMapper();
            final var rootNode = json.readTree(feeScheduleJsonBytes);
            final var builder = CurrentAndNextFeeSchedule.newBuilder();
            final var schedules = rootNode.elements();
            while (schedules.hasNext()) {
                final var scheduleContainerNode = schedules.next();
                if (scheduleContainerNode.has("currentFeeSchedule")) {
                    final var currentFeeSchedule = parseFeeSchedule(scheduleContainerNode.get("currentFeeSchedule"));
                    builder.currentFeeSchedule(currentFeeSchedule);
                } else if (scheduleContainerNode.has("nextFeeSchedule")) {
                    final var nextFeeSchedule = parseFeeSchedule(scheduleContainerNode.get("nextFeeSchedule"));
                    builder.nextFeeSchedule(nextFeeSchedule);
                } else {
                    logger.warn("Unexpected node encountered while parsing fee schedule: {}", scheduleContainerNode);
                }
            }

            return builder.build();
        } catch (final Exception e) {
            throw new IllegalArgumentException("Unable to parse fee schedule file", e);
        }
    }

    private static FeeSchedule parseFeeSchedule(@NonNull final JsonNode scheduleNode) {
        final var builder = FeeSchedule.newBuilder();
        final var transactionFeeSchedules = new ArrayList<TransactionFeeSchedule>();
        final var transactionFeeScheduleIterator = scheduleNode.elements();
        while (transactionFeeScheduleIterator.hasNext()) {
            final var childNode = transactionFeeScheduleIterator.next();
            if (childNode.has("transactionFeeSchedule")) {
                final var transactionFeeScheduleNode = childNode.get("transactionFeeSchedule");
                final var feesContainer = transactionFeeScheduleNode.get("fees");
                final var feeDataList = new ArrayList<FeeData>();
                feesContainer.elements().forEachRemaining(feeNode -> feeDataList.add(parseFeeData(feeNode)));
                transactionFeeSchedules.add(TransactionFeeSchedule.newBuilder()
                        .hederaFunctionality(fromString(transactionFeeScheduleNode
                                .get("hederaFunctionality")
                                .asText()))
                        .fees(feeDataList)
                        .build());
            } else if (childNode.has("expiryTime")) {
                final var expiryTime = childNode.get("expiryTime").asLong();
                builder.expiryTime(TimestampSeconds.newBuilder().seconds(expiryTime));
            } else {
                logger.warn("Unexpected node encountered while parsing fee schedule: {}", childNode);
            }
        }

        return builder.transactionFeeSchedule(transactionFeeSchedules).build();
    }

    private static FeeData parseFeeData(@NonNull final JsonNode feeNode) {
        return FeeData.newBuilder()
                .subType((Optional.ofNullable(feeNode.get("subType"))
                        .map(JsonNode::asText)
                        .map(SubType::fromString)
                        .orElse(SubType.DEFAULT)))
                .nodedata(parseFeeComponents(feeNode.get("nodedata")))
                .networkdata(parseFeeComponents(feeNode.get("networkdata")))
                .servicedata(parseFeeComponents(feeNode.get("servicedata")))
                .build();
    }

    private static FeeComponents parseFeeComponents(@NonNull final JsonNode componentNode) {
        return FeeComponents.newBuilder()
                .constant(componentNode.get("constant").asLong())
                .bpt(componentNode.get("bpt").asLong())
                .vpt(componentNode.get("vpt").asLong())
                .rbh(componentNode.get("rbh").asLong())
                .sbh(componentNode.get("sbh").asLong())
                .gas(componentNode.get("gas").asLong())
                .bpr(componentNode.get("bpr").asLong())
                .sbpr(componentNode.get("sbpr").asLong())
                .min(componentNode.get("min").asLong())
                .max(componentNode.get("max").asLong())
                .build();
    }

    // ================================================================================================================
    // Creates and loads the initial Exchange Rate into state

    public void createGenesisExchangeRate(@NonNull final SystemContext systemContext) {
        final var config = systemContext.configuration();
        final var masterKey = Key.newBuilder()
                .ed25519(config.getConfigData(BootstrapConfig.class).genesisPublicKey())
                .build();
        systemContext.dispatchCreation(
                TransactionBody.newBuilder()
                        .fileCreate(FileCreateTransactionBody.newBuilder()
                                .contents(genesisExchangeRates(config))
                                .keys(KeyList.newBuilder().keys(masterKey))
                                .expirationTime(maxLifetimeExpiry(systemContext))
                                .build())
                        .build(),
                systemContext.configuration().getConfigData(FilesConfig.class).exchangeRates());
    }

    /**
     * Returns the genesis exchange rates for the given configuration.
     *
     * @param config the configuration
     * @return the genesis exchange rates
     */
    public Bytes genesisExchangeRates(@NonNull final Configuration config) {
        final var bootstrapConfig = config.getConfigData(BootstrapConfig.class);
        // See HfsSystemFilesManager#defaultRates. This does the same thing.
        final var exchangeRateSet = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .centEquiv(bootstrapConfig.ratesCurrentCentEquiv())
                        .hbarEquiv(bootstrapConfig.ratesCurrentHbarEquiv())
                        .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesCurrentExpiry()))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .centEquiv(bootstrapConfig.ratesNextCentEquiv())
                        .hbarEquiv(bootstrapConfig.ratesNextHbarEquiv())
                        .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesNextExpiry()))
                        .build())
                .build();
        return ExchangeRateSet.PROTOBUF.toBytes(exchangeRateSet);
    }

    // ================================================================================================================
    // Creates and loads the network properties into state

    public void createGenesisNetworkProperties(@NonNull final SystemContext systemContext) {
        final var config = systemContext.configuration();
        final var bootstrapConfig = config.getConfigData(BootstrapConfig.class);
        // The overrides file is initially empty
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        systemContext.dispatchCreation(
                TransactionBody.newBuilder()
                        .fileCreate(FileCreateTransactionBody.newBuilder()
                                .contents(genesisNetworkProperties(config))
                                .keys(KeyList.newBuilder().keys(masterKey))
                                .expirationTime(maxLifetimeExpiry(systemContext))
                                .build())
                        .build(),
                systemContext.configuration().getConfigData(FilesConfig.class).networkProperties());
    }

    /**
     * Returns the genesis network properties for the given configuration.
     *
     * @param config the configuration
     * @return the genesis network properties
     */
    public Bytes genesisNetworkProperties(@NonNull final Configuration config) {
        final var servicesConfigList =
                ServicesConfigurationList.newBuilder().nameValue(List.of()).build();
        return ServicesConfigurationList.PROTOBUF.toBytes(servicesConfigList);
    }

    // ================================================================================================================
    // Creates and loads the HAPI Permissions into state
    public void createGenesisHapiPermissions(@NonNull final SystemContext systemContext) {
        final var config = systemContext.configuration();
        final var bootstrapConfig = config.getConfigData(BootstrapConfig.class);
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        systemContext.dispatchCreation(
                TransactionBody.newBuilder()
                        .fileCreate(FileCreateTransactionBody.newBuilder()
                                .contents(genesisHapiPermissions(config))
                                .keys(KeyList.newBuilder().keys(masterKey))
                                .expirationTime(maxLifetimeExpiry(systemContext))
                                .build())
                        .build(),
                systemContext.configuration().getConfigData(FilesConfig.class).hapiPermissions());
    }

    public Bytes genesisHapiPermissions(@NonNull final Configuration config) {
        final var bootstrapConfig = config.getConfigData(BootstrapConfig.class);
        // Get the path to the HAPI permissions file
        final var pathToApiPermissions = Path.of(bootstrapConfig.hapiPermissionsPath());
        // If the file exists, load from there
        String apiPermissionsContent = null;
        if (Files.exists(pathToApiPermissions)) {
            try {
                apiPermissionsContent = Files.readString(pathToApiPermissions);
                logger.info("API Permissions loaded from {}", pathToApiPermissions);
            } catch (IOException e) {
                logger.warn(
                        "API Permissions could not be loaded from {}, looking for fallback on classpath",
                        pathToApiPermissions);
            }
        }
        // Otherwise, load from the classpath. If that cannot be done, we have a totally broken build.
        if (apiPermissionsContent == null) {
            final var resourceName = "api-permission.properties";
            try (final var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
                apiPermissionsContent = new String(requireNonNull(in).readAllBytes(), UTF_8);
                logger.info("API Permissions loaded from classpath resource {}", resourceName);
            } catch (IOException | NullPointerException e) {
                logger.fatal("API Permissions could not be loaded from classpath");
                throw new IllegalArgumentException("API Permissions could not be loaded from classpath", e);
            }
        }
        return parseConfigList("HAPI permissions", apiPermissionsContent);
    }

    /**
     * Extracts the text-based key/value pairs from the given content as a Java properties file, accumulates all the
     * settings into a {@link ServicesConfigurationList} message and returns the serialized bytes of the message.
     *
     * @param purpose the purpose of the configuration
     * @param content the content of the configuration
     * @return the serialized bytes of the {@link ServicesConfigurationList} message
     */
    public static Bytes parseConfigList(@NonNull final String purpose, @NonNull final String content) {
        final var settings = new ArrayList<Setting>();
        try (final var in = new StringReader(content)) {
            final var props = new Properties();
            props.load(in);
            props.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> settings.add(Setting.newBuilder()
                            .name(String.valueOf(entry.getKey()))
                            .value(String.valueOf(entry.getValue()))
                            .build()));
        } catch (final IOException e) {
            throw new IllegalArgumentException(purpose + " config could not be parsed", e);
        }
        return ServicesConfigurationList.PROTOBUF.toBytes(
                ServicesConfigurationList.newBuilder().nameValue(settings).build());
    }

    // ================================================================================================================
    // Creates and loads the Throttle definitions into state
    public void createGenesisThrottleDefinitions(@NonNull final SystemContext systemContext) {
        final var config = systemContext.configuration();
        final var bootstrapConfig = config.getConfigData(BootstrapConfig.class);
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        systemContext.dispatchCreation(
                TransactionBody.newBuilder()
                        .fileCreate(FileCreateTransactionBody.newBuilder()
                                .contents(genesisThrottleDefinitions(config))
                                .keys(KeyList.newBuilder().keys(masterKey))
                                .expirationTime(maxLifetimeExpiry(systemContext))
                                .build())
                        .build(),
                systemContext.configuration().getConfigData(FilesConfig.class).throttleDefinitions());
    }

    /**
     * Returns the genesis throttle definitions for the given configuration.
     *
     * @param config the configuration
     * @return the genesis throttle definitions
     */
    public Bytes genesisThrottleDefinitions(@NonNull final Configuration config) {
        final var bootstrapConfig = config.getConfigData(BootstrapConfig.class);
        final var throttleDefinitionsProtoBytes = loadBootstrapThrottleDefinitions(bootstrapConfig);
        return Bytes.wrap(throttleDefinitionsProtoBytes);
    }

    /**
     * Load the throttle definitions from the bootstrap configuration.
     *
     * @param bootstrapConfig the bootstrap configuration
     * @return the throttle definitions proto as a byte array
     */
    private static byte[] loadBootstrapThrottleDefinitions(@NonNull BootstrapConfig bootstrapConfig) {
        // Get the path to the throttles permissions file
        final var throttleDefinitionsResource = bootstrapConfig.throttleDefsJsonResource();
        final var pathToThrottleDefinitions = Path.of(throttleDefinitionsResource);

        // If the file exists, load from there
        String throttleDefinitionsContent = null;
        if (Files.exists(pathToThrottleDefinitions)) {
            try {
                throttleDefinitionsContent = Files.readString(pathToThrottleDefinitions);
                logger.info("Throttle definitions loaded from {}", pathToThrottleDefinitions);
            } catch (IOException e) {
                logger.warn(
                        "Throttle definitions could not be loaded from {}, looking for fallback on classpath",
                        pathToThrottleDefinitions);
            }
        }

        // Otherwise, load from the classpath. If that cannot be done, we have a totally broken build.
        if (throttleDefinitionsContent == null) {
            try (final var in =
                    Thread.currentThread().getContextClassLoader().getResourceAsStream(throttleDefinitionsResource)) {
                throttleDefinitionsContent = new String(requireNonNull(in).readAllBytes(), UTF_8);
                logger.info("Throttle definitions loaded from classpath resource {}", throttleDefinitionsResource);
            } catch (IOException | NullPointerException e) {
                logger.fatal("Throttle definitions could not be loaded from disk or from classpath");
                throw new IllegalStateException("Throttle definitions could not be loaded from classpath", e);
            }
        }

        // Parse the throttle definitions JSON file into a ServicesConfigurationList protobuf object
        return parseThrottleDefinitions(throttleDefinitionsContent);
    }

    /**
     * Deserializes a JSON object representing a {@link ThrottleDefinitions} message from the given bytes,
     * returning the serialized bytes of the equivalent protobuf message.
     *
     * @param throttleJson the serialized JSON representing the fee schedules
     * @return the {@link CurrentAndNextFeeSchedule} message
     */
    public static byte[] parseThrottleDefinitions(@NonNull final String throttleJson) {
        try {
            final var om = new ObjectMapper();
            final var throttleDefinitionsObj = om.readValue(
                    throttleJson, com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions.class);
            return throttleDefinitionsObj.toProto().toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse throttle definitions", e);
        }
    }

    // ================================================================================================================
    // Creates and loads the software update file into state
    public void createGenesisSoftwareUpdateFiles(@NonNull final SystemContext systemContext) {
        final var bootstrapConfig = systemContext.configuration().getConfigData(BootstrapConfig.class);
        // These files all start off as an empty byte array for all upgrade files from 150-159.
        // But only file 150 is actually used, the others are not, but may be used in the future.
        final var updateFilesRange =
                systemContext.configuration().getConfigData(FilesConfig.class).softwareUpdateRange();
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        // initializing the files 150 -159
        for (var updateNum = updateFilesRange.left(); updateNum <= updateFilesRange.right(); updateNum++) {
            systemContext.dispatchCreation(
                    TransactionBody.newBuilder()
                            .fileCreate(FileCreateTransactionBody.newBuilder()
                                    .contents(Bytes.EMPTY)
                                    .keys(KeyList.newBuilder().keys(masterKey))
                                    .expirationTime(maxLifetimeExpiry(systemContext))
                                    .build())
                            .build(),
                    updateNum);
        }
    }

    private static Timestamp maxLifetimeExpiry(@NonNull final SystemContext systemContext) {
        return Timestamp.newBuilder()
                .seconds(systemContext.now().getEpochSecond()
                        + systemContext
                                .configuration()
                                .getConfigData(EntitiesConfig.class)
                                .maxLifetime())
                .build();
    }

    private PublicKey getPublicKeyFromCertBytes(@NonNull final byte[] certBytes, long nodeId) {
        try {
            final var certificate = (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certBytes));
            return certificate.getPublicKey();
        } catch (final CertificateException e) {
            logger.error("Unable to extract RSA key for node{} from certificate bytes {}", nodeId, hex(certBytes), e);
        }
        return null;
    }

    private String readableKey(@Nullable final PublicKey publicKey) {
        if (publicKey == null) {
            return "";
        } else {
            return hex(publicKey.getEncoded());
        }
    }

    private static FileID createFileID(final long fileNum, @NonNull final Configuration config) {
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        return FileID.newBuilder()
                .realmNum(hederaConfig.realm())
                .shardNum(hederaConfig.shard())
                .fileNum(fileNum)
                .build();
    }
}
