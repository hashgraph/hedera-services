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

/**
 * This metadata encapsulates a MerkleAccount's token associations details.
 *
 * numAssociations: tracks the total token association the account currently have.
 * numZeroBalances: tracks the number of token associations with zero balances on this account.
 * latestAssociation: tracks the latest token association of the account.
 */
public record TokenAssociationMetadata(int numAssociations, int numZeroBalances, EntityNumPair latestAssociation) {
	public static final TokenAssociationMetadata EMPTY_TOKEN_ASSOCIATION_META = new TokenAssociationMetadata(
			0, 0, MISSING_NUM_PAIR);

	public TokenAssociationMetadata {
		Objects.requireNonNull(latestAssociation);
	}

	public boolean hasNoTokenBalances() {
		return numAssociations == numZeroBalances;
	}
}
