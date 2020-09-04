package com.hedera.services.sigs.metadata;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleToken;

import java.util.Optional;

/**
 * Represents metadata about the signing attributes of a Hedera token.
 *
 * @author Michael Tinker
 */
public class TokenSigningMetadata {
	private final JKey adminKey;
	private final Optional<JKey> kycKey;
	private final Optional<JKey> freezeKey;

	private TokenSigningMetadata(
			JKey adminKey,
			Optional<JKey> kycKey,
			Optional<JKey> freezeKey
	) {
		this.adminKey = adminKey;
		this.kycKey = kycKey;
		this.freezeKey = freezeKey;
	}

	public static TokenSigningMetadata from(MerkleToken token) {
		return new TokenSigningMetadata(token.adminKey(), token.kycKey(), token.freezeKey());
	}

	public JKey adminKey() {
		return adminKey;
	}

	public Optional<JKey> optionalFreezeKey() {
		return freezeKey;
	}

	public Optional<JKey> optionalKycKey() {
		return kycKey;
	}
}
