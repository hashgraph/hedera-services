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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.SysFileCallbacks;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.files.interceptors.MockFileNumbers;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.sysfiles.serdes.FeesJsonToProtoSerde;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.SerdeUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.codec.DecoderException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(LogCaptureExtension.class)
class HfsSystemFilesManagerTest {
    private static final String R4_FEE_SCHEDULE_REPR_PATH =
            "src/test/resources/testfiles/r4FeeSchedule.bin";
    private static final String bootstrapJutilPropsLoc =
            "src/test/resources/bootstrap/hfsSystemFilesBootstrap.properties";
    private static final String bootstrapJutilPermsLoc =
            "src/test/resources/permission-bootstrap.properties";
    private static final String nonexistentBootstrapJutilLoc = "nowhere";

    private static final byte[] nonsense = "NONSENSE".getBytes();
    private static final ServicesConfigurationList fromState =
            ServicesConfigurationList.newBuilder()
                    .addNameValue(Setting.newBuilder().setName("stateName").setValue("stateValue"))
                    .build();
    private static final ServicesConfigurationList fromBootstrapFile =
            ServicesConfigurationList.newBuilder()
                    .addNameValue(
                            Setting.newBuilder()
                                    .setName("bootstrapNameA")
                                    .setValue("bootstrapValueA"))
                    .addNameValue(
                            Setting.newBuilder()
                                    .setName("bootstrapNameB")
                                    .setValue("bootstrapValueB"))
                    .build();
    private static final FileID bookId = expectedFid(101);
    private static final FileID detailsId = expectedFid(102);
    private static final FileID appPropsId = expectedFid(121);
    private static final FileID apiPermsId = expectedFid(122);
    private static final FileID throttlesId = expectedFid(123);
    private static final FileID schedulesId = expectedFid(111);
    private static final FileID ratesId = expectedFid(112);

    private static final long expiry = 1_234_567_890L;
    private static final long nextExpiry = 2_234_567_890L;
    private static final int curCentEquiv = 1;
    private static final int curHbarEquiv = 12;
    private static final int nxtCentEquiv = 2;
    private static final int nxtHbarEquiv = 31;
    private static final ExchangeRateSet expectedDefaultRates =
            ExchangeRateSet.newBuilder()
                    .setCurrentRate(fromRatio(curCentEquiv, curHbarEquiv, expiry))
                    .setNextRate(fromRatio(nxtCentEquiv, nxtHbarEquiv, nextExpiry))
                    .build();
    private static final byte[] aKeyEncoding = "not-really-A-key".getBytes();
    private static final byte[] bKeyEncoding = "not-really-B-key".getBytes();

