/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.merkle.map;

import static com.swirlds.demo.platform.TestUtil.deleteFirstRecord;
import static com.swirlds.demo.platform.TestUtil.generateRandomContent;
import static com.swirlds.demo.platform.TestUtil.generateTxRecord;
import static com.swirlds.demo.platform.TestUtil.getExpirationTime;
import static com.swirlds.merkle.map.test.lifecycle.EntityType.FCQ;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.io.InputOutputStream;
import com.swirlds.demo.platform.PlatformTestingToolState;
import com.swirlds.demo.platform.expiration.ExpirationUtils;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.map.test.lifecycle.ExpectedValue;
import com.swirlds.merkle.map.test.pta.MapKey;
import com.swirlds.merkle.map.test.pta.TransactionRecord;
import com.swirlds.platform.state.DualStateImpl;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

public class MapValueFCQTests {

    static final int fcqTTL = 10;
    private static final int TRANSACTION_RECORD_LENGTH = 100;
    private static final int INIT_BALANCE = 100_000;
    private static final Random RANDOM = new Random();
    private static final Random random = new Random();
    static PlatformTestingToolState state;
    private static MerkleCryptography cryptography;
    private static MapValueFCQ mapValueFCQ;
    private static MapKey mapKey;

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        cryptography = MerkleCryptoFactory.getInstance();

