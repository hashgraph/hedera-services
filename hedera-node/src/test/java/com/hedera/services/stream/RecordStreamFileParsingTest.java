package com.hedera.services.stream;

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
	static void setUp() throws ConstructableRegistryException {
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
	void parseRCDV5files() throws Exception {
		final File out = new File("src/test/resources/record0.0.3/out.log");
		final File recordsDir = new File("src/test/resources/record0.0.3");
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
		assertEquals(EMPTY_HASH, startHash);
		assertNotEquals(0, recordsCount);
		assertNotEquals(EMPTY_HASH, endHash);
	}

	@Test
	public void readHashForV2() {
		final String recordV2Dir = "src/test/resources/recordV2";
		byte[] hash = RecordStream.readPrevFileHash(recordV2Dir);
		// the hash read from this directory should not be empty
		assertFalse(Arrays.equals(new byte[DigestType.SHA_384.digestLength()], hash));
	}
}
