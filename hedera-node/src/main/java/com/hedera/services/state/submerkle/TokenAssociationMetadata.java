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

import java.util.Objects;

import static com.hedera.services.utils.EntityNumPair.MISSING_NUM_PAIR;

public record TokenAssociationMetadata(int numAssociations, int numZeroBalances, EntityNumPair lastAssociation) {
	public static final TokenAssociationMetadata EMPTY_TOKEN_ASSOCIATION_META = new TokenAssociationMetadata(
			0, 0, MISSING_NUM_PAIR);

	public TokenAssociationMetadata {
		Objects.requireNonNull(lastAssociation);
	}

	@Override
	public int hashCode() {
		return Objects.hash(numAssociations, numZeroBalances, lastAssociation);
	}

	@Override
	public String toString() {
		return "numAssociations = " + numAssociations + ", " +
				"numZeroBalances = " + numZeroBalances + ", " +
				"lastAssociations = " + lastAssociation.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || TokenAssociationMetadata.class != o.getClass()) {
			return false;
		}

		var that = (TokenAssociationMetadata) o;

		return this.numAssociations == that.numAssociations &&
				this.numZeroBalances == that.numZeroBalances &&
				this.lastAssociation.equals(that.lastAssociation);
	}
}