        mapKey = new MapKey(0, 0, random.nextLong());
        state = Mockito.spy(PlatformTestingToolState.class);
        Platform platform = Mockito.spy(PlatformWithDeprecatedMethods.class);
        when(platform.getSelfId()).thenReturn(NodeId.createMain(0));
        AddressBook addressBook = Mockito.spy(AddressBook.class);
        when(addressBook.getNumberWithStake()).thenReturn(4);
        when(platform.getAddressBook()).thenReturn(addressBook);
        state.init(platform, new DualStateImpl(), InitTrigger.RESTART, SoftwareVersion.NO_VERSION);
        state.initChildren();
    }

    @BeforeEach
    public void buildMapValueFCQ() {
        state.initializeExpirationQueueAndAccountsSet();
        final byte[] content = generateRandomContent();
        final long index = random.nextLong();
        final long balance = random.nextLong();
        mapValueFCQ = MapValueFCQ.newBuilder()
                .setIndex(index)
                .setBalance(balance)
                .setContent(content)
                .setMapKey(mapKey)
                .setExpirationQueue(state.getExpirationQueue())
                .setExpirationTime(Instant.now().getEpochSecond() + fcqTTL)
                .setAccountsWithExpiringRecords(state.getAccountsWithExpiringRecords())
                .build();
    }

    @Test
    @Tag(TIME_CONSUMING)
    public void serializeDeserializeTest() throws IOException {
        final MerkleMap<MapKey, MapValueFCQ<TransactionRecord>> fcm = new MerkleMap<>();
        try (final InputOutputStream io = new InputOutputStream()) {
            for (int index = 0; index < 10_000; index++) {
                final MapKey mapKey = new MapKey(index, index, index);
                final byte[] content = new byte[TRANSACTION_RECORD_LENGTH];
                RANDOM.nextBytes(content);
                @SuppressWarnings("unchecked")
                final MapValueFCQ<TransactionRecord> value =
                        (MapValueFCQ<TransactionRecord>) new MapValueFCQ.MapValueFCQBuilder<>()
                                .setIndex(index)
                                .setBalance(INIT_BALANCE + index)
                                .setContent(content)
                                .build();
                fcm.put(mapKey, value);
            }
            cryptography.digestTreeSync(fcm);

            io.getOutput().writeMerkleTree(testDirectory, fcm);
            io.startReading();
            final MerkleMap<MapKey, MapValueFCQ<TransactionRecord>> deserialized =
                    io.getInput().readMerkleTree(testDirectory, Integer.MAX_VALUE);
            cryptography.digestTreeSync(deserialized);

            assertEquals(fcm, deserialized);
        }
    }

    @Test
    public void addAndDeleteRecordsTest() {
        final long sizeBefore = mapValueFCQ.getRecordsSize();

        final int addTimes = 1000;
        final int deleteTimes = 500;
        final int transferTimes = 1000;

        generateRecords(addTimes, deleteTimes, transferTimes);

        assertEquals(addTimes - deleteTimes + transferTimes, mapValueFCQ.getRecordsSize() - sizeBefore);
    }

    @ParameterizedTest(name = "{index} => addRecords={0}, deleteTransactions={1}, transferTransactions={2}")
    @CsvSource({"10, 10, 10"})
    public void addRecordToExpirationQueueTest(int addRecords, int deleteTransactions, int transferTransactions) {
        state.getExpirationQueue().clear();
        state.getAccountsWithExpiringRecords().clear();

        generateRecords(addRecords, deleteTransactions, transferTransactions);
        ExpirationUtils.addRecordsDuringRebuild(
                state.getStateMap(), state.getExpirationQueue(), state.getAccountsWithExpiringRecords());
        assertEquals(1, state.getAccountsWithExpiringRecords().size());
        assertNotEquals(0, state.getExpirationQueue().size());
    }

    @Test
    @Tag(TIME_CONSUMING)
    public void purgeExpirationTest() throws Exception {

        generateRecords(1000, 0, 1000);

        Thread.sleep(10_000);

        generateRecords(500, 0, 500);

        final long purgeTime = Instant.now().getEpochSecond();
        final long removedNum = state.purgeExpiredRecords(purgeTime);

        int expectedNumberOfPurgedRecords = 2001;

        // get new Value for this mapKey
        mapValueFCQ = state.getStateMap().getAccountFCQMap().get(mapKey);

        TransactionRecord firstRecord =
                (TransactionRecord) mapValueFCQ.getRecords().peek();
        assertTrue(firstRecord.getExpirationTime() > purgeTime);
        assertEquals(1000, mapValueFCQ.getRecordsSize());
        assertEquals(expectedNumberOfPurgedRecords, removedNum);
        assertEquals(1, state.getExpirationQueue().size());
    }

    private void generateRecords(final int addNum, final int deleteNum, final int transferNum) {
        // addRecord multiple times
        for (int i = 0; i < addNum; i++) {
            mapValueFCQ = addRecord();
        }
        // deleteIndex multiple times
        for (int i = 0; i < deleteNum; i++) {
            mapValueFCQ = deleteFirstRecord(mapValueFCQ);
        }
        // transfer multiple times
        for (int i = 0; i < transferNum; i++) {
            mapValueFCQ = transfer();
        }
        state.getStateMap().getAccountFCQMap().put(mapKey.copy(), mapValueFCQ);
        state.getStateExpectedMap()
                .getExpectedMap()
                .put(
                        mapKey,
                        new ExpectedValue(
                                FCQ, mapValueFCQ.calculateHash(), true, null, null, null, mapValueFCQ.getUid()));
    }

    private MapValueFCQ<TransactionRecord> addRecord() {
        TransactionRecord txRecord = generateTxRecord(getExpirationTime(fcqTTL));
        return mapValueFCQ.addRecord(
                txRecord.getBalance(),
                txRecord,
                mapKey,
                state.getExpirationQueue(),
                state.getAccountsWithExpiringRecords());
    }

    private MapValueFCQ<TransactionRecord> transfer() {
        final long balance = random.nextLong();
        final byte[] content = generateRandomContent();
        final boolean from = random.nextInt(100) < 50;
        if (from) {
            return mapValueFCQ.transferFrom(
                    balance,
                    content,
                    mapKey,
                    getExpirationTime(fcqTTL),
                    state.getExpirationQueue(),
                    state.getAccountsWithExpiringRecords());
        }
        return mapValueFCQ.transferTo(
                balance,
                content,
                mapKey,
                getExpirationTime(fcqTTL),
                state.getExpirationQueue(),
                state.getAccountsWithExpiringRecords());
    }
}
