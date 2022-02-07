package com.hedera.services.txns.crypto;

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


import com.google.protobuf.BoolValue;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.NftAllowance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AllowanceHelpersTest {

	@Test
	void aggregatedCorrectly() {
		Map<FcTokenAllowanceId, FcTokenAllowance> map = new TreeMap<>();
		final var Nftid = FcTokenAllowanceId.from(EntityNum.fromTokenId(asToken("0.0.1000")),
				EntityNum.fromAccountId(asAccount("0.0.1001")));
		final var val = FcTokenAllowance.from(false, List.of(1L, 100L));
		map.put(Nftid, val);
		assertEquals(2, aggregateNftAllowances(map));
	}

	@Test
	void aggregatedListCorrectly() {
		List<NftAllowance> list = new ArrayList<>();
		final var Nftid = NftAllowance.newBuilder().setSpender(asAccount("0.0.1000"))
				.addAllSerialNumbers(List.of(1L, 10L)).setTokenId(asToken("0.0.10001"))
				.setOwner(asAccount("0.0.5000")).setApprovedForAll(
						BoolValue.of(false)).build();
		final var Nftid2 = NftAllowance.newBuilder().setSpender(asAccount("0.0.1000"))
				.addAllSerialNumbers(List.of(1L, 100L)).setTokenId(asToken("0.0.10001"))
				.setOwner(asAccount("0.0.5000")).setApprovedForAll(
						BoolValue.of(false)).build();
		list.add(Nftid);
		list.add(Nftid2);
		assertEquals(4, aggregateNftAllowances(list));
	}
}
