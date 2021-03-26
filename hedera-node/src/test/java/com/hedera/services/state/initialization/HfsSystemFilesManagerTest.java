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

import static com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde.loadProtoDefs;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.files.interceptors.MockFileNumbers;
import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import static org.mockito.BDDMockito.*;

class HfsSystemFilesManagerTest {
	String R4_FEE_SCHEDULE_REPR_PATH = "src/test/resources/testfiles/r4FeeSchedule.bin";
	String bootstrapJutilPropsLoc = "src/test/resources/bootstrap.properties";
	String bootstrapJutilPermsLoc = "src/test/resources/permission-bootstrap.properties";

	byte[] nonsense = "NONSENSE".getBytes();
	ServicesConfigurationList fromState = ServicesConfigurationList.newBuilder()
			.addNameValue(Setting.newBuilder()
					.setName("stateName")
					.setValue("stateValue"))
			.build();
	ServicesConfigurationList fromBootstrapFile = ServicesConfigurationList.newBuilder()
			.addNameValue(Setting.newBuilder()
					.setName("bootstrapNameA")
					.setValue("bootstrapValueA"))
			.addNameValue(Setting.newBuilder()
					.setName("bootstrapNameB")
					.setValue("bootstrapValueB"))
			.build();
	FileID bookId = expectedFid(101);
	FileID detailsId = expectedFid(102);
	FileID appPropsId = expectedFid(121);
	FileID apiPermsId = expectedFid(122);
	FileID throttlesId = expectedFid(123);
	FileID schedulesId = expectedFid(111);
	FileID ratesId = expectedFid(112);

	long expiry = 1_234_567_890L;
	long nextExpiry = 2_234_567_890L;
	int curCentEquiv = 1;
	int curHbarEquiv = 12;
	int nxtCentEquiv = 2;
	int nxtHbarEquiv = 31;
	Map<FileID, byte[]> data;
	Map<FileID, HFileMeta> metadata;
	JKey masterKey;
	byte[] aIpv4, bIpv4;
	byte[] aKeyEncoding = "not-really-A-key".getBytes();
	byte[] bKeyEncoding = "not-really-B-key".getBytes();
	String memoA, memoB;
	Address addressA, addressB;
	PublicKey keyA, keyB;
	AddressBook currentBook;
	HFileMeta expectedInfo;
	TieredHederaFs hfs;
	MerkleDiskFs diskFs;
	MockFileNumbers fileNumbers;
	PropertySource properties;
	Consumer<ServicesConfigurationList> propertiesCb;
	Consumer<ServicesConfigurationList> permissionsCb;
	Consumer<ExchangeRateSet> ratesCb;
	Consumer<ThrottleDefinitions> throttlesCb;
	Consumer<CurrentAndNextFeeSchedule> schedulesCb;

	HfsSystemFilesManager subject;

