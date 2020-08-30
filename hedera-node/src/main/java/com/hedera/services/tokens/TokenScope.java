package com.hedera.services.tokens;

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

import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.TokenID;

public class TokenScope {
	private final TokenID id;
	private final MerkleToken token;

	private TokenScope(TokenID id, MerkleToken token) {
		this.id = id;
		this.token = token;
	}

	public static TokenScope scopeOf(TokenID id, MerkleToken token) {
		return new TokenScope(id, token);
	}

	public TokenID id() {
		return id;
	}

	public MerkleToken token() {
		return token;
	}
}
