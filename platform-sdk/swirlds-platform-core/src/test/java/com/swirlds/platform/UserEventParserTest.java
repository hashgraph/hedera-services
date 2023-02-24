/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Parse the event file layer by layer from user perspective follows the event stream format documentation.
 * First parse the stream object, then parse consensus event, then transactions, etc
 */
class UserEventParserTest {
    static {
        System.setProperty("log4j.configurationFile", "log4j2-test.xml");
    }

    private static final int MAX_SIG_LENGTH = 384;
    private static final long HASH_CLASS_ID = 0xf422da83a251741eL;
    private static final long CENSENSUS_EVENT_CLASS_ID = 0xe250a9fbdcc4b1baL;
    private static final int maxTransactionCountPerEvent = 245760;
    private static final int MAX_ADDRESSBOOK_SIZE = 2048;

    private boolean skipTransaction = false;
    private boolean dumpDetails = false;

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("com.swirlds.common.system.transaction");
        SettingsCommon.maxTransactionCountPerEvent = 245760;
        SettingsCommon.maxTransactionBytesPerEvent = 245760;
        SettingsCommon.transactionMaxBytes = 6144;
        SettingsCommon.maxAddressSizeAllowed = MAX_ADDRESSBOOK_SIZE;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // Event file version 5 with new Transaction class
                "src/test/resources/eventFiles/v5withTransactionV1/2021-04-18T04_27_00.004964000Z.evts",
                "src/test/resources/eventFiles/v5withTransactionV1/2021-04-18T04_26_00.021395000Z.evts",
                "src/test/resources/eventFiles/v5withTransactionV1/2021-04-18T04_25_00.051650000Z.evts",
                "src/test/resources/eventFiles/v5withTransactionV1/2021-04-18T04_24_00.033462000Z.evts",
            })
    void parseEventFileTest(String fileName) {
        // parse event file and does not skip transactions
        skipTransaction = false;
        parseSingleFile(fileName);

        // parse event file and skip transactions
        skipTransaction = true;
        parseSingleFile(fileName);
    }

    private void parseSingleFile(String fileName) {
        try (FileInputStream fis = new FileInputStream(new File(fileName));
                SerializableDataInputStream inputStream = new SerializableDataInputStream(fis)) {

            // read file version and object stream version
            int fileVersion = inputStream.readInt();
            assertEquals(5, fileVersion, "Incorrect fiel version");
            int objectStreamVersion = inputStream.readInt();
            assertEquals(1, objectStreamVersion, "incorrect object stream version");

            // read start running hash
            Hash startRunningHash = RunningHashDeserialize(inputStream, false);

            while (true) {
                // based on class ID to know how to deserialize next object
                long classID = inputStream.readLong();
                if (classID == HASH_CLASS_ID) {
                    // Lastly, read ending running hash then quit
                    Hash endRunningHash = RunningHashDeserialize(inputStream, true);
                    break;
                } else if (classID == CENSENSUS_EVENT_CLASS_ID) {
                    EventDeserialize(inputStream);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail("Paring file " + fileName);
        }
    }

    public void EventDeserialize(SerializableDataInputStream in) throws IOException {
        long version = in.readInt();
        BaseEventHashDataDeserialize(in);
        BaseEventUnhashDataDeserialize(in);
        ConsensusDataDeserialize(in);
    }

    public void BaseEventHashDataDeserialize(SerializableDataInputStream in) throws IOException {
        long version = in.readInt();
        long creatorId = in.readLong();
        long selfParentGen = in.readLong();
        long otherParentGen = in.readLong();
        // self parent hash, classID was not written
        Hash selfParentHash = RunningHashDeserialize(in, true);
        // other parent hash, classID was not written
        Hash otherParentHash = RunningHashDeserialize(in, true);

        Instant timeCreated = in.readInstant();
        if (version == 2) {
            int totalByteLength = in.readInt();
            if (skipTransaction) {
                byte[] skipData = new byte[totalByteLength];
                in.read(skipData);
            } else {
                in.readSerializableArray(Transaction[]::new, maxTransactionCountPerEvent, true);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported version " + version);
        }
        if (dumpDetails) {
            System.out.println("BaseEventHashedData{" + "creatorId="
                    + creatorId + ", selfParentGen="
                    + selfParentGen + ", otherParentGen="
                    + otherParentGen + ", selfParentHash="
                    + selfParentHash + ", otherParentHash="
                    + otherParentHash + ", timeCreated="
                    + timeCreated + '}');
        }
    }

    public void BaseEventUnhashDataDeserialize(SerializableDataInputStream in) throws IOException {
        long version = in.readInt();
        long creatorSeq = in.readLong();
        long otherId = in.readLong();
        long otherSeq = in.readLong();
        byte[] signature = in.readByteArray(MAX_SIG_LENGTH);
        if (dumpDetails) {
            System.out.println("BaseEventUnhashedData{" + "creatorSeq="
                    + creatorSeq + ", otherId="
                    + otherId + ", otherSeq="
                    + otherSeq + ", signature="
                    + CommonUtils.hex(signature, signature == null ? 0 : signature.length) + '}');
        }
    }

    public void ConsensusDataDeserialize(SerializableDataInputStream in) throws IOException {
        long version = in.readInt();
        long generation = in.readLong();
        long roundCreated = in.readLong();
        if (version == 1) {
            // read isWitness & isFamous
            in.readBoolean();
            in.readBoolean();
        }
        boolean stale = in.readBoolean();
        boolean lastInRoundReceived = in.readBoolean();
        Instant consensusTimestamp = in.readInstant();
        long roundReceived = in.readLong();
        long consensusOrder = in.readLong();
        if (dumpDetails) {
            System.out.println("ConsensusEventData{" + "generation="
                    + generation + ", roundCreated="
                    + roundCreated + ", stale="
                    + stale + ", consensusTimestamp="
                    + consensusTimestamp + ", roundReceived="
                    + roundReceived + ", consensusOrder="
                    + consensusOrder + ", lastInRoundReceived="
                    + lastInRoundReceived + '}');
        }
    }

    public Hash RunningHashDeserialize(SerializableDataInputStream in, boolean classIDAlreadyRead) throws IOException {
        if (!classIDAlreadyRead) {
            long classID = in.readLong();
        }
        long version = in.readInt();
        int digestType = in.readInt(); // 0x58ff811b for SHA-384
        int length = in.readInt(); // should be 64
        byte[] hashBytes = new byte[length];
        in.read(hashBytes);
        return (new Hash(hashBytes, DigestType.valueOf(digestType)));
    }
}