    private Address addressA;
    private Address addressB;
    private Map<FileID, byte[]> data;
    private Map<FileID, HFileMeta> metadata;
    private JEd25519Key masterKey;
    private AddressBook currentBook;
    private HFileMeta expectedInfo;
    private TieredHederaFs hfs;
    private MerkleSpecialFiles specialFiles;
    private MockFileNumbers fileNumbers;
    private Consumer<ServicesConfigurationList> propertiesCb;
    private Consumer<ServicesConfigurationList> permissionsCb;
    private Consumer<ExchangeRateSet> ratesCb;
    private Consumer<ThrottleDefinitions> throttlesCb;
    private Consumer<CurrentAndNextFeeSchedule> schedulesCb;
    private SysFileCallbacks callbacks;
    private PropertySource properties;

    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private HfsSystemFilesManager subject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws DecoderException {
        final var keyBytes = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
        masterKey = new JEd25519Key(keyBytes);
        expectedInfo =
                new HFileMeta(
                        false,
                        JKey.mapKey(
                                Key.newBuilder()
                                        .setKeyList(
                                                KeyList.newBuilder()
                                                        .addKeys(
                                                                MiscUtils.asKeyUnchecked(
                                                                        masterKey)))
                                        .build()),
                        expiry);

        final var keyA = mock(PublicKey.class);
        given(keyA.getEncoded()).willReturn(aKeyEncoding);
        addressA = mock(Address.class);
        final var aIpv4 = new byte[] {(byte) 1, (byte) 2, (byte) 3, (byte) 4};
        final var memoA = "A new memo that is not the node account ID.";
        given(addressA.getId()).willReturn(111L);
        given(addressA.getMemo()).willReturn(memoA);
        given(addressA.getAddressExternalIpv4()).willReturn(aIpv4);
        given(addressA.getSigPublicKey()).willReturn(keyA);

        final var keyB = mock(PublicKey.class);
        given(keyB.getEncoded()).willReturn(bKeyEncoding);
        addressB = mock(Address.class);
        final var bIpv4 = new byte[] {(byte) 2, (byte) 3, (byte) 4, (byte) 5};
        final var memoB = "0.0.3";
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
        specialFiles = mock(MerkleSpecialFiles.class);
        given(hfs.getData()).willReturn(data);
        given(hfs.getMetadata()).willReturn(metadata);
        given(hfs.specialFiles()).willReturn(specialFiles);
        fileNumbers = new MockFileNumbers();
        fileNumbers.setShard(0L);
        fileNumbers.setRealm(0L);
        given(specialFiles.contains(fileNumbers.toFid(111))).willReturn(false);

        properties = mock(PropertySource.class);
        given(properties.getStringProperty(BOOTSTRAP_HAPI_PERMISSIONS_PATH))
                .willReturn(bootstrapJutilPermsLoc);
        given(properties.getStringProperty(BOOTSTRAP_NETWORK_PROPERTIES_PATH))
                .willReturn(bootstrapJutilPropsLoc);
        given(properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY)).willReturn(expiry);
        given(properties.getIntProperty(BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV))
                .willReturn(curHbarEquiv);
        given(properties.getIntProperty(BOOTSTRAP_RATES_CURRENT_CENT_EQUIV))
                .willReturn(curCentEquiv);
        given(properties.getLongProperty(BOOTSTRAP_RATES_CURRENT_EXPIRY)).willReturn(expiry);
        given(properties.getIntProperty(BOOTSTRAP_RATES_NEXT_HBAR_EQUIV)).willReturn(nxtHbarEquiv);
        given(properties.getIntProperty(BOOTSTRAP_RATES_NEXT_CENT_EQUIV)).willReturn(nxtCentEquiv);
        given(properties.getLongProperty(BOOTSTRAP_RATES_NEXT_EXPIRY)).willReturn(nextExpiry);
        given(properties.getStringProperty(BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE))
                .willReturn("R4FeeSchedule.json");
        given(properties.getStringProperty(BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE))
                .willReturn("bootstrap/throttles.json");

        ratesCb = mock(Consumer.class);
        schedulesCb = mock(Consumer.class);
        propertiesCb = mock(Consumer.class);
        permissionsCb = mock(Consumer.class);
        throttlesCb = mock(Consumer.class);

        callbacks = mock(SysFileCallbacks.class);

