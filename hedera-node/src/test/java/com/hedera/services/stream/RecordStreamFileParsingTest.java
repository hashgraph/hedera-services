package com.hedera.services.stream;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.stream.RecordStream;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecordStreamFileParsingTest {
	private static final Hash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	@BeforeAll
	public static void setUp() throws ConstructableRegistryException {
		// this register is needed so that the Hash objects can be de-serialized
		ConstructableRegistry.registerConstructables("com.swirlds.common");
		// this register is needed so that RecordStreamObject can be de-serialized
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(RecordStreamObject.class, RecordStreamObject::new));
		// the following settings are needed for de-serializing Transaction
		SettingsCommon.maxTransactionCountPerEvent = 245760;
		SettingsCommon.maxTransactionBytesPerEvent = 245760;
		SettingsCommon.transactionMaxBytes = 6144;
	}

	@Test
	public void parseRCDV5files() throws Exception {
		// these files are generated with initial Hash be an empty Hash
		final String dir = "src/test/resources/recordStreamTest/record0.0.3";
		parseV5(dir, EMPTY_HASH);
	}

	@Test
	public void readHashForV2() {
		final String recordV2Dir = "src/test/resources/recordStreamTest/recordV2";
		byte[] hash = RecordStream.readPrevFileHash(recordV2Dir);
		// the hash read from this directory should not be empty
		assertFalse(Arrays.equals(new byte[DigestType.SHA_384.digestLength()], hash));
	}

	@Test
	public void parseRCDV5AfterV2files() throws Exception {
		final String v2Dir = "src/test/resources/recordStreamTest/recordV2";
		// these files are generated with initial Hash whose byte array is the bytes read from recordV2Dir
		final String v5Dir = "src/test/resources/recordStreamTest/recordV5AfterV2";
		final Hash expectedStartHash = new Hash(RecordStream.readPrevFileHash(v2Dir));
		parseV5(v5Dir, expectedStartHash);
	}

	private void parseV5(final String dir, final Hash expectedStartHash) throws Exception {
		final File out = new File(dir + "/out.log");
		// these files are generated with initial Hash be an empty Hash
		final File recordsDir = new File(dir);
		Iterator<SelfSerializable> iterator = LinkedObjectStreamUtilities.parseStreamDirOrFile(recordsDir,
				RecordStreamType.RECORD);

		Hash startHash = null;
		int recordsCount = 0;
		Hash endHash = null;
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {
			while (iterator.hasNext()) {
				SelfSerializable object = iterator.next();
				if (startHash == null) {
					startHash = (Hash) object;
					writer.write(startHash.toString());
					writer.write("\n");
				} else if (!iterator.hasNext()) {
					endHash = (Hash) object;
					writer.write(endHash.toString());
					writer.write("\n");
					break;
				} else {
					assertTrue(object instanceof RecordStreamObject);
					RecordStreamObject recordStreamObject = (RecordStreamObject) object;
					writer.write(recordStreamObject.toShortString());
					writer.write("\n");
					assertNotNull(recordStreamObject.getTimestamp());
					recordsCount++;
				}
			}
		}

		// the record streams are generated with an empty startHash
		assertEquals(expectedStartHash, startHash);
		assertNotEquals(0, recordsCount);
		assertNotEquals(startHash, endHash);
	}
}
