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
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.tokens.TokenStore;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;

import java.util.List;

/**
 * An interface which defines methods for the UniqueTokenStore
 *
 * @author Yoan Sredkov
 */
public interface UniqueStore extends TokenStore {


	/**
	 * Attempts to mint unique tokens in a batch. Rollbacks the batch if anything goes wrong.
	 * It's important to note that this mint should be called only on unique tokens, otherwise we'll go into invalid state
	 *
	 * @param txBody {@link TokenMintTransactionBody} the body of the mint tx
	 * @param creationTime {@link RichInstant} the consensus time of the minting
	 * @return {@link CreationResult<List>} List of serial numbers on success, else - the cause of the fail {@link ResponseCodeEnum}
	 */
	CreationResult<List<Long>> mint(final TokenMintTransactionBody txBody, final RichInstant creationTime);

	/**
	 * Checks whether given unique token exists
	 *
	 * @param id
	 * @return true/false
	 */
	boolean nftExists(final NftID id);

	/**
	 * Fetches an unique token
	 * @param id {@link NftID}
	 * @return {@link MerkleUniqueToken}
	 */
	MerkleUniqueToken get(final NftID id);
}