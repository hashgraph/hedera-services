package com.hedera.services.state.migration;

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

import com.hedera.services.ServicesState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.utils.MiscUtils.forEach;

/**
 * Release 0.25.x migration to initialize the {@code MerkleAccountState#numTreasuryTitles} field with the
 * number of non-deleted tokens for which the account serves as treasury.
 */
public class ReleaseTwentyFiveMigration {
	private static final Logger log = LogManager.getLogger(ReleaseTwentyFiveMigration.class);

	public static void initTreasuryTitleCounts(final ServicesState initializingState) {
		final var tokens = initializingState.tokens();
		final var accounts = initializingState.accounts();
		forEach(tokens, (tokenNum, token) -> {
			if (token.isDeleted()) {
				return;
			}
			final var treasuryNum = token.treasuryNum();
			if (!accounts.containsKey(treasuryNum)) {
				log.warn("Token {} has non-existent treasury {}, skipping",
						tokenNum.toIdString(), treasuryNum.toIdString());
			} else {
				final var mutableAccount = accounts.getForModify(treasuryNum);
				final var curTitles = mutableAccount.getNumTreasuryTitles();
				mutableAccount.setNumTreasuryTitles(curTitles + 1);
			}
		});
	}

	private ReleaseTwentyFiveMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
