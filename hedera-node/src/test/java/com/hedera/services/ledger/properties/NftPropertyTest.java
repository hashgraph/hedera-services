package com.hedera.services.ledger.properties;

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

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NftPropertyTest {
	private final byte[] aMeta = "abcdefgh".getBytes();
	private final EntityId aEntity = new EntityId(1, 2, 3);
	private final EntityId bEntity = new EntityId(2, 3, 4);
	private final RichInstant aInstant = new RichInstant(1_234_567L, 1);

	@Test
	void getterWorks() {
		// given:
		final var aSubject = new MerkleUniqueToken(aEntity, aMeta, aInstant);
		// and:
		final var getter = NftProperty.OWNER.getter();

		// expect:
		assertEquals(aEntity, getter.apply(aSubject));
	}

	@Test
	void setterWorks() {
		// given:
		final var aSubject = new MerkleUniqueToken(aEntity, aMeta, aInstant);
		// and:
		final var setter = NftProperty.OWNER.setter();

		// when:
		setter.accept(aSubject, bEntity);

		// expect:
		assertSame(bEntity, aSubject.getOwner());
	}
}
