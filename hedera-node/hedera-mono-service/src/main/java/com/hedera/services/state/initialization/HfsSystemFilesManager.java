/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.initialization;

import static com.google.protobuf.TextFormat.escapeBytes;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_HAPI_PERMISSIONS_PATH;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_NETWORK_PROPERTIES_PATH;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_RATES_CURRENT_CENT_EQUIV;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_RATES_CURRENT_EXPIRY;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_RATES_NEXT_CENT_EQUIV;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_RATES_NEXT_EXPIRY;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_RATES_NEXT_HBAR_EQUIV;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE;
import static com.hedera.services.sysfiles.serdes.FeesJsonToProtoSerde.loadFeeScheduleFromStream;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static com.swirlds.common.system.address.Address.ipString;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.SysFileCallbacks;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public final class HfsSystemFilesManager implements SystemFilesManager {
    private static final Logger log = LogManager.getLogger(HfsSystemFilesManager.class);
    private static final String PROPERTIES_SYS_FILE_NAME = "properties";
    private static final String PERMISSIONS_SYS_FILE_NAME = "API permissions";
    private static final String EXCHANGE_RATES_SYS_FILE_NAME = "exchange rates";
    private static final String FEE_SCHEDULES_SYS_FILE_NAME = "fee schedules";
    private static final String THROTTLE_DEFINITIONS_SYS_FILE_NAME = "throttle definitions";

    private String propsSysFileDefaultResource = "application.properties";
    private String permsSysFileDefaultResource = "api-permission.properties";

    private JKey systemKey;
    private boolean filesLoaded = false;
    private final FileNumbers fileNumbers;
    private final PropertySource properties;
    private final TieredHederaFs hfs;
    private final Supplier<JEd25519Key> keySupplier;
    private final Supplier<AddressBook> bookSupplier;
    private final SysFileCallbacks callbacks;

    @Inject
    public HfsSystemFilesManager(
            final Supplier<AddressBook> bookSupplier,
            final FileNumbers fileNumbers,
            @CompositeProps final PropertySource properties,
            final TieredHederaFs hfs,
            final Supplier<JEd25519Key> keySupplier,
            final SysFileCallbacks callbacks) {
        this.hfs = hfs;
        this.callbacks = callbacks;
        this.properties = properties;
        this.bookSupplier = bookSupplier;
        this.fileNumbers = fileNumbers;
        this.keySupplier = keySupplier;
    }

    @Override
    public void createAddressBookIfMissing() {
        writeFromBookIfMissing(fileNumbers.addressBook(), this::platformAddressBookToGrpc);
    }

    @Override
    public void createNodeDetailsIfMissing() {
        writeFromBookIfMissing(fileNumbers.nodeDetails(), this::platformAddressBookToGrpc);
    }

    @Override
    public void updateStakeDetails() {
        final var book = bookSupplier.get();
        final Map<Long, Long> stakes = new HashMap<>();
        for (int i = 0, n = book.getSize(); i < n; i++) {
            final var address = book.getAddress(i);
            stakes.put(address.getId(), address.getStake());
        }

        final var detailsFid = fileNumbers.toFid(fileNumbers.nodeDetails());
        final var extant = hfs.getData().get(detailsFid);
        try {
            final var oldBook = NodeAddressBook.parseFrom(extant);
            final var newBuilder = oldBook.toBuilder();
            for (int i = 0, n = oldBook.getNodeAddressCount(); i < n; i++) {
                final var entry = oldBook.getNodeAddress(i);
                final var nodeId = entry.getNodeId();
                final var stake = stakes.getOrDefault(nodeId, 0L);
                newBuilder.setNodeAddress(i, entry.toBuilder().setStake(stake).build());
                log.info("Updated node{} stake to {}", nodeId, stake);
            }
            final var replacement = newBuilder.build();
            hfs.getData().put(detailsFid, replacement.toByteArray());
        } catch (Exception e) {
            log.error("Existing address book was missing or corrupt", e);
        }
    }

    @Override
    public void loadApiPermissions() {
        loadConfigWithJutilPropsFallback(
                fileNumbers.apiPermissions(),
                PERMISSIONS_SYS_FILE_NAME,
                BOOTSTRAP_HAPI_PERMISSIONS_PATH,
                permsSysFileDefaultResource,
                callbacks.permissionsCb());
    }

    @Override
    public void loadApplicationProperties() {
        loadConfigWithJutilPropsFallback(
                fileNumbers.applicationProperties(),
                PROPERTIES_SYS_FILE_NAME,
                BOOTSTRAP_NETWORK_PROPERTIES_PATH,
                propsSysFileDefaultResource,
                callbacks.propertiesCb());
    }

    @Override
    public void loadExchangeRates() {
        loadProtoWithSupplierFallback(
                fileNumbers.exchangeRates(),
                EXCHANGE_RATES_SYS_FILE_NAME,
                callbacks.exchangeRatesCb(),
                ExchangeRateSet::parseFrom,
                () -> defaultRates().toByteArray());
    }

    @Override
    public void loadFeeSchedules() {
        loadProtoWithSupplierFallback(
                fileNumbers.feeSchedules(),
                FEE_SCHEDULES_SYS_FILE_NAME,
                callbacks.feeSchedulesCb(),
                CurrentAndNextFeeSchedule::parseFrom,
                () -> defaultSchedules().toByteArray());
    }

    @Override
    public void loadThrottleDefinitions() {
        loadProtoWithSupplierFallback(
                fileNumbers.throttleDefinitions(),
                THROTTLE_DEFINITIONS_SYS_FILE_NAME,
                callbacks.throttlesCb(),
                ThrottleDefinitions::parseFrom,
                () -> defaultThrottles().toByteArray());
    }

    @Override
    public void setObservableFilesLoaded() {
        filesLoaded = true;
    }

    @Override
    public void setObservableFilesNotLoaded() {
        filesLoaded = false;
    }

    @Override
    public boolean areObservableFilesLoaded() {
        return filesLoaded;
    }

    @Override
    public void createUpdateFilesIfMissing() {
        final var firstUpdateNum = fileNumbers.firstSoftwareUpdateFile();
        final var lastUpdateNum = fileNumbers.lastSoftwareUpdateFile();
        final var specialFiles = hfs.specialFiles();
        for (var updateNum = firstUpdateNum; updateNum <= lastUpdateNum; updateNum++) {
            final var disFid = fileNumbers.toFid(updateNum);
            if (!hfs.exists(disFid)) {
                materialize(disFid, systemFileInfo(), new byte[0]);
            } else if (!specialFiles.contains(disFid)) {
                /* This can be the case for file 0.0.150, whose metadata had
                 * been created for the legacy MerkleDiskFs. But whatever its
                 * contents were doesn't matter now. Just make sure it exists
                 * in the MerkleSpecialFiles! */
                specialFiles.update(disFid, new byte[0]);
            }
        }
    }

    @FunctionalInterface
    private interface BootstrapLoader {
        byte[] get() throws Exception;
    }

    @FunctionalInterface
    private interface GrpcParser<T> {
        T parseFrom(byte[] data) throws InvalidProtocolBufferException;
    }

    @FunctionalInterface
    private interface ThrowingStreamProvider {
        InputStream get() throws IOException;
    }

    private <T> T loadFrom(final FileID disFid, final String resource, final GrpcParser<T> parser) {
        final byte[] contents = hfs.cat(disFid);
        try {
            return parser.parseFrom(hfs.cat(disFid));
        } catch (final InvalidProtocolBufferException e) {
            log.error(
                    "Corrupt {} in saved state ({}), unable to continue!",
                    resource,
                    escapeBytes(contents));
            throw new IllegalStateException(e);
        }
    }

    private void bootstrapInto(
            final FileID disFid, final String resource, final BootstrapLoader loader) {
        byte[] rawProps;
        try {
            rawProps = loader.get();
        } catch (final Exception e) {
            log.error("Failed to read bootstrap {}, unable to continue!", resource, e);
            throw new IllegalStateException(e);
        }
        materialize(disFid, systemFileInfo(), rawProps);
    }

    private void materialize(final FileID fid, final HFileMeta info, final byte[] contents) {
        hfs.getMetadata().put(fid, info);
        if (isUpdateFile(fid.getFileNum())) {
            hfs.specialFiles().update(fid, contents);
        } else {
            hfs.getData().put(fid, contents);
        }
    }

    private boolean isUpdateFile(long num) {
        return num >= fileNumbers.firstSoftwareUpdateFile()
                && num <= fileNumbers.lastSoftwareUpdateFile();
    }

    private <T> void loadProtoWithSupplierFallback(
            final long disNum,
            final String resource,
            final Consumer<T> onSuccess,
            final GrpcParser<T> parser,
            final BootstrapLoader fallback) {
        final var disFid = fileNumbers.toFid(disNum);
        if (!hfs.exists(disFid)) {
            bootstrapInto(disFid, resource, fallback);
        }
        final var proto = loadFrom(disFid, resource, parser);
        onSuccess.accept(proto);
    }

    private void loadConfigWithJutilPropsFallback(
            final long sysFileNum,
            final String sysFileName,
            final String externalLocProp,
            final String defaultResource,
            final Consumer<ServicesConfigurationList> onSuccess) {
        final var sysFileFid = fileNumbers.toFid(sysFileNum);
        if (!hfs.exists(sysFileFid)) {
            bootstrapInto(
                    sysFileFid,
                    sysFileName,
                    () ->
                            asSerializedConfig(
                                    sysFileName,
                                    properties.getStringProperty(externalLocProp),
                                    defaultResource,
                                    errorLogIfAnyForFailureToLoad(sysFileName)));
        }
        final var config = loadFrom(sysFileFid, sysFileName, ServicesConfigurationList::parseFrom);
        onSuccess.accept(config);
    }

    private String errorLogIfAnyForFailureToLoad(final String sysFileName) {
        return PERMISSIONS_SYS_FILE_NAME.equals(sysFileName)
                ? "Could not bootstrap permissions, only superusers will be able to perform HAPI"
                        + " operations!"
                : "Could not bootstrap properties, likely benign but resources should be"
                        + " double-checked!";
    }

    private byte[] asSerializedConfig(
            final String sysFileName,
            final String externalPropsLoc,
            final String defaultResource,
            final String errorLog) {
        final var externalSrcMsg =
                String.format("Bootstrapping %s from '%s':", sysFileName, externalPropsLoc);
        final var externalConfig =
                configBytesFrom(
                        () -> Files.newInputStream(Paths.get(externalPropsLoc)), externalSrcMsg);
        if (null != externalConfig) {
            return externalConfig;
        }

        final var resourceSrcMsg =
                String.format("Bootstrapping %s from resource '%s':", sysFileName, defaultResource);
        final var defaultConfig =
                configBytesFrom(
                        () -> {
                            final var in =
                                    HfsSystemFilesManager.class
                                            .getClassLoader()
                                            .getResourceAsStream(defaultResource);
                            if (null == in) {
                                throw new IOException(
                                        "Could not load resource '" + defaultResource + "'");
                            }
                            return in;
                        },
                        resourceSrcMsg);
        if (null != defaultConfig) {
            return defaultConfig;
        }

        log.error(errorLog);
        return ServicesConfigurationList.getDefaultInstance().toByteArray();
    }

    private @Nullable byte[] configBytesFrom(
            final ThrowingStreamProvider inProvider, final String baseMsg) {
        try (final var in = inProvider.get()) {
            final var jutilProps = new Properties();
            jutilProps.load(in);
            final var config = ServicesConfigurationList.newBuilder();
            final var sb = new StringBuilder(baseMsg);
            mapOrderedJutilProps(jutilProps, sb, config);
            log.info(sb.toString());
            return config.build().toByteArray();
        } catch (final IOException ignore) {
            return null;
        }
    }

    static void mapOrderedJutilProps(
            final Properties jutilProps,
            final StringBuilder intoSb,
            final ServicesConfigurationList.Builder config) {
        jutilProps.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(
                        entry -> {
                            intoSb.append(
                                    String.format("%n  %s=%s", entry.getKey(), entry.getValue()));
                            config.addNameValue(
                                    Setting.newBuilder()
                                            .setName(String.valueOf(entry.getKey()))
                                            .setValue(String.valueOf(entry.getValue())));
                        });
    }

    private HFileMeta systemFileInfo() {
        return new HFileMeta(
                false,
                new JKeyList(List.of(masterKey())),
                properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY));
    }

    private void writeFromBookIfMissing(final long disNum, final Supplier<byte[]> scribe) {
        final var disFid = fileNumbers.toFid(disNum);
        if (!hfs.exists(disFid)) {
            materialize(disFid, systemFileInfo(), scribe.get());
        }
    }

    private byte[] platformAddressBookToGrpc() {
        final var basics = com.hederahashgraph.api.proto.java.NodeAddressBook.newBuilder();
        final var currentBook = bookSupplier.get();
        LongStream.range(0, currentBook.getSize())
                .mapToObj(currentBook::getAddress)
                .map(address -> basicBioEntryFrom(address).build())
                .forEach(basics::addNodeAddress);
        return basics.build().toByteArray();
    }

    static NodeAddress.Builder basicBioEntryFrom(final Address address) {
        final var builder =
                NodeAddress.newBuilder()
                        .setIpAddress(
                                ByteString.copyFromUtf8(ipString(address.getAddressExternalIpv4())))
                        .setRSAPubKey(CommonUtils.hex(address.getSigPublicKey().getEncoded()))
                        .setNodeId(address.getId())
                        .setStake(address.getStake())
                        .setMemo(ByteString.copyFromUtf8(address.getMemo()));
        final var serviceEndpoint =
                ServiceEndpoint.newBuilder()
                        .setIpAddressV4(ByteString.copyFrom(address.getAddressExternalIpv4()))
                        .setPort(address.getPortExternalIpv4());
        builder.addServiceEndpoint(serviceEndpoint);
        try {
            builder.setNodeAccountId(parseAccount(address.getMemo()));
        } catch (final IllegalArgumentException e) {
            log.warn(
                    "Address for node {} had memo {}, not a parseable account!",
                    address.getId(),
                    address.getMemo());
        }
        return builder;
    }

    private CurrentAndNextFeeSchedule defaultSchedules() throws Exception {
        final var resource = properties.getStringProperty(BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE);
        final var in = HfsSystemFilesManager.class.getClassLoader().getResourceAsStream(resource);
        return loadFeeScheduleFromStream(in);
    }

    private ThrottleDefinitions defaultThrottles() throws Exception {
        final var resource = properties.getStringProperty(BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE);
        try (final var in =
                HfsSystemFilesManager.class.getClassLoader().getResourceAsStream(resource)) {
            return ThrottlesJsonToProtoSerde.loadProtoDefs(in);
        }
    }

    private ExchangeRateSet defaultRates() {
        return ExchangeRateSet.newBuilder()
                .setCurrentRate(
                        rateFrom(
                                properties.getIntProperty(BOOTSTRAP_RATES_CURRENT_CENT_EQUIV),
                                properties.getIntProperty(BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV),
                                properties.getLongProperty(BOOTSTRAP_RATES_CURRENT_EXPIRY)))
                .setNextRate(
                        rateFrom(
                                properties.getIntProperty(BOOTSTRAP_RATES_NEXT_CENT_EQUIV),
                                properties.getIntProperty(BOOTSTRAP_RATES_NEXT_HBAR_EQUIV),
                                properties.getLongProperty(BOOTSTRAP_RATES_NEXT_EXPIRY)))
                .build();
    }

    private ExchangeRate rateFrom(final int centEquiv, final int hbarEquiv, final long expiry) {
        return ExchangeRate.newBuilder()
                .setCentEquiv(centEquiv)
                .setHbarEquiv(hbarEquiv)
                .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expiry))
                .build();
    }

    private JKey masterKey() {
        if (systemKey == null) {
            systemKey = keySupplier.get();
        }
        return systemKey;
    }

    void setPermsSysFileDefaultResource(final String permsSysFileDefaultResource) {
        this.permsSysFileDefaultResource = permsSysFileDefaultResource;
    }

    public void setPropsSysFileDefaultResource(final String propsSysFileDefaultResource) {
        this.propsSysFileDefaultResource = propsSysFileDefaultResource;
    }
}
