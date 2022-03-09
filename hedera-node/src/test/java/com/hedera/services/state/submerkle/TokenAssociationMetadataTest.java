package com.hedera.services.state.submerkle;

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

import com.hedera.services.utils.EntityNumPair;
import org.junit.jupiter.api.Test;

import static com.hedera.services.utils.EntityNumPair.MISSING_NUM_PAIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAssociationMetadataTest {
	final int numAssociations = 10;
	final int numZeroBalances = 7;
	final EntityNumPair lastAssociation = EntityNumPair.fromLongs(1001L, 1002L);
	final TokenAssociationMetadata subject =
			new TokenAssociationMetadata(numAssociations, numZeroBalances, lastAssociation);

	@Test
	void equalsWorks() {
		final var other1 = new TokenAssociationMetadata(numAssociations+1, numZeroBalances, lastAssociation);
		final var other2 = new TokenAssociationMetadata(numAssociations, numZeroBalances-1, lastAssociation);
		final var other3 = new TokenAssociationMetadata(numAssociations, numZeroBalances, MISSING_NUM_PAIR);
		final var identicalSubject = new TokenAssociationMetadata(numAssociations, numZeroBalances, lastAssociation);

		assertEquals(subject, identicalSubject);
		assertNotEquals(subject, other1);
		assertNotEquals(subject, other2);
		assertNotEquals(subject, other3);
	}

	@Test
	void toStringWorks() {
		final var expected = "numAssociations = " + numAssociations + ", " +
				"numZeroBalances = " + numZeroBalances + ", lastAssociation = " + lastAssociation;

		assertEquals(expected, subject.toString());
	}

	@Test
	void validatesAllTokenBalancesIfZero() {
		assertFalse(subject.hasNoTokenBalances());

		final var other = new TokenAssociationMetadata(5, 5, MISSING_NUM_PAIR);

		assertTrue(other.hasNoTokenBalances());
	}
}
