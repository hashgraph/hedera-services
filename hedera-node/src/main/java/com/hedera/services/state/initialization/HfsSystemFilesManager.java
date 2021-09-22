package com.hedera.services.state.initialization;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.SysFileCallbacks;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static com.google.protobuf.TextFormat.escapeBytes;
import static com.hedera.services.sysfiles.serdes.FeesJsonToProtoSerde.loadFeeScheduleFromStream;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static com.swirlds.common.Address.ipString;

@Singleton
public class HfsSystemFilesManager implements SystemFilesManager {
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
	private final Supplier<JKey> keySupplier;
	private final Supplier<AddressBook> bookSupplier;
	private final SysFileCallbacks callbacks;

	@Inject
	public HfsSystemFilesManager(
			Supplier<AddressBook> bookSupplier,
			FileNumbers fileNumbers,
			@CompositeProps PropertySource properties,
			TieredHederaFs hfs,
			Supplier<JKey> keySupplier,
			SysFileCallbacks callbacks
	) {
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
	public void loadApiPermissions() {
		loadConfigWithJutilPropsFallback(
				fileNumbers.apiPermissions(),
				PERMISSIONS_SYS_FILE_NAME,
				"bootstrap.hapiPermissions.path",
				permsSysFileDefaultResource,
				callbacks.permissionsCb());
	}

	@Override
	public void loadApplicationProperties() {
		loadConfigWithJutilPropsFallback(
				fileNumbers.applicationProperties(),
				PROPERTIES_SYS_FILE_NAME,
				"bootstrap.networkProperties.path",
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
		for (var updateNum = firstUpdateNum; updateNum <= lastUpdateNum; updateNum++) {
			var disFid = fileNumbers.toFid(updateNum);
			if (!hfs.exists(disFid)) {
				materialize(disFid, systemFileInfo(), new byte[0]);
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

	private <T> T loadFrom(FileID disFid, String resource, GrpcParser<T> parser) {
		final byte[] contents = hfs.cat(disFid);
		try {
			return parser.parseFrom(hfs.cat(disFid));
		} catch (InvalidProtocolBufferException e) {
			log.error("Corrupt {} in saved state ({}), unable to continue!", resource, escapeBytes(contents));
			throw new IllegalStateException(e);
		}
	}

	private void bootstrapInto(
			FileID disFid,
			String resource,
			BootstrapLoader loader
	) {
		byte[] rawProps;
		try {
			rawProps = loader.get();
		} catch (Exception e) {
			log.error("Failed to read bootstrap {}, unable to continue!", resource, e);
			throw new IllegalStateException(e);
		}
		materialize(disFid, systemFileInfo(), rawProps);
	}

	private void materialize(FileID fid, HFileMeta info, byte[] contents) {
		hfs.getMetadata().put(fid, info);
		if (isUpdateFile(fid.getFileNum())) {
			hfs.specialFiles().update(fid, contents);
		} else {
			hfs.getData().put(fid, contents);
		}
	}

	private boolean isUpdateFile(long num) {
		return num >= fileNumbers.firstSoftwareUpdateFile() && num <= fileNumbers.lastSoftwareUpdateFile();
	}

	private <T> void loadProtoWithSupplierFallback(
			long disNum,
			String resource,
			Consumer<T> onSuccess,
			GrpcParser<T> parser,
			BootstrapLoader fallback
	) {
		var disFid = fileNumbers.toFid(disNum);
		if (!hfs.exists(disFid)) {
			bootstrapInto(disFid, resource, fallback);
		}
		var proto = loadFrom(disFid, resource, parser);
		onSuccess.accept(proto);
	}

	private void loadConfigWithJutilPropsFallback(
			long sysFileNum,
			String sysFileName,
			String externalLocProp,
			String defaultResource,
			Consumer<ServicesConfigurationList> onSuccess
	) {
		final var sysFileFid = fileNumbers.toFid(sysFileNum);
		if (!hfs.exists(sysFileFid)) {
			bootstrapInto(
					sysFileFid,
					sysFileName,
					() -> asSerializedConfig(
							sysFileName,
							properties.getStringProperty(externalLocProp),
							defaultResource,
							errorLogIfAnyForFailureToLoad(sysFileName)));
		}
		final var config = loadFrom(sysFileFid, sysFileName, ServicesConfigurationList::parseFrom);
		onSuccess.accept(config);
	}

	private String errorLogIfAnyForFailureToLoad(String sysFileName) {
		return PERMISSIONS_SYS_FILE_NAME.equals(sysFileName)
				? "Could not bootstrap permissions, only superusers will be able to perform HAPI operations!"
				: "Could not bootstrap properties, likely benign but resources should be double-checked!";
	}

	private byte[] asSerializedConfig(
			String sysFileName,
			String externalPropsLoc,
			String defaultResource,
			String errorLog
	) {
		final var externalSrcMsg = String.format("Bootstrapping %s from '%s':", sysFileName, externalPropsLoc);
		final var resourceSrcMsg = String.format("Bootstrapping %s from resource '%s':", sysFileName, defaultResource);

		return configBytesFrom(
				() -> Files.newInputStream(Paths.get(externalPropsLoc)),
				externalSrcMsg
		)
				.or(() ->
						configBytesFrom(
								() -> {
									var in = HfsSystemFilesManager.class.getClassLoader()
											.getResourceAsStream(defaultResource);
									if (in == null) {
										throw new IOException("Could not load resource '" + defaultResource + "'");
									}
									return in;
								},
								resourceSrcMsg
						)
				).or(() -> {
							log.error(errorLog);
							return Optional.of(ServicesConfigurationList.getDefaultInstance().toByteArray());
						}
				).get();
	}

	private Optional<byte[]> configBytesFrom(ThrowingStreamProvider inProvider, String baseMsg) {
		try (var in = inProvider.get()) {
			final var jutilProps = new Properties();
			jutilProps.load(in);
			final var config = ServicesConfigurationList.newBuilder();
			final var sb = new StringBuilder(baseMsg);
			mapOrderedJutilProps(jutilProps, sb, config);
			log.info(sb.toString());
			return Optional.of(config.build().toByteArray());
		} catch (IOException ignore) {
			return Optional.empty();
		}
	}

	static void mapOrderedJutilProps(Properties jutilProps, StringBuilder intoSb,
			ServicesConfigurationList.Builder config) {
		jutilProps.entrySet()
				.stream()
				.sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
				.peek(entry -> intoSb.append(String.format(
						"\n  %s=%s",
						String.valueOf(entry.getKey()),
						String.valueOf(entry.getValue()))))
				.forEach(entry ->
						config.addNameValue(Setting.newBuilder()
								.setName(String.valueOf(entry.getKey()))
								.setValue(String.valueOf(entry.getValue()))));
	}

	private HFileMeta systemFileInfo() {
		return new HFileMeta(
				false,
				new JKeyList(List.of(masterKey())),
				properties.getLongProperty("bootstrap.system.entityExpiry"));
	}

	private void writeFromBookIfMissing(long disNum, Supplier<byte[]> scribe) {
		var disFid = fileNumbers.toFid(disNum);
		if (!hfs.exists(disFid)) {
			materialize(disFid, systemFileInfo(), scribe.get());
		}
	}

	private byte[] platformAddressBookToGrpc() {
		var basics = com.hederahashgraph.api.proto.java.NodeAddressBook.newBuilder();
		final var currentBook = bookSupplier.get();
		LongStream.range(0, currentBook.getSize())
				.mapToObj(currentBook::getAddress)
				.map(address -> basicBioEntryFrom(address).build())
				.forEach(basics::addNodeAddress);
		return basics.build().toByteArray();
	}

	private NodeAddress.Builder basicBioEntryFrom(Address address) {
		var builder = NodeAddress.newBuilder()
				.setIpAddress(ByteString.copyFromUtf8(ipString(address.getAddressExternalIpv4())))
				.setRSAPubKey(CommonUtils.hex(address.getSigPublicKey().getEncoded()))
				.setNodeId(address.getId())
				.setStake(address.getStake())
				.setMemo(ByteString.copyFromUtf8(address.getMemo()));
		var serviceEndpoint = ServiceEndpoint.newBuilder()
				.setIpAddressV4(ByteString.copyFrom(address.getAddressExternalIpv4()))
				.setPort(address.getPortExternalIpv4());
		builder.addServiceEndpoint(serviceEndpoint);
		try {
			builder.setNodeAccountId(parseAccount(address.getMemo()));
		} catch (Exception e) {
			log.warn("Address for node {} had memo {}, not a parseable account!", address.getId(), address.getMemo());
		}
		return builder;
	}

	private CurrentAndNextFeeSchedule defaultSchedules() throws Exception {
		var resource = properties.getStringProperty("bootstrap.feeSchedulesJson.resource");
		final var in = HfsSystemFilesManager.class.getClassLoader().getResourceAsStream(resource);
		return loadFeeScheduleFromStream(in);
	}

	private ThrottleDefinitions defaultThrottles() throws Exception {
		var resource = properties.getStringProperty("bootstrap.throttleDefsJson.resource");
		try (InputStream in = HfsSystemFilesManager.class.getClassLoader().getResourceAsStream(resource)) {
			return ThrottlesJsonToProtoSerde.loadProtoDefs(in);
		}
	}

	private ExchangeRateSet defaultRates() {
		return ExchangeRateSet.newBuilder()
				.setCurrentRate(
						rateFrom(
								properties.getIntProperty("bootstrap.rates.currentCentEquiv"),
								properties.getIntProperty("bootstrap.rates.currentHbarEquiv"),
								properties.getLongProperty("bootstrap.rates.currentExpiry")))
				.setNextRate(
						rateFrom(
								properties.getIntProperty("bootstrap.rates.nextCentEquiv"),
								properties.getIntProperty("bootstrap.rates.nextHbarEquiv"),
								properties.getLongProperty("bootstrap.rates.nextExpiry")))
				.build();
	}

	private ExchangeRate rateFrom(int centEquiv, int hbarEquiv, long expiry) {
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

	void setPermsSysFileDefaultResource(String permsSysFileDefaultResource) {
		this.permsSysFileDefaultResource = permsSysFileDefaultResource;
	}

	public void setPropsSysFileDefaultResource(String propsSysFileDefaultResource) {
		this.propsSysFileDefaultResource = propsSysFileDefaultResource;
	}
}
