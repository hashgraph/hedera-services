package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.google.protobuf.BytesValue;
import com.hedera.services.stream.proto.StorageChange;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SidecarUtilsTest {

	@Test
	void stripsLeadingZerosInChangeRepresentation() {
		final var slot = Bytes.wrap(Address.BLS12_G1MULTIEXP.toArray());
		final var access = Pair.of(
								Bytes.of(Address.BLS12_MAP_FP2_TO_G2.toArray()),
								Bytes.of(Address.BLS12_G1MUL.toArray()));
		final var expected = StorageChange.newBuilder()
				.setSlot(ByteString.copyFrom(Address.BLS12_G1MULTIEXP.trimLeadingZeros().toArray()))
				.setValueRead(ByteString.copyFrom(Address.BLS12_MAP_FP2_TO_G2.trimLeadingZeros().toArray()))
				.setValueWritten(BytesValue.newBuilder()
						.setValue(ByteString.copyFrom(Address.BLS12_G1MUL.trimLeadingZeros().toArray()))
						.build())
				.build();
		final var actual = SidecarUtils.trimmedGrpc(slot, access);
		assertEquals(expected, actual.build());
	}

}