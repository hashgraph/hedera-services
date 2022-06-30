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

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum NftProperty implements BeanProperty<MerkleUniqueToken> {
	OWNER {
		@Override
		public BiConsumer<MerkleUniqueToken, Object> setter() {
			return (t, o) -> t.setOwner((EntityId) o);
		}

		@Override
		public Function<MerkleUniqueToken, Object> getter() {
			return MerkleUniqueToken::getOwner;
		}
	},
	CREATION_TIME {
		@Override
		public BiConsumer<MerkleUniqueToken, Object> setter() {
			return (t, o) -> t.setPackedCreationTime((long) o);
		}

		@Override
		public Function<MerkleUniqueToken, Object> getter() {
			return MerkleUniqueToken::getPackedCreationTime;
		}
	},
	METADATA {
		@Override
		public BiConsumer<MerkleUniqueToken, Object> setter() {
			return (t, o) -> t.setMetadata((byte[]) o);
		}

		@Override
		public Function<MerkleUniqueToken, Object> getter() {
			return MerkleUniqueToken::getMetadata;
		}
	},
	SPENDER {
		@Override
		public BiConsumer<MerkleUniqueToken, Object> setter() {
			return (t, o) -> t.setSpender((EntityId) o);
		}

		@Override
		public Function<MerkleUniqueToken, Object> getter() {
			return MerkleUniqueToken::getSpender;
		}
	},
}
