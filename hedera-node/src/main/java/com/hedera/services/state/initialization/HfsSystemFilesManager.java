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
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.SysFileCallbacks;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static com.hedera.services.fees.bootstrap.JsonToProtoSerde.loadFeeScheduleFromJson;
import static com.swirlds.common.Address.ipString;

public class HfsSystemFilesManager implements SystemFilesManager {
	private static final Logger log = LogManager.getLogger(HfsSystemFilesManager.class);
	private static final String PROPERTIES_TAG = "properties";
	private static final String PERMISSIONS_TAG = "API permissions";
	private static final String EXCHANGE_RATES_TAG = "exchange rates";
	private static final String FEE_SCHEDULES_TAG = "fee schedules";
	private static final String THROTTLE_DEFINITIONS_TAG = "throttle definitions";

	private JKey systemKey;
	private boolean filesLoaded = false;
	private final AddressBook currentBook;
	private final FileNumbers fileNumbers;
	private final PropertySource properties;
	private final TieredHederaFs hfs;
	private final Supplier<JKey> keySupplier;
	private SysFileCallbacks callbacks;

	public HfsSystemFilesManager(
			AddressBook currentBook,
			FileNumbers fileNumbers,
			PropertySource properties,
			TieredHederaFs hfs,
			Supplier<JKey> keySupplier,
			SysFileCallbacks callbacks
	) {
		this.hfs = hfs;
		this.callbacks = callbacks;
		this.properties = properties;
		this.currentBook = currentBook;
		this.fileNumbers = fileNumbers;
		this.keySupplier = keySupplier;
	}

	@Override
	public void createAddressBookIfMissing() {
		writeFromBookIfMissing(fileNumbers.addressBook(), this::bioAndIpv4Contents);
	}

	@Override
	public void createNodeDetailsIfMissing() {
		writeFromBookIfMissing(fileNumbers.nodeDetails(), this::bioAndPubKeyContents);
	}

	@Override
	public void loadApiPermissions() {
		loadConfigWithJutilPropsFallback(
				fileNumbers.apiPermissions(),
				PERMISSIONS_TAG,
				"bootstrap.hapiPermissions.path",
				callbacks.permissionsCb());
	}

	@Override
	public void loadApplicationProperties() {
		loadConfigWithJutilPropsFallback(
				fileNumbers.applicationProperties(),
				PROPERTIES_TAG,
				"bootstrap.networkProperties.path",
				callbacks.propertiesCb());
	}

	@Override
	public void loadExchangeRates() {
		loadProtoWithSupplierFallback(
				fileNumbers.exchangeRates(),
				EXCHANGE_RATES_TAG,
				callbacks.exchangeRatesCb(),
				ExchangeRateSet::parseFrom,
				() -> defaultRates().toByteArray());
	}

	@Override
	public void loadFeeSchedules() {
		loadProtoWithSupplierFallback(
				fileNumbers.feeSchedules(),
				FEE_SCHEDULES_TAG,
				callbacks.feeSchedulesCb(),
				CurrentAndNextFeeSchedule::parseFrom,
				() -> defaultSchedules().toByteArray());
	}

	@Override
	public void loadThrottleDefinitions() {
		loadProtoWithSupplierFallback(
				fileNumbers.throttleDefinitions(),
				THROTTLE_DEFINITIONS_TAG,
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
	public void createUpdateZipFileIfMissing() {
		var disFid = fileNumbers.toFid(fileNumbers.softwareUpdateZip());
		if (!hfs.exists(disFid)) {
			materialize(disFid, systemFileInfo(), new byte[0]);
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

	private <T> T loadFrom(FileID disFid, String resource, GrpcParser<T> parser) {
		try {
			return parser.parseFrom(hfs.cat(disFid));
		} catch (InvalidProtocolBufferException e) {
			log.error("Corrupt {} in saved state, unable to continue!", resource);
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
		if (fileNumbers.softwareUpdateZip() == fid.getFileNum()) {
			hfs.diskFs().put(fid, contents);
		} else {
			hfs.getData().put(fid, contents);
		}
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
			long disNum,
			String resource,
			String jutilLocProp,
			Consumer<ServicesConfigurationList> onSuccess
	) {
		var disFid = fileNumbers.toFid(disNum);
		if (!hfs.exists(disFid)) {
			bootstrapInto(
					disFid,
					resource,
					() -> asSerializedConfig(
							resource,
							properties.getStringProperty(jutilLocProp)));
		}
		var config = loadFrom(disFid, resource, ServicesConfigurationList::parseFrom);
		onSuccess.accept(config);
	}

	private byte[] asSerializedConfig(String resource, String propsLoc) throws IOException {
		try (InputStream fin = Files.newInputStream(Paths.get(propsLoc))) {
			var jutilProps = new Properties();
			jutilProps.load(fin);
			var config = ServicesConfigurationList.newBuilder();
			var sb = new StringBuilder(String.format("Bootstrapping network %s from '%s':", resource, propsLoc));
			jutilProps.entrySet()
					.stream()
					.sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
					.peek(entry -> sb.append(String.format(
							"\n  %s=%s",
							String.valueOf(entry.getKey()),
							String.valueOf(entry.getValue()))))
					.forEach(entry ->
							config.addNameValue(Setting.newBuilder()
									.setName(String.valueOf(entry.getKey()))
									.setValue(String.valueOf(entry.getValue()))));
			log.info(sb.toString());
			return config.build().toByteArray();
		}
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

	private byte[] bioAndIpv4Contents() {
		var basics = com.hederahashgraph.api.proto.java.AddressBook.newBuilder();
		LongStream.range(0, currentBook.getSize())
				.mapToObj(currentBook::getAddress)
				.map(address ->
						basicBioEntryFrom(address)
								.setIpAddress(ByteString.copyFromUtf8(ipString(address.getAddressExternalIpv4())))
								.build())
				.forEach(basics::addNodeAddress);
		return basics.build().toByteArray();
	}

	private byte[] bioAndPubKeyContents() {
		var details = com.hederahashgraph.api.proto.java.AddressBook.newBuilder();
		LongStream.range(0, currentBook.getSize())
				.mapToObj(currentBook::getAddress)
				.map(address ->
						basicBioEntryFrom(address)
								.setRSAPubKey(MiscUtils.commonsBytesToHex(address.getSigPublicKey().getEncoded()))
								.build())
				.forEach(details::addNodeAddress);
		return details.build().toByteArray();
	}

	private NodeAddress.Builder basicBioEntryFrom(Address address) {
		var builder = NodeAddress.newBuilder()
				.setNodeId(address.getId())
				.setMemo(ByteString.copyFromUtf8(address.getMemo()));
		try {
			builder.setNodeAccountId(EntityIdUtils.accountParsedFromString(address.getMemo()));
		} catch (Exception ignore) {
			log.warn(ignore.getMessage());
		}
		return builder;
	}

	private CurrentAndNextFeeSchedule defaultSchedules() throws Exception {
		var resource = properties.getStringProperty("bootstrap.feeSchedulesJson.resource");

		return loadFeeScheduleFromJson(resource);
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
}