	@BeforeEach
	private void setup() throws Exception {
		masterKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asJKey();
		expectedInfo = new HFileMeta(
				false,
				JKey.mapKey(Key.newBuilder()
								.setKeyList(KeyList.newBuilder()
										.addKeys(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())).build()),
				expiry);

		keyA = mock(PublicKey.class);
		given(keyA.getEncoded()).willReturn(aKeyEncoding);
		addressA = mock(Address.class);
		aIpv4 = new byte[] { (byte)1, (byte)2, (byte)3, (byte)4 };
		memoA = "A new memo that is not the node account ID.";
		given(addressA.getId()).willReturn(111L);
		given(addressA.getMemo()).willReturn(memoA);
		given(addressA.getAddressExternalIpv4()).willReturn(aIpv4);
		given(addressA.getSigPublicKey()).willReturn(keyA);

		keyB = mock(PublicKey.class);
		given(keyB.getEncoded()).willReturn(bKeyEncoding);
		addressB = mock(Address.class);
		bIpv4 = new byte[] { (byte)2, (byte)3, (byte)4, (byte)5 };
		memoB = "0.0.3";
		given(addressB.getId()).willReturn(222L);
		given(addressB.getMemo()).willReturn(memoB);
		given(addressB.getAddressExternalIpv4()).willReturn(bIpv4);
		given(addressB.getSigPublicKey()).willReturn(keyB);

		currentBook = mock(AddressBook.class);
		given(currentBook.getAddress(0L)).willReturn(addressA);
		given(currentBook.getAddress(1L)).willReturn(addressB);
		given(currentBook.getSize()).willReturn(2);

		data = mock(Map.class);
		metadata = mock(Map.class);
		hfs = mock(TieredHederaFs.class);
		diskFs = mock(MerkleDiskFs.class);
		given(hfs.getData()).willReturn(data);
		given(hfs.getMetadata()).willReturn(metadata);
		given(hfs.diskFs()).willReturn(diskFs);
		fileNumbers = new MockFileNumbers();
		fileNumbers.setShard(1L);
		fileNumbers.setRealm(22L);
		given(diskFs.contains(fileNumbers.toFid(111))).willReturn(false);

		properties = mock(PropertySource.class);
		given(properties.getStringProperty("bootstrap.hapiPermissions.path"))
				.willReturn(bootstrapJutilPermsLoc);
		given(properties.getStringProperty("bootstrap.networkProperties.path"))
				.willReturn(bootstrapJutilPropsLoc);
		given(properties.getLongProperty("bootstrap.system.entityExpiry"))
				.willReturn(expiry);
		given(properties.getIntProperty("bootstrap.rates.currentHbarEquiv"))
				.willReturn(curHbarEquiv);
		given(properties.getIntProperty("bootstrap.rates.currentCentEquiv"))
				.willReturn(curCentEquiv);
		given(properties.getLongProperty("bootstrap.rates.currentExpiry"))
				.willReturn(expiry);
		given(properties.getIntProperty("bootstrap.rates.nextHbarEquiv"))
				.willReturn(nxtHbarEquiv);
		given(properties.getIntProperty("bootstrap.rates.nextCentEquiv"))
				.willReturn(nxtCentEquiv);
		given(properties.getLongProperty("bootstrap.rates.nextExpiry"))
				.willReturn(nextExpiry);
		given(properties.getStringProperty("bootstrap.feeSchedulesJson.resource"))
				.willReturn("R4FeeSchedule.json");
		given(properties.getStringProperty("bootstrap.throttleDefsJson.resource"))
				.willReturn("bootstrap/throttles.json");

		ratesCb = mock(Consumer.class);
		schedulesCb = mock(Consumer.class);
		propertiesCb = mock(Consumer.class);
		permissionsCb = mock(Consumer.class);
		throttlesCb = mock(Consumer.class);

		subject = new HfsSystemFilesManager(
				currentBook,
				fileNumbers,
				properties,
				hfs,
				() -> masterKey,
				ratesCb,
				throttlesCb,
				schedulesCb,
				propertiesCb,
				permissionsCb);
	}

	@Test
	public void loadsEverything() {
		// setup:
		SystemFilesManager sub = mock(SystemFilesManager.class);

		willCallRealMethod().given(sub).loadAllSystemFiles();

		// when:
		sub.loadAllSystemFiles();

		// then:
		verify(sub).loadApplicationProperties();
		verify(sub).loadApiPermissions();
		verify(sub).loadFeeSchedules();
		verify(sub).loadExchangeRates();
		verify(sub).loadThrottleDefinitions();
		verify(sub).setFilesLoaded();
	}

	@Test
	public void tracksFileLoading() {
		// expect:
		assertFalse(subject.areFilesLoaded());

		// when:
		subject.setFilesLoaded();

		// then:
		assertTrue(subject.areFilesLoaded());
	}

	@Test
	public void doesntCreateAddressBookIfPresent() {
		given(hfs.exists(bookId)).willReturn(true);

		// when:
		subject.createAddressBookIfMissing();

		// then:
		verify(hfs).exists(bookId);
		verify(data, never()).put(any(), any());
		verify(metadata, never()).put(
				argThat(bookId::equals),
				argThat(info -> expectedInfo.toString().equals(info.toString())));
	}

	@Test
	public void createsAddressBookIfMissing() {
		// setup:
		var expectedBook = legacyBookConstruction(currentBook);

		given(hfs.exists(bookId)).willReturn(false);

		// when:
		subject.createAddressBookIfMissing();

		// then:
		verify(hfs).exists(bookId);
		verify(data).put(
				argThat(bookId::equals),
				argThat((byte[] bytes) -> Arrays.equals(expectedBook.toByteArray(), bytes)));
		verify(metadata).put(
				argThat(bookId::equals),
				argThat(info -> expectedInfo.toString().equals(info.toString())));
	}

	@Test
	public void createsNodeDetailsIfMissing() {
		// setup:
		var expectedDetails = legacyNodeDetailsConstruction(currentBook);

		given(hfs.exists(detailsId)).willReturn(false);

		// when:
		subject.createNodeDetailsIfMissing();

		// then:
		verify(hfs).exists(detailsId);
		verify(data).put(
				argThat(detailsId::equals),
				argThat((byte[] bytes) -> Arrays.equals(expectedDetails.toByteArray(), bytes)));
	}

