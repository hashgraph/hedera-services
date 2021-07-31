package com.hedera.services.state.migration;

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

import com.hedera.services.ServicesState;
import com.hedera.services.state.LegacyStateChildIndices;
import com.hedera.services.state.StateChildIndices;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.copy.MerkleCopy;

public class Release0170Migration {
	@FunctionalInterface
	interface TreeCopier {
		void copyToLocation(MerkleInternal parent, int position, MerkleNode child);
	}

	private static TreeCopier treeCopier = MerkleCopy::copyTreeToLocation;

	public static void moveLargeFcmsToBinaryRoutePositions(ServicesState initializingState) {
		/* First swap the address book and unique tokens */
		final var movableBook = initializingState.getChild(LegacyStateChildIndices.ADDRESS_BOOK).copy();
		treeCopier.copyToLocation(
				initializingState,
				StateChildIndices.UNIQUE_TOKENS,
				initializingState.getChild(LegacyStateChildIndices.UNIQUE_TOKENS));
		initializingState.setChild(StateChildIndices.ADDRESS_BOOK, movableBook);

		/* Second swap the network context and token associations */
		final var movableContext = initializingState.getChild(LegacyStateChildIndices.NETWORK_CTX).copy();
		treeCopier.copyToLocation(
				initializingState,
				StateChildIndices.TOKEN_ASSOCIATIONS,
				initializingState.getChild(LegacyStateChildIndices.TOKEN_ASSOCIATIONS));
		initializingState.setChild(StateChildIndices.NETWORK_CTX, movableContext);
	}

	static void setTreeCopier(TreeCopier treeCopier) {
		Release0170Migration.treeCopier = treeCopier;
	}

	Release0170Migration() {
		throw new IllegalStateException("Utility class");
	}
}
