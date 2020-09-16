package com.hedera.services.keys;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Provides static helpers for interrogating the simple keys in a complex Hedera key.
 *
 * @author Michael Tinker
 * @see JKey
 */
public class HederaKeyTraversal {

	private static final Logger log = LogManager.getLogger(HederaKeyTraversal.class);

	private HederaKeyTraversal() {
		throw new IllegalStateException("Utility Class");
	}

	/**
	 * Performs a left-to-right DFS of the Hedera key structure, offering each simple key to
	 * the provided {@link Consumer}.
	 *
	 * @param key
	 * 		the top-level Hedera key to traverse.
	 * @param actionOnSimpleKey
	 * 		the logic to apply to each visited simple key.
	 */
	public static void visitSimpleKeys(JKey key, Consumer<JKey> actionOnSimpleKey) {
		if (key.hasThresholdKey()) {
			key.getThresholdKey().getKeys().getKeysList().forEach(k -> visitSimpleKeys(k, actionOnSimpleKey));
		} else if (key.hasKeyList()) {
			key.getKeyList().getKeysList().forEach(k -> visitSimpleKeys(k, actionOnSimpleKey));
		} else {
			actionOnSimpleKey.accept(key);
		}
	}

	/**
	 * Counts the simple keys present in a complex Hedera key.
	 *
	 * @param key
	 * 		the top-level Hedera key.
	 * @return the number of simple keys in the leaves of the Hedera key.
	 */
	public static int numSimpleKeys(JKey key) {
		AtomicInteger count = new AtomicInteger(0);
		visitSimpleKeys(key, ignore -> count.incrementAndGet());
		return count.get();
	}

	/**
	 * Counts the simple keys present in an account's Hedera key.
	 *
	 * @param account
	 * 		the account with the Hedera key of interest.
	 * @return the number of simple keys.
	 */
	public static int numSimpleKeys(MerkleAccount account) {
		try {
			return numSimpleKeys(account.getKey());
		} catch (Exception ignore) {
			log.warn(ignore.getMessage());
			return 0;
		}
	}
}