	@Test
	public void createsEmptyUpdateFeatureFile() {
		FileID file150 = fileNumbers.toFid(fileNumbers.softwareUpdateZip());

		// setup:
		given(hfs.exists(file150)).willReturn(false);
		given(hfs.diskFs()).willReturn(diskFs);
		given(diskFs.contains(file150)).willReturn(true);

		// when:
		subject.createUpdateZipFileIfMissing();

		// then:
		verify(diskFs).put(file150, new byte[0]);
	}

	@Test
	public void loadsPropsFromHfsIfAvailable() {
		given(hfs.exists(appPropsId)).willReturn(true);
		given(hfs.cat(appPropsId)).willReturn(fromState.toByteArray());

		// when:
		subject.loadApplicationProperties();

		// then:
		verify(hfs).exists(appPropsId);
		verify(propertiesCb).accept(fromState);
	}

	@Test
	public void loadsPermsFromHfsIfAvailable() {
		given(hfs.exists(apiPermsId)).willReturn(true);
		given(hfs.cat(apiPermsId)).willReturn(fromState.toByteArray());

		// when:
		subject.loadApiPermissions();

		// then:
		verify(hfs).exists(apiPermsId);
		verify(permissionsCb).accept(fromState);
	}

	@Test
	public void loadsRatesFromHfsIfAvailable() {
		given(hfs.exists(ratesId)).willReturn(true);
		given(hfs.cat(ratesId)).willReturn(expectedDefaultRates().toByteArray());

		// when:
		subject.loadExchangeRates();

		// then:
		verify(hfs).exists(ratesId);
		verify(ratesCb).accept(expectedDefaultRates());
	}

	@Test
	public void createsRatesFromPropsIfMissing() {
		given(hfs.exists(ratesId)).willReturn(false);
		given(hfs.cat(ratesId)).willReturn(expectedDefaultRates().toByteArray());

		// when:
		subject.loadExchangeRates();

		// then:
		verify(hfs).exists(ratesId);
		verify(metadata).put(
				argThat(ratesId::equals),
				argThat(info -> expectedInfo.toString().equals(info.toString())));
		verify(data).put(
				argThat(ratesId::equals),
				argThat((byte[] bytes) -> Arrays.equals(expectedDefaultRates().toByteArray(), bytes)));
		verify(ratesCb).accept(expectedDefaultRates());
	}

	@Test
	public void loadsSchedulesFromHfsIfAvailable() throws IOException {
		// setup:
		byte[] schedules = Files.readAllBytes(Paths.get(R4_FEE_SCHEDULE_REPR_PATH));
		var proto = CurrentAndNextFeeSchedule.parseFrom(schedules);

		given(hfs.exists(schedulesId)).willReturn(true);
		given(hfs.cat(schedulesId)).willReturn(schedules);

		// when:
		subject.loadFeeSchedules();

		// then:
		verify(hfs).exists(schedulesId);
		verify(schedulesCb).accept(proto);
	}

	@Test
	public void loadsThrottlesFromHfsIfAvailable() {
		// setup:
		var proto = loadProtoDefs("bootstrap/throttles.json");
		byte[] throttleBytes = proto.toByteArray();

		given(hfs.exists(throttlesId)).willReturn(true);
		given(hfs.cat(throttlesId)).willReturn(throttleBytes);

		// when:
		subject.loadThrottleDefinitions();

		// then:
		verify(hfs).exists(throttlesId);
		verify(throttlesCb).accept(proto);
	}

	@Test
	public void createsThrottlesFromResourceIfMissing() throws IOException {
		// setup:
		var proto = loadProtoDefs("bootstrap/throttles.json");
		byte[] throttleBytes = proto.toByteArray();

		given(hfs.exists(throttlesId)).willReturn(false);
		given(hfs.cat(throttlesId)).willReturn(throttleBytes);

		// when:
		subject.loadThrottleDefinitions();

		// then:
		verify(hfs).exists(throttlesId);
		verify(metadata).put(
				argThat(throttlesId::equals),
				argThat(info -> expectedInfo.toString().equals(info.toString())));
		verify(data).put(
				argThat(throttlesId::equals),
				argThat((byte[] bytes) -> Arrays.equals(throttleBytes, bytes)));
	}

	@Test
	public void createsSchedulesFromResourcesIfMissing() throws IOException {
		// setup:
		byte[] schedules = Files.readAllBytes(Paths.get(R4_FEE_SCHEDULE_REPR_PATH));

		given(hfs.exists(schedulesId)).willReturn(false);
		given(hfs.cat(schedulesId)).willReturn(schedules);

		// when:
		subject.loadFeeSchedules();

		// then:
		verify(hfs).exists(schedulesId);
		verify(metadata).put(
				argThat(schedulesId::equals),
				argThat(info -> expectedInfo.toString().equals(info.toString())));
		verify(data).put(
				argThat(schedulesId::equals),
				argThat((byte[] bytes) -> Arrays.equals(schedules, bytes)));
	}

