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

import com.hedera.services.sigs.metadata.lookups.SafeLookupResult;
import com.hedera.services.tokens.TokenStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;

import java.util.function.Function;

import static com.hedera.services.sigs.metadata.TokenSigningMetadata.from;
import static com.hedera.services.sigs.metadata.lookups.SafeLookupResult.failure;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_TOKEN;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;

/**
 * Defines a type able to look up metadata associated to the signing activities
 * of any Hedera entity (account, smart contract, file, topic, or token).
 *
 * @author Michael Tinker
 */
public interface SigMetadataLookup {
	Function<
			TokenStore,
			Function<TokenID, SafeLookupResult<TokenSigningMetadata>>> REF_LOOKUP_FACTORY = tokenStore -> ref -> {
		TokenID id;
		return TokenStore.MISSING_TOKEN.equals(id = tokenStore.resolve(ref))
				? failure(MISSING_TOKEN)
				: new SafeLookupResult<>(from(tokenStore.get(id)));
	};

	ContractSigningMetadata lookup(ContractID contract) throws Exception;

	SafeLookupResult<FileSigningMetadata> fileSigningMetaFor(FileID id);
	SafeLookupResult<TopicSigningMetadata> topicSigningMetaFor(TopicID id);
	SafeLookupResult<TokenSigningMetadata> tokenSigningMetaFor(TokenID id);
	SafeLookupResult<AccountSigningMetadata> accountSigningMetaFor(AccountID id);
}
