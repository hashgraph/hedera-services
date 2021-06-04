package com.hedera.services.store.tokens.unique;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.tokens.TokenStore;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;

/**
 * An interface which defines methods for the UniqueTokenStore
 *
 * @author Yoan Sredkov
 */
public interface UniqueStore extends TokenStore {

	ResponseCodeEnum mint(final TokenID tId, String memo, RichInstant creationTime);

	//	MerkleUniqueToken getUnique(final EntityId eId, final int serialNum);
//	Iterator<MerkleUniqueTokenId> getByToken(final MerkleUniqueToken token);
//	Iterator<MerkleUniqueTokenId> getByTokenFromIdx(final MerkleUniqueToken token, final int start);
//	Iterator<MerkleUniqueTokenId> getByTokenFromIdxToIdx(final MerkleUniqueToken token, final int start, final int end);
//	Iterator<MerkleUniqueTokenId> getByAccountFromIdxToIdx(final AccountID aId, final int start, final int end);
	boolean nftExists(final NftID id);

	MerkleUniqueToken get(final NftID id);
}