	@Test
	public void bootstrapsPropsFromDiskOnNetworkStartup() throws IOException {
		// setup:
		var jutilProps = new Properties();
		fromBootstrapFile.getNameValueList().forEach(setting ->
						jutilProps.put(setting.getName(), setting.getValue()));
		jutilProps.store(Files.newOutputStream(Paths.get(bootstrapJutilPropsLoc)), "Testing 123");

		given(hfs.exists(appPropsId)).willReturn(false);
		given(hfs.cat(appPropsId)).willReturn(fromBootstrapFile.toByteArray());

		// when:
		subject.loadApplicationProperties();

		// then:
		verify(hfs).exists(appPropsId);
		// and:
		verify(metadata).put(
				argThat(appPropsId::equals),
				argThat(info -> expectedInfo.toString().equals(info.toString())));
		verify(data).put(
				argThat(appPropsId::equals),
				argThat((byte[] bytes) -> Arrays.equals(fromBootstrapFile.toByteArray(), bytes)));
		// and:
		verify(propertiesCb).accept(fromBootstrapFile);

		// cleanup:
		Files.deleteIfExists(Paths.get(bootstrapJutilPropsLoc));
	}

	@Test
	public void throwsIseOnMissingBootstrapProps() throws IOException {
		// setup:
		Files.deleteIfExists(Paths.get(bootstrapJutilPropsLoc));

		given(hfs.exists(appPropsId)).willReturn(false);

		// expect:
		assertThrows(IllegalStateException.class, subject::loadApplicationProperties);
	}

	@Test
	public void throwsIseOnNonsenseStateProperties() {
		given(hfs.exists(appPropsId)).willReturn(true);
		given(hfs.cat(appPropsId)).willReturn(nonsense);

		// expect:
		assertThrows(IllegalStateException.class, subject::loadApplicationProperties);
	}

	private FileID expectedFid(long num) {
		return FileID.newBuilder()
				.setShardNum(1L)
				.setRealmNum(22L)
				.setFileNum(num)
				.build();
	}

	private NodeAddressBook legacyBookConstruction(AddressBook fromBook) {
		NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
		for (int i = 0; i < fromBook.getSize(); i++) {
			var address = fromBook.getAddress(i);
			byte[] nodeIP = address.getAddressExternalIpv4();
			String nodeIPStr = Address.ipString(nodeIP);
			String memo = address.getMemo();
			NodeAddress.Builder nodeAddress = NodeAddress.newBuilder()
					.setIpAddress(ByteString.copyFromUtf8(nodeIPStr))
					.setMemo(ByteString.copyFromUtf8(memo))
					.setNodeId(address.getId());
			setNodeAccountIfAvail(address, nodeAddress);
			builder.addNodeAddress(nodeAddress);
		}
		return builder.build();
	}

	private NodeAddressBook legacyNodeDetailsConstruction(AddressBook fromBook) {
		NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
		for (int i = 0; i < fromBook.getSize(); i++) {
			var address = fromBook.getAddress(i);
			PublicKey publicKey = address.getSigPublicKey();
			String memo = address.getMemo();
			NodeAddress.Builder nodeAddress = NodeAddress.newBuilder()
					.setMemo(ByteString.copyFromUtf8(memo))
					.setRSAPubKey(MiscUtils.commonsBytesToHex(publicKey.getEncoded()))
					.setNodeId(address.getId());
			setNodeAccountIfAvail(address, nodeAddress);
			builder.addNodeAddress(nodeAddress);
		}
		return builder.build();
	}

	private void setNodeAccountIfAvail(Address entry, NodeAddress.Builder builder) {
		try {
			var id = EntityIdUtils.accountParsedFromString(entry.getMemo());
			builder.setNodeAccountId(id);
		} catch (Exception ignore) { }
	}

	private ExchangeRateSet expectedDefaultRates() {
		return ExchangeRateSet.newBuilder()
				.setCurrentRate(fromRatio(curCentEquiv, curHbarEquiv, expiry))
				.setNextRate(fromRatio(nxtCentEquiv, nxtHbarEquiv, nextExpiry))
				.build();
	}

	private ExchangeRate fromRatio(int cent, int hbar, long expiry) {
		return ExchangeRate.newBuilder()
				.setCentEquiv(cent)
				.setHbarEquiv(hbar)
				.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expiry))
				.build();
	}
}
