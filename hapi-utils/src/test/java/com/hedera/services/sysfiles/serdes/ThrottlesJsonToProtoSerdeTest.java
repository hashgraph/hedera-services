package com.hedera.services.sysfiles.serdes;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.hedera.services.TestUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ThrottleBucket;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.hederahashgraph.api.proto.java.ThrottleGroup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static org.junit.jupiter.api.Assertions.*;

class ThrottlesJsonToProtoSerdeTest {
	@Test
	void loadsExpectedDefs() throws IOException {
		// expect:
		assertDoesNotThrow(ThrottlesJsonToProtoSerde::new);

		// given:
		var actual = TestUtils.protoDefs("bootstrap/throttles.json");

		// expect:
		assertEquals(expected(), actual);
	}

	private ThrottleDefinitions expected() {
		return ThrottleDefinitions.newBuilder()
				.addThrottleBuckets(aBucket())
				.addThrottleBuckets(bBucket())
				.addThrottleBuckets(cBucket())
				.addThrottleBuckets(dBucket())
				.build();
	}

	private ThrottleBucket aBucket() {
		return ThrottleBucket.newBuilder()
				.setName("A")
				.setBurstPeriod(2)
				.addThrottleGroups(from(10000, List.of(CryptoTransfer, CryptoCreate)))
				.addThrottleGroups(from(12, List.of(ContractCall)))
				.addThrottleGroups(from(3000, List.of(TokenMint)))
				.build();
	}

	private ThrottleBucket bBucket() {
		return ThrottleBucket.newBuilder()
				.setName("B")
				.setBurstPeriod(2)
				.addThrottleGroups(from(10, List.of(ContractCall)))
				.build();
	}

	private ThrottleBucket cBucket() {
		return ThrottleBucket.newBuilder()
				.setName("C")
				.setBurstPeriod(3)
				.addThrottleGroups(from(2, List.of(CryptoCreate)))
				.addThrottleGroups(from(100, List.of(TokenCreate, TokenAssociateToAccount)))
				.build();
	}

	private ThrottleBucket dBucket() {
		return ThrottleBucket.newBuilder()
				.setName("D")
				.setBurstPeriod(4)
				.addThrottleGroups(from(1_000_000, List.of(CryptoGetAccountBalance, TransactionGetReceipt)))
				.build();
	}

	private ThrottleGroup from(int opsPerSec, List<HederaFunctionality> functions) {
		return ThrottleGroup.newBuilder()
				.setOpsPerSec(opsPerSec)
				.addAllOperations(functions)
				.build();
	}
}
