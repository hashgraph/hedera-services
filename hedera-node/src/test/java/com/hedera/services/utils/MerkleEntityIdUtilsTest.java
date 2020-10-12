package com.hedera.services.utils;

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

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.services.utils.EntityIdUtils.contractParsedFromSolidityAddress;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;

@RunWith(JUnitPlatform.class)
class MerkleEntityIdUtilsTest {
	@Test
	public void correctLiteral() {
		// expect:
		assertEquals("1.2.3", asLiteralString(IdUtils.asAccount("1.2.3")));
		assertEquals("11.22.33", asLiteralString(IdUtils.asFile("11.22.33")));
	}

	@Test
	public void serializesExpectedSolidityAddress() {
		// given:
		byte[] shardBytes = {
				(byte)0x00, (byte)0x00, (byte)0x00, (byte)0xAB,
		};
		int shard = Ints.fromByteArray(shardBytes);
		byte[] realmBytes = {
				(byte)0x00, (byte)0x00, (byte)0x00, (byte)0xCD,
				(byte)0xFE, (byte)0x00, (byte)0x00, (byte)0xFE,
		};
		long realm = Longs.fromByteArray(realmBytes);
		byte[] numBytes = {
				(byte)0x00, (byte)0x00, (byte)0x00, (byte)0xDE,
				(byte)0xBA, (byte)0x00, (byte)0x00, (byte)0xBA
		};
		long num = Longs.fromByteArray(numBytes);
		byte[] expected = {
				(byte)0x00, (byte)0x00, (byte)0x00, (byte)0xAB,
				(byte)0x00, (byte)0x00, (byte)0x00, (byte)0xCD,
				(byte)0xFE, (byte)0x00, (byte)0x00, (byte)0xFE,
				(byte)0x00, (byte)0x00, (byte)0x00, (byte)0xDE,
				(byte)0xBA, (byte)0x00, (byte)0x00, (byte)0xBA
		};
		// and:
		AccountID equivAccount = asAccount(String.format("%d.%d.%d", shard, realm, num));
		ContractID equivContract = asContract(String.format("%d.%d.%d", shard, realm, num));

		// when:
		byte[] actual = asSolidityAddress(shard, realm, num);
		byte[] anotherActual = asSolidityAddress(equivContract);
		// and:
		String actualHex = asSolidityAddressHex(equivAccount);

		// then:
		assertArrayEquals(expected, actual);
		assertArrayEquals(expected, anotherActual);
		// and:
		assertEquals(Hex.encodeHexString(expected), actualHex);
		// and:
		assertEquals(equivAccount, accountParsedFromSolidityAddress(actual));
		// and:
		assertEquals(equivContract, contractParsedFromSolidityAddress(actual));
	}

	@Test
	public void translatesIndexOutOfBoundsException() {
		// given:
		String invalidLiteral = "0.1";

		IllegalArgumentException iae = null;

		// when:
		try {
			accountParsedFromString(invalidLiteral);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertNotNull(iae.getCause());
		assertEquals(ArrayIndexOutOfBoundsException.class, iae.getCause().getClass());
	}

	@Test
	public void translatesNumberFormatException() {
		// given:
		String invalidLiteral = "0.0.asdf";

		IllegalArgumentException iae = null;

		// when:
		try {
			accountParsedFromString(invalidLiteral);
		} catch (IllegalArgumentException thrown) {
			iae = thrown;
		}

		// then:
		assertNotNull(iae.getCause());
		assertEquals(NumberFormatException.class, iae.getCause().getClass());
	}

	@Test
	public void parsesValidLiteral() {
		// given:
		String[] validLiterals = {
				"1.0.0", "0.1.0", "0.0.1", "1.2.3"
		};

		// expect:
		for (String literal : validLiterals) {
			assertEquals(asAccount(literal), accountParsedFromString(literal));
		}
	}

	@Test
	public void prettyPrintsTokenIds() {
		// given:
		TokenID id = TokenID.newBuilder().setShardNum(1).setRealmNum(2).setTokenNum(3).build();

		// expect:
		assertEquals("1.2.3", EntityIdUtils.readableId(id));
	}

	@Test
	public void prettyPrintsTopicIds() {
		// given:
		TopicID id = TopicID.newBuilder().setShardNum(1).setRealmNum(2).setTopicNum(3).build();

		// expect:
		assertEquals("1.2.3", EntityIdUtils.readableId(id));
	}

	@Test
	public void prettyPrintsAccountIds() {
		// given:
		AccountID id = AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

		// expect:
		assertEquals("1.2.3", EntityIdUtils.readableId(id));
	}

	@Test
	public void prettyPrintsFileIds() {
		// given:
		FileID id = FileID.newBuilder().setShardNum(1).setRealmNum(2).setFileNum(3).build();

		// expect:
		assertEquals("1.2.3", EntityIdUtils.readableId(id));
	}

	@Test
	public void givesUpOnNonAccountIds() {
		// given:
		String id = "my-account";

		// expect:
		assertEquals(id, EntityIdUtils.readableId(id));
	}

	@Test
	public void asContractWorks() {
		// setup:
		ContractID expected = ContractID.newBuilder().setShardNum(1).setRealmNum(2).setContractNum(3).build();

		// given:
		AccountID id = AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

		// when:
		ContractID cid = EntityIdUtils.asContract(id);

		// then:
		assertEquals(expected, cid);
	}

	@Test
	public void asFileWorks() {
		// setup:
		FileID expected = FileID.newBuilder().setShardNum(1).setRealmNum(2).setFileNum(3).build();

		// given:
		AccountID id = AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

		// when:
		FileID fid = EntityIdUtils.asFile(id);

		// then:
		assertEquals(expected, fid);
	}
}
