package com.hedera.services.store.tokens.views.utils;

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

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import javax.annotation.Nullable;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;

public class GrpcUtils {
	public TokenNftInfo reprOf(TokenID type, long serialNo, MerkleUniqueToken nft, AccountID treasury) {
		return doRepr(type, serialNo, nft, treasury);
	}

	private TokenNftInfo doRepr(
			TokenID type,
			long serialNo,
			MerkleUniqueToken nft,
			@Nullable AccountID treasury
	) {
		final var nftId = NftID.newBuilder()
				.setTokenID(type)
				.setSerialNumber(serialNo);

		AccountID effectiveOwner;
		var explicitOwner = nft.getOwner();
		if (explicitOwner.equals(MISSING_ENTITY_ID)) {
			if (treasury == null) {
				throw new IllegalArgumentException(EntityIdUtils.readableId(type)
						+ "." + serialNo
						+ " has wildcard owner, but no treasury information was provided");
			}
			effectiveOwner = treasury;
		} else {
			effectiveOwner =  explicitOwner.toGrpcAccountId();
		}

		return TokenNftInfo.newBuilder()
				.setNftID(nftId)
				.setAccountID(effectiveOwner)
				.setCreationTime(nft.getCreationTime().toGrpc())
				.setMetadata(ByteString.copyFrom(nft.getMetadata()))
				.build();
	}
}