        subject =
                new HfsSystemFilesManager(
                        () -> currentBook,
                        fileNumbers,
                        properties,
                        hfs,
                        () -> masterKey,
                        callbacks);
    }

    @Test
    void warnsIfForSomeReasonExtantBookIsUnobtainable() {
        subject.updateStakeDetails();

        assertThat(
                logCaptor.errorLogs(),
                contains(Matchers.startsWith("Existing address book was missing or corrupt")));
    }

    @Test
    void canUpdateStake() throws InvalidProtocolBufferException {
        final ArgumentCaptor<byte[]> updateCaptor = ArgumentCaptor.forClass(byte[].class);
        given(addressA.getMemo()).willReturn("0.0.4");
        final var book = legacyBookConstruction(currentBook);
        given(data.get(detailsId)).willReturn(book.toByteArray());

        given(addressA.getStake()).willReturn(123L);
        given(addressB.getStake()).willReturn(456L);

        subject.updateStakeDetails();

        verify(data).put(eq(detailsId), updateCaptor.capture());
        final var updatedBook = NodeAddressBook.parseFrom(updateCaptor.getValue());
        // then:
        final var updatedA = updatedBook.getNodeAddress(0);
        assertEquals(AccountID.newBuilder().setAccountNum(4L).build(), updatedA.getNodeAccountId());
        assertEquals(123L, updatedA.getStake());
        // and:
        final var updatedB = updatedBook.getNodeAddress(1);
        assertEquals(AccountID.newBuilder().setAccountNum(3L).build(), updatedB.getNodeAccountId());
        assertEquals(456L, updatedB.getStake());
    }

    @Test
    void loadsEverything() {
        final var sub = mock(SystemFilesManager.class);
        willCallRealMethod().given(sub).loadObservableSystemFiles();

        sub.loadObservableSystemFiles();

        verify(sub).loadApplicationProperties();
        verify(sub).loadApiPermissions();
        verify(sub).loadFeeSchedules();
        verify(sub).loadExchangeRates();
        verify(sub).loadThrottleDefinitions();
        verify(sub).setObservableFilesLoaded();
    }

    @Test
    void createsEverything() {
        final var sub = mock(SystemFilesManager.class);
        willCallRealMethod().given(sub).createManagedFilesIfMissing();

        sub.createManagedFilesIfMissing();

        verify(sub).createAddressBookIfMissing();
        verify(sub).createNodeDetailsIfMissing();
        verify(sub).createUpdateFilesIfMissing();
    }

    @Test
    void canMarkFilesNotLoaded() {
        subject.setObservableFilesLoaded();
        assertTrue(subject.areObservableFilesLoaded());

        subject.setObservableFilesNotLoaded();
        assertFalse(subject.areObservableFilesLoaded());
    }

    @Test
    void tracksFileLoading() {
        assertFalse(subject.areObservableFilesLoaded());

        subject.setObservableFilesLoaded();

        assertTrue(subject.areObservableFilesLoaded());
    }

    @Test
    void doesntCreateAddressBookIfPresent() {
        given(hfs.exists(bookId)).willReturn(true);

        subject.createAddressBookIfMissing();

        verify(hfs).exists(bookId);
        verify(data, never()).put(any(), any());
        verify(metadata, never())
                .put(
                        argThat(bookId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
    }

    @Test
    void createsAddressBookIfMissing() {
        final var expectedBook = legacyBookConstruction(currentBook);
        given(hfs.exists(bookId)).willReturn(false);

        subject.createAddressBookIfMissing();

        verify(hfs).exists(bookId);
        verify(data)
                .put(
                        argThat(bookId::equals),
                        argThat(
                                (byte[] bytes) ->
                                        Arrays.equals(expectedBook.toByteArray(), bytes)));
        verify(metadata)
                .put(
                        argThat(bookId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
    }

    @Test
    void createsNodeDetailsIfMissing() {
        final var expectedDetails = legacyBookConstruction(currentBook);
        given(hfs.exists(detailsId)).willReturn(false);

        subject.createNodeDetailsIfMissing();

        verify(hfs).exists(detailsId);
        verify(data)
                .put(
                        argThat(detailsId::equals),
                        argThat(
                                (byte[] bytes) ->
                                        Arrays.equals(expectedDetails.toByteArray(), bytes)));
    }

    @Test
    void createsEmptyUpdateFiles() {
        final var canonicalUpgradeFid = IdUtils.asFile("0.0.150");
        // setup:
        given(hfs.specialFiles()).willReturn(specialFiles);
        given(hfs.exists(canonicalUpgradeFid)).willReturn(true);

        // when:
        subject.createUpdateFilesIfMissing();

        // then:
        for (var i = fileNumbers.firstSoftwareUpdateFile();
                i <= fileNumbers.lastSoftwareUpdateFile();
                i++) {
            verify(specialFiles).update(fileNumbers.toFid(i), new byte[0]);
        }
    }

    @Test
    void noOpsOnExistingUpdateFeatureFile() {
        given(hfs.exists(any())).willReturn(true);
        given(specialFiles.contains(any())).willReturn(true);

        subject.createUpdateFilesIfMissing();

        verify(hfs, never()).getMetadata();
        verify(hfs, never()).getData();
    }

    @Test
    void loadsPropsFromHfsIfAvailable() {
        given(hfs.exists(appPropsId)).willReturn(true);
        given(hfs.cat(appPropsId)).willReturn(fromState.toByteArray());
        given(callbacks.propertiesCb()).willReturn(propertiesCb);

        subject.loadApplicationProperties();

        verify(hfs).exists(appPropsId);
        verify(propertiesCb).accept(fromState);
    }

    @Test
    void loadsPermsFromHfsIfAvailable() {
        given(hfs.exists(apiPermsId)).willReturn(true);
        given(hfs.cat(apiPermsId)).willReturn(fromState.toByteArray());
        given(callbacks.permissionsCb()).willReturn(permissionsCb);

        subject.loadApiPermissions();

        verify(hfs).exists(apiPermsId);
        verify(permissionsCb).accept(fromState);
    }

    @Test
    void loadsRatesFromHfsIfAvailable() {
        given(hfs.exists(ratesId)).willReturn(true);
        given(hfs.cat(ratesId)).willReturn(expectedDefaultRates.toByteArray());
        given(callbacks.exchangeRatesCb()).willReturn(ratesCb);

        subject.loadExchangeRates();

        verify(hfs).exists(ratesId);
        verify(ratesCb).accept(expectedDefaultRates);
    }

    @Test
    void createsRatesFromPropsIfMissing() {
        given(hfs.exists(ratesId)).willReturn(false);
        given(hfs.cat(ratesId)).willReturn(expectedDefaultRates.toByteArray());
        given(callbacks.exchangeRatesCb()).willReturn(ratesCb);

        subject.loadExchangeRates();

        verify(hfs).exists(ratesId);
        verify(metadata)
                .put(
                        argThat(ratesId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(data)
                .put(
                        argThat(ratesId::equals),
                        argThat(
                                (byte[] bytes) ->
                                        Arrays.equals(expectedDefaultRates.toByteArray(), bytes)));
        verify(ratesCb).accept(expectedDefaultRates);
    }

    @Test
    void loadsSchedulesFromHfsIfAvailable() throws IOException {
        final var schedules = Files.readAllBytes(Paths.get(R4_FEE_SCHEDULE_REPR_PATH));
        final var proto = CurrentAndNextFeeSchedule.parseFrom(schedules);
        given(hfs.exists(schedulesId)).willReturn(true);
        given(hfs.cat(schedulesId)).willReturn(schedules);
        given(callbacks.feeSchedulesCb()).willReturn(schedulesCb);

        subject.loadFeeSchedules();

        verify(hfs).exists(schedulesId);
        verify(schedulesCb).accept(proto);
    }

    @Test
    void loadsThrottlesFromHfsIfAvailable() throws IOException {
        final var proto = SerdeUtils.protoDefs("bootstrap/throttles.json");
        final var throttleBytes = proto.toByteArray();
        given(callbacks.throttlesCb()).willReturn(throttlesCb);
        given(hfs.exists(throttlesId)).willReturn(true);
        given(hfs.cat(throttlesId)).willReturn(throttleBytes);

        subject.loadThrottleDefinitions();

        verify(hfs).exists(throttlesId);
        verify(throttlesCb).accept(proto);
    }

    @Test
    void createsThrottlesFromResourceIfMissing() throws IOException {
        final var proto = SerdeUtils.protoDefs("bootstrap/throttles.json");
        final var throttleBytes = proto.toByteArray();
        given(callbacks.throttlesCb()).willReturn(throttlesCb);
        given(hfs.exists(throttlesId)).willReturn(false);
        given(hfs.cat(throttlesId)).willReturn(throttleBytes);

        subject.loadThrottleDefinitions();

        verify(hfs).exists(throttlesId);
        verify(metadata)
                .put(
                        argThat(throttlesId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(data)
                .put(
                        argThat(throttlesId::equals),
                        argThat((byte[] bytes) -> Arrays.equals(throttleBytes, bytes)));
    }

    @Test
    void logsSchedulesIfCorrupt() {
        final var desired = "Corrupt fee schedules in saved state (NONSENSE), unable to continue!";
        given(hfs.exists(schedulesId)).willReturn(false);
        given(hfs.cat(schedulesId)).willReturn(nonsense);

        assertThrows(IllegalStateException.class, subject::loadFeeSchedules);

        verify(hfs).exists(schedulesId);
        assertThat(logCaptor.errorLogs(), contains(desired));
    }

    @Test
    void throwsIseOnBootstrapLoaderException() {
        given(hfs.exists(schedulesId)).willReturn(false);
        final var mockedStatic = mockStatic(FeesJsonToProtoSerde.class);
        mockedStatic
                .when(() -> FeesJsonToProtoSerde.loadFeeScheduleFromStream(any()))
                .thenThrow(IOException.class);

        assertThrows(IllegalStateException.class, subject::loadFeeSchedules);

        assertThat(
                logCaptor.errorLogs(),
                contains(
                        "Failed to read bootstrap fee schedules, unable to continue!"
                                + " java.io.IOException: null"));

        mockedStatic.close();
    }

    @Test
    void createsSchedulesFromResourcesIfMissing() throws IOException {
        final var schedules = Files.readAllBytes(Paths.get(R4_FEE_SCHEDULE_REPR_PATH));
        given(hfs.exists(schedulesId)).willReturn(false);
        given(hfs.cat(schedulesId)).willReturn(schedules);
        given(callbacks.feeSchedulesCb()).willReturn(schedulesCb);

        subject.loadFeeSchedules();

        verify(hfs).exists(schedulesId);
        verify(metadata)
                .put(
                        argThat(schedulesId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(data)
                .put(
                        argThat(schedulesId::equals),
                        argThat((byte[] bytes) -> Arrays.equals(schedules, bytes)));
    }

    @Test
    void bootstrapsPropsAsEmptyConfigListIfNoDiskProperties() {
        final var emptyConfig = ServicesConfigurationList.getDefaultInstance();
        given(hfs.exists(appPropsId)).willReturn(false);
        given(hfs.cat(appPropsId)).willReturn(emptyConfig.toByteArray());
        given(callbacks.propertiesCb()).willReturn(propertiesCb);

        subject.loadApplicationProperties();

        verify(hfs).exists(appPropsId);
        verify(metadata)
                .put(
                        argThat(appPropsId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(data)
                .put(
                        argThat(appPropsId::equals),
                        argThat((byte[] bytes) -> Arrays.equals(emptyConfig.toByteArray(), bytes)));
        verify(propertiesCb).accept(ServicesConfigurationList.getDefaultInstance());
    }

    @Test
    void bootstrapsPermissionsAsDefaultConfigListIfNoDiskProperties() throws IOException {
        final var defaultPermissions = defaultApiPermissionsFromResource();
        given(properties.getStringProperty(BOOTSTRAP_HAPI_PERMISSIONS_PATH))
                .willReturn(nonexistentBootstrapJutilLoc);
        given(hfs.exists(apiPermsId)).willReturn(false);
        given(hfs.cat(apiPermsId)).willReturn(defaultPermissions.toByteArray());
        given(callbacks.permissionsCb()).willReturn(permissionsCb);

        subject.loadApiPermissions();

        verify(hfs).exists(apiPermsId);
        verify(metadata)
                .put(
                        argThat(apiPermsId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(data)
                .put(
                        argThat(apiPermsId::equals),
                        argThat(
                                (byte[] bytes) ->
                                        Arrays.equals(defaultPermissions.toByteArray(), bytes)));
        verify(permissionsCb).accept(defaultPermissions);
    }

    @Test
    void bootstrapsPermissionsAsEmptyConfigListIfNoDiskPropertiesAndNoResourceWithErrorLog() {
        final var emptyPermissions = ServicesConfigurationList.getDefaultInstance();
        subject.setPermsSysFileDefaultResource("not-a-real-resource");
        given(properties.getStringProperty(BOOTSTRAP_HAPI_PERMISSIONS_PATH))
                .willReturn(nonexistentBootstrapJutilLoc);
        given(hfs.exists(apiPermsId)).willReturn(false);
        given(hfs.cat(apiPermsId)).willReturn(emptyPermissions.toByteArray());
        given(callbacks.permissionsCb()).willReturn(permissionsCb);

        subject.loadApiPermissions();

        verify(hfs).exists(apiPermsId);
        verify(metadata)
                .put(
                        argThat(apiPermsId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(data)
                .put(
                        argThat(apiPermsId::equals),
                        argThat(
                                (byte[] bytes) ->
                                        Arrays.equals(emptyPermissions.toByteArray(), bytes)));
        verify(permissionsCb).accept(emptyPermissions);
        assertThat(
                logCaptor.errorLogs(),
                contains(
                        "Could not bootstrap permissions, only superusers will be able to perform"
                                + " HAPI operations!"));
    }

    @Test
    void bootstrapsPropsAsEmptyConfigListIfNoDiskPropertiesAndNoResourceWithErrorLog() {
        final var emptyProps = ServicesConfigurationList.getDefaultInstance();
        subject.setPropsSysFileDefaultResource("not-a-real-resource");
        given(properties.getStringProperty(BOOTSTRAP_NETWORK_PROPERTIES_PATH))
                .willReturn(nonexistentBootstrapJutilLoc);
        given(hfs.exists(appPropsId)).willReturn(false);
        given(hfs.cat(appPropsId)).willReturn(emptyProps.toByteArray());
        given(callbacks.propertiesCb()).willReturn(propertiesCb);

        subject.loadApplicationProperties();

        verify(hfs).exists(appPropsId);
        verify(metadata)
                .put(
                        argThat(appPropsId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(data)
                .put(
                        argThat(appPropsId::equals),
                        argThat((byte[] bytes) -> Arrays.equals(emptyProps.toByteArray(), bytes)));
        verify(propertiesCb).accept(emptyProps);
        assertThat(
                logCaptor.errorLogs(),
                contains(
                        "Could not bootstrap properties, likely benign but resources should be"
                                + " double-checked!"));
    }

    @Test
    void bootstrapsPropsFromDiskOnNetworkStartup() throws IOException {
        final var jutilProps = new Properties();
        fromBootstrapFile
                .getNameValueList()
                .forEach(setting -> jutilProps.put(setting.getName(), setting.getValue()));
        jutilProps.store(Files.newOutputStream(Paths.get(bootstrapJutilPropsLoc)), "Testing 123");
        given(hfs.exists(appPropsId)).willReturn(false);
        given(hfs.cat(appPropsId)).willReturn(fromBootstrapFile.toByteArray());
        given(callbacks.propertiesCb()).willReturn(propertiesCb);

        subject.loadApplicationProperties();

        verify(hfs).exists(appPropsId);
        verify(metadata)
                .put(
                        argThat(appPropsId::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(data)
                .put(
                        argThat(appPropsId::equals),
                        argThat(
                                (byte[] bytes) ->
                                        Arrays.equals(fromBootstrapFile.toByteArray(), bytes)));
        verify(propertiesCb).accept(fromBootstrapFile);

        Files.deleteIfExists(Paths.get(bootstrapJutilPropsLoc));
    }

    @Test
    void throwsIseOnNonsenseStateProperties() {
        given(hfs.exists(appPropsId)).willReturn(true);
        given(hfs.cat(appPropsId)).willReturn(nonsense);

        assertThrows(IllegalStateException.class, subject::loadApplicationProperties);
    }

    @Test
    void getsMasterKeyOnlyOnce() {
        final var file150 = fileNumbers.toFid(150L);
        given(hfs.exists(any())).willReturn(true);
        given(hfs.exists(file150)).willReturn(false);
        given(specialFiles.contains(any())).willReturn(true);
        final var keySupplier = mock(Supplier.class);
        given(keySupplier.get()).willReturn(masterKey);
        subject =
                new HfsSystemFilesManager(
                        () -> currentBook, fileNumbers, properties, hfs, keySupplier, callbacks);

        subject.createUpdateFilesIfMissing();
        subject.createUpdateFilesIfMissing();

        verify(metadata, times(2))
                .put(
                        argThat(file150::equals),
                        argThat(info -> expectedInfo.toString().equals(info.toString())));
        verify(specialFiles, times(2)).update(file150, new byte[0]);
        verify(keySupplier).get();
    }

    private static FileID expectedFid(final long num) {
        return FileID.newBuilder().setFileNum(num).build();
    }

    private static NodeAddressBook legacyBookConstruction(final AddressBook fromBook) {
        final var builder = NodeAddressBook.newBuilder();
        for (int i = 0; i < fromBook.getSize(); i++) {
            final var address = fromBook.getAddress(i);
            final var publicKey = address.getSigPublicKey();
            final var nodeIP = address.getAddressExternalIpv4();
            final var nodeIPStr = Address.ipString(nodeIP);
            final var memo = address.getMemo();
            final var nodeAddress =
                    NodeAddress.newBuilder()
                            .setIpAddress(ByteString.copyFromUtf8(nodeIPStr))
                            .setMemo(ByteString.copyFromUtf8(memo))
                            .setRSAPubKey(CommonUtils.hex(publicKey.getEncoded()))
                            .setNodeId(address.getId())
                            .setStake(address.getStake());

            final var serviceEndpoint =
                    ServiceEndpoint.newBuilder()
                            .setIpAddressV4(ByteString.copyFrom(address.getAddressExternalIpv4()))
                            .setPort(address.getPortExternalIpv4());
            nodeAddress.addServiceEndpoint(serviceEndpoint);

            setNodeAccountIfAvailforAddressBook(address, nodeAddress);
            builder.addNodeAddress(nodeAddress);
        }
        return builder.build();
    }

    private static void setNodeAccountIfAvailforAddressBook(
            final Address entry, final NodeAddress.Builder builder) {
        try {
            final var id = EntityIdUtils.parseAccount(entry.getMemo());
            builder.setNodeAccountId(id);
        } catch (final IllegalArgumentException ignore) {
        }
    }

    private static final ExchangeRate fromRatio(final int cent, final int hbar, final long expiry) {
        return ExchangeRate.newBuilder()
                .setCentEquiv(cent)
                .setHbarEquiv(hbar)
                .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expiry))
                .build();
    }

    private ServicesConfigurationList defaultApiPermissionsFromResource() throws IOException {
        final var builder = ServicesConfigurationList.newBuilder();
        final var loader = HfsSystemFilesManager.class.getClassLoader();
        try (final var in = loader.getResourceAsStream("api-permission.properties")) {
            final var permissions = new Properties();
            permissions.load(in);
            HfsSystemFilesManager.mapOrderedJutilProps(permissions, new StringBuilder(), builder);
        }
        return builder.build();
    }
}
