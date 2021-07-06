package com.hedera.services.pricing;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hederahashgraph.api.proto.java.FeeComponents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsableResourceTest {
	private final FeeComponents comps = FeeComponents.newBuilder()
			.setConstant(1)
			.setBpt(2)
			.setVpt(3)
			.setRbh(4)
			.setSbh(5)
			.setGas(6)
			.setBpr(7)
			.setSbpr(8)
			.build();

	@Test
	void noGetterTypos() {
		assertEquals(1, UsableResource.CONSTANT.getter().applyAsLong(comps));
		assertEquals(2, UsableResource.BPT.getter().applyAsLong(comps));
		assertEquals(3, UsableResource.VPT.getter().applyAsLong(comps));
		assertEquals(4, UsableResource.RBH.getter().applyAsLong(comps));
		assertEquals(5, UsableResource.SBH.getter().applyAsLong(comps));
		assertEquals(6, UsableResource.GAS.getter().applyAsLong(comps));
		assertEquals(7, UsableResource.BPR.getter().applyAsLong(comps));
		assertEquals(8, UsableResource.SBPR.getter().applyAsLong(comps));
	}
}
