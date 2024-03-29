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
import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;
import static com.hedera.node.app.service.file.impl.FileServiceImpl.UPGRADE_DATA_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.base.Setting;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.base.TransactionFeeSchedule;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.service.file.impl.codec.FileServiceStateTranslator;
import com.hedera.node.app.service.mono.files.DataMapFactory;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.files.store.FcBlobsBytesStore;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.types.LongPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The initial schema definition for the file service.
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47FileGenesisSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class InitialModFileGenesisSchema extends Schema {
    private static final Logger logger = LogManager.getLogger(InitialModFileGenesisSchema.class);
    /**
     * A hint to the database system of the maximum number of files we will store. This MUST NOT BE CHANGED. If it is
     * changed, then the database has to be rebuilt.
     */
    private static final int MAX_FILES_HINT = 50_000_000;

    private final ConfigProvider configProvider;
    private Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fileFromState;
    private Map<com.hederahashgraph.api.proto.java.FileID, byte[]> fileContents;
    private Map<com.hederahashgraph.api.proto.java.FileID, HFileMeta> fileAttrs;

    /** Create a new instance */
    public InitialModFileGenesisSchema(
            @NonNull final SemanticVersion version, @NonNull final ConfigProvider configProvider) {
        super(version);
        this.configProvider = requireNonNull(configProvider);
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        final Set<StateDefinition> definitions = new LinkedHashSet<>();
        definitions.add(StateDefinition.onDisk(BLOBS_KEY, FileID.PROTOBUF, File.PROTOBUF, MAX_FILES_HINT));

        final FilesConfig filesConfig = configProvider.getConfiguration().getConfigData(FilesConfig.class);
        final HederaConfig hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
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

    public void setFs(@Nullable final Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss) {
        this.fileFromState = fss;
        var blobStore = new FcBlobsBytesStore(fss);
        this.fileContents = DataMapFactory.dataMapFrom(blobStore);
        this.fileAttrs = MetadataMapFactory.metaMapFrom(blobStore);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        logger.debug("Migrating genesis state");
        final var isGenesis = ctx.previousVersion() == null;
        if (isGenesis) {
            final var bootstrapConfig = ctx.configuration().getConfigData(BootstrapConfig.class);
            final var filesConfig = ctx.configuration().getConfigData(FilesConfig.class);
            final var hederaConfig = ctx.configuration().getConfigData(HederaConfig.class);
            final WritableKVState<FileID, File> files = ctx.newStates().get(BLOBS_KEY);
            createGenesisAddressBookAndNodeDetails(
                    bootstrapConfig, hederaConfig, filesConfig, files, ctx.networkInfo());
            createGenesisFeeSchedule(bootstrapConfig, hederaConfig, filesConfig, files);
            createGenesisExchangeRate(bootstrapConfig, hederaConfig, filesConfig, files);
            createGenesisNetworkProperties(bootstrapConfig, hederaConfig, filesConfig, files, ctx.configuration());
            createGenesisHapiPermissions(bootstrapConfig, hederaConfig, filesConfig, files);
            createGenesisThrottleDefinitions(bootstrapConfig, hederaConfig, filesConfig, files);
            createGenesisSoftwareUpdateFiles(bootstrapConfig, hederaConfig, filesConfig, files);
        }

        if (fileFromState != null && fileFromState.get() != null) {
            var toBlobsState = ctx.newStates().<FileID, File>get(BLOBS_KEY);

            logger.info("BBM: Running file service migration...");
            var allFileIds = extractFileIds(fileFromState.get());
            var migratedFileIds = new ArrayList<Long>();
            allFileIds.forEach(fromFileIdRaw -> {
                var fromFileId = com.hederahashgraph.api.proto.java.FileID.newBuilder()
                        .setFileNum(fromFileIdRaw)
                        .build();
                var fromFileMeta = fileAttrs.get(fromFileId);
                // Note: if the file meta is null, then this file is more specialized
                // (e.g. contract bytecode) and will be migrated elsewhere
                if (fromFileMeta != null) {
                    File toFile = FileServiceStateTranslator.stateToPbj(
                            fileContents.get(fromFileId), fromFileMeta, fromFileId);
                    toBlobsState.put(
                            FileID.newBuilder().fileNum(fromFileId.getFileNum()).build(), toFile);
                    migratedFileIds.add(fromFileIdRaw);
                }
            });

            if (toBlobsState.isModified()) ((WritableKVStateBase) toBlobsState).commit();

            logger.info("BBM: finished file service migration. Migrated fileIds are : " + migratedFileIds);
        } else {
            logger.warn("BBM: no file 'from' state found");
        }

        fileFromState = null;
        fileContents = null;
        fileAttrs = null;
    }

    private List<Long> extractFileIds(VirtualMapLike<VirtualBlobKey, VirtualBlobValue> fileStorage) {
        final var fileIds = new ArrayList<Long>();
        try {
            fileStorage.extractVirtualMapData(
                    AdHocThreadManager.getStaticThreadManager(),
                    entry -> fileIds.add((long) entry.left().getEntityNumCode()),
                    1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return fileIds;
    }

    // ================================================================================================================
    // Creates and loads the Address Book into state

    private void createGenesisAddressBookAndNodeDetails(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files,
            @NonNull final NetworkInfo networkInfo) {

        logger.debug("Creating genesis address book and node details files");

        logger.trace("Converting NetworkInfo to NodeAddressBook");
        final var nodeAddresses = new ArrayList<NodeAddress>();
        for (final var nodeInfo : networkInfo.addressBook()) {
            nodeAddresses.add(NodeAddress.newBuilder()
                    .ipAddress(Bytes.wrap(nodeInfo.externalHostName()))
                    .rsaPubKey(nodeInfo.hexEncodedPublicKey())
                    .nodeId(nodeInfo.nodeId())
                    .stake(nodeInfo.stake())
                    .memo(Bytes.wrap(nodeInfo.memo()))
                    .serviceEndpoint(ServiceEndpoint.newBuilder()
                            .ipAddressV4(Bytes.wrap(nodeInfo.externalHostName()))
                            .port(nodeInfo.externalPort())
                            .build())
                    .nodeAccountId(nodeInfo.accountId())
                    .build());
        }

        final var nodeAddressBook =
                NodeAddressBook.newBuilder().nodeAddress(nodeAddresses).build();
        final var nodeAddressBookProto = NodeAddressBook.PROTOBUF.toBytes(nodeAddressBook);

        // Create the master key that will own both of these special files
        final var masterKey = KeyList.newBuilder()
                .keys(Key.newBuilder()
                        .ed25519(bootstrapConfig.genesisPublicKey())
                        .build())
                .build();

        // Create the address book file
        final var addressBookFileNum = filesConfig.addressBook();
        final var addressBookFileId = FileID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .fileNum(addressBookFileNum)
                .build();

        logger.trace("Add address book into {}", addressBookFileNum);
        files.put(
                addressBookFileId,
                File.newBuilder()
                        .contents(nodeAddressBookProto)
                        .fileId(addressBookFileId)
                        .keys(masterKey)
                        .expirationSecond(bootstrapConfig.systemEntityExpiry())
                        .build());

        // Create the node details
        final var nodeInfoFileNum = filesConfig.nodeDetails();
        final var nodeInfoFileId = FileID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .fileNum(nodeInfoFileNum)
                .build();

        logger.trace("Add node info into {}", nodeInfoFileNum);
        files.put(
                nodeInfoFileId,
                File.newBuilder()
                        .contents(nodeAddressBookProto)
                        .fileId(nodeInfoFileId)
                        .keys(masterKey)
                        .expirationSecond(bootstrapConfig.systemEntityExpiry())
                        .build());
    }

    // ================================================================================================================
    // Creates and loads the initial Fee Schedule into state

    private void createGenesisFeeSchedule(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis fee schedule file");
        final var resourceName = bootstrapConfig.feeSchedulesJsonResource();
        try (final var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            final var feeScheduleJsonBytes = requireNonNull(in).readAllBytes();
            final var feeSchedule = parseFeeSchedules(feeScheduleJsonBytes);
            final var fileNum = filesConfig.feeSchedules();
            final var fileId = FileID.newBuilder()
                    .shardNum(hederaConfig.shard())
                    .realmNum(hederaConfig.realm())
                    .fileNum(fileNum)
                    .build();
            final var masterKey =
                    Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
            files.put(
                    fileId,
                    File.newBuilder()
                            .contents(CurrentAndNextFeeSchedule.PROTOBUF.toBytes(feeSchedule))
                            .fileId(fileId)
                            .keys(KeyList.newBuilder().keys(masterKey))
                            .expirationSecond(bootstrapConfig.systemEntityExpiry())
                            .build());
        } catch (IOException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Fee schedule (" + resourceName + ") " + "could not be found in the class path", e);
        }
    }

    private static CurrentAndNextFeeSchedule parseFeeSchedules(@NonNull final byte[] feeScheduleJsonBytes) {
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
                .subType(SubType.fromString(feeNode.get("subType").asText()))
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

    private void createGenesisExchangeRate(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis exchange rate file");
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

        final var fileNum = filesConfig.exchangeRates();
        final var fileId = FileID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .fileNum(fileNum)
                .build();
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        files.put(
                fileId,
                File.newBuilder()
                        .contents(ExchangeRateSet.PROTOBUF.toBytes(exchangeRateSet))
                        .fileId(fileId)
                        .keys(KeyList.newBuilder().keys(masterKey))
                        .expirationSecond(bootstrapConfig.systemEntityExpiry())
                        .build());
    }

    // ================================================================================================================
    // Creates and loads the network properties into state

    private void createGenesisNetworkProperties(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files,
            @NonNull final Configuration configuration) {
        logger.debug("Creating genesis network properties file");

        // Get the set of network properties from configuration, and generate the file content to store in state.
        List<Setting> settings = new ArrayList<>();
        // FUTURE: We would like to preload all network properties. If we actually do that, then we would do that here.
        //         using some kind of reflection on the configuration system.

        final var servicesConfigList =
                ServicesConfigurationList.newBuilder().nameValue(settings).build();
        final var fileNum = filesConfig.networkProperties();
        final var fileId = FileID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .fileNum(fileNum)
                .build();
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        files.put(
                fileId,
                File.newBuilder()
                        .contents(ServicesConfigurationList.PROTOBUF.toBytes(servicesConfigList))
                        .fileId(fileId)
                        .keys(KeyList.newBuilder().keys(masterKey))
                        .expirationSecond(bootstrapConfig.systemEntityExpiry())
                        .build());
    }

    // ================================================================================================================
    // Creates and loads the HAPI Permissions into state

    private void createGenesisHapiPermissions(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis HAPI permissions file");

        // Get the path to the HAPI permissions file
        final var pathToApiPermissions = Path.of(bootstrapConfig.hapiPermissionsPath());

        // If the file exists, load from there
        String apiPermissionsContent = null;
        if (Files.exists(pathToApiPermissions)) {
            try {
                apiPermissionsContent = Files.readString(pathToApiPermissions);
                logger.info("API Permissions loaded from {}", pathToApiPermissions);
            } catch (IOException e) {
                logger.error(
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

        // Parse the HAPI permissions file into a ServicesConfigurationList protobuf object
        final var settings = new ArrayList<Setting>();
        try (final var in = new StringReader(apiPermissionsContent)) {
            final var props = new Properties();
            props.load(in);
            props.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> settings.add(Setting.newBuilder()
                            .name(String.valueOf(entry.getKey()))
                            .value(String.valueOf(entry.getValue()))
                            .build()));
        } catch (final IOException e) {
            logger.fatal("API Permissions could not be parsed");
            throw new IllegalArgumentException("API Permissions could not be parsed", e);
        }

        // Store the configuration in state
        final var fileNum = filesConfig.hapiPermissions();
        final var fileId = FileID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .fileNum(fileNum)
                .build();
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        files.put(
                fileId,
                File.newBuilder()
                        .contents(ServicesConfigurationList.PROTOBUF.toBytes(ServicesConfigurationList.newBuilder()
                                .nameValue(settings)
                                .build()))
                        .fileId(fileId)
                        .keys(KeyList.newBuilder().keys(masterKey))
                        .expirationSecond(bootstrapConfig.systemEntityExpiry())
                        .build());
    }

    // ================================================================================================================
    // Creates and loads the Throttle definitions into state

    private void createGenesisThrottleDefinitions(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis throttle definitions file");

        byte[] throttleDefinitionsProtoBytes = loadBootstrapThrottleDefinitions(bootstrapConfig);

        // Store the configuration in state
        final var fileNum = filesConfig.throttleDefinitions();
        final var fileId = FileID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .fileNum(fileNum)
                .build();
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        files.put(
                fileId,
                File.newBuilder()
                        .contents(Bytes.wrap(throttleDefinitionsProtoBytes))
                        .fileId(fileId)
                        .keys(KeyList.newBuilder().keys(masterKey))
                        .expirationSecond(bootstrapConfig.systemEntityExpiry())
                        .build());
    }

    public static byte[] loadBootstrapThrottleDefinitions(@NonNull BootstrapConfig bootstrapConfig) {
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
        byte[] throttleDefinitionsProtoBytes;
        try {
            var om = new ObjectMapper();
            var throttleDefinitionsObj = om.readValue(
                    throttleDefinitionsContent,
                    com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions.class);
            throttleDefinitionsProtoBytes = throttleDefinitionsObj.toProto().toByteArray();
        } catch (IOException e) {
            logger.fatal("Throttle definitions JSON could not be parsed and converted to proto");
            throw new IllegalStateException("Throttle definitions JSON could not be parsed and converted to proto", e);
        }
        return throttleDefinitionsProtoBytes;
    }

    // ================================================================================================================
    // Creates and loads the software update file into state

    private void createGenesisSoftwareUpdateFiles(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {

        // These files all start off as an empty byte array for all upgrade files from 150-159.
        // But only file 150 is actually used, the others are not, but may be used in the future.
        logger.debug("Creating genesis software update files");
        final var fileNums = filesConfig.softwareUpdateRange();
        final var firstUpdateNum = fileNums.left();
        final var lastUpdateNum = fileNums.right();
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        // initializing the files 150 -159
        for (var updateNum = firstUpdateNum; updateNum <= lastUpdateNum; updateNum++) {
            final var fileId = FileID.newBuilder()
                    .shardNum(hederaConfig.shard())
                    .realmNum(hederaConfig.realm())
                    .fileNum(updateNum)
                    .build();
            logger.debug("Putting update file {} into state", updateNum);
            files.put(
                    fileId,
                    File.newBuilder()
                            .contents(Bytes.EMPTY)
                            .fileId(fileId)
                            .keys(KeyList.newBuilder().keys(masterKey))
                            .expirationSecond(bootstrapConfig.systemEntityExpiry())
                            .build());
        }
    }
}
