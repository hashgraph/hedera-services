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
import com.hedera.services.state.org.LegacyStateChildIndices;
import com.hedera.services.state.org.StateChildIndices;
import com.hedera.services.state.org.StateVersions;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.copy.MerkleCopy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Static helper to move the two (expected) largest {@link com.swirlds.fcmap.FCMap}
 * children to the "binary route" positions in the Merkle tree, hence reducing the
 * size of their Merkle routes.
 */
public class Release0170Migration {
	private static final Logger log = LogManager.getLogger(Release0170Migration.class);

	@FunctionalInterface
	interface TreeCopier {
		void copyToLocation(MerkleInternal parent, int position, MerkleNode child);
	}

	private static TreeCopier treeCopier = MerkleCopy::copyTreeToLocation;

	public static void moveLargeFcmsToBinaryRoutePositions(ServicesState initializingState) {
		log.info("Performing 0.16.0 ==> 0.17.0 migration (state version {} to {})",
				StateVersions.RELEASE_0160_VERSION, StateVersions.RELEASE_0170_VERSION);

		/* First swap the address book and unique tokens */
		final var movableBook = initializingState.getChild(LegacyStateChildIndices.ADDRESS_BOOK).copy();
		treeCopier.copyToLocation(
				initializingState,
				StateChildIndices.UNIQUE_TOKENS,
				initializingState.getChild(LegacyStateChildIndices.UNIQUE_TOKENS));
		initializingState.setChild(StateChildIndices.ADDRESS_BOOK, movableBook);
		log.info("  ↪ Swapped address book ({} ==> {}) and unique token ({} to {}) FCMs",
				LegacyStateChildIndices.ADDRESS_BOOK, StateChildIndices.ADDRESS_BOOK,
				LegacyStateChildIndices.UNIQUE_TOKENS, StateChildIndices.UNIQUE_TOKENS);

		/* Second swap the network context and token associations */
		final var movableContext = initializingState.getChild(LegacyStateChildIndices.NETWORK_CTX).copy();
		treeCopier.copyToLocation(
				initializingState,
				StateChildIndices.TOKEN_ASSOCIATIONS,
				initializingState.getChild(LegacyStateChildIndices.TOKEN_ASSOCIATIONS));
		initializingState.setChild(StateChildIndices.NETWORK_CTX, movableContext);
		log.info("  ↪ Swapped network context ({} ==> {}) and token association ({} to {}) FCMs",
				LegacyStateChildIndices.NETWORK_CTX, StateChildIndices.NETWORK_CTX,
				LegacyStateChildIndices.TOKEN_ASSOCIATIONS, StateChildIndices.TOKEN_ASSOCIATIONS);
	}

	static void setTreeCopier(TreeCopier treeCopier) {
		Release0170Migration.treeCopier = treeCopier;
	}

	Release0170Migration() {
		throw new IllegalStateException("Utility class");
	}
}
