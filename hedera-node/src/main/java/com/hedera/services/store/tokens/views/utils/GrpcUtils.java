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
import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import javax.annotation.Nullable;

/**
 * Small helper class to provide a query-friendly representation of a Merkle
 * unique token whose owner id may be the sentinel {@code 0.0.0} representing
 * treasury ownership.
 */
public final class GrpcUtils {
	private GrpcUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}

	/**
	 * Build a query-friendly representation of the given unique token,
	 * using the given treasury account id to substitute for the sentinel
	 * {@code 0.0.0} id if necessary.
	 *
	 * @param type
	 * 		the type of the unique token
	 * @param serialNo
	 * 		the serial number of the unique token
	 * @param nft
	 * 		the creation time, owner, and metadata of the unique token
	 * @param treasury
	 * 		if not null, the account id that should replace the sentinel id
	 * @return the desired query-friendly representation
	 */
	public static TokenNftInfo reprOf(
			final TokenID type,
			final long serialNo,
			final MerkleUniqueToken nft,
			@Nullable final AccountID treasury
	) {
		final var nftId = NftID.newBuilder()
				.setTokenID(type)
				.setSerialNumber(serialNo);

		AccountID effectiveOwner;
		if (nft.isTreasuryOwned()) {
			if (treasury == null) {
				throw new IllegalArgumentException(EntityIdUtils.readableId(type)
						+ "." + serialNo
						+ " has wildcard owner, but no treasury information was provided");
			}
			effectiveOwner = treasury;
		} else {
			effectiveOwner = nft.getOwner().toGrpcAccountId();
		}

		return TokenNftInfo.newBuilder()
				.setLedgerId(ServicesState.getLedgerId())
				.setNftID(nftId)
				.setAccountID(effectiveOwner)
				.setCreationTime(nft.getCreationTime().toGrpc())
				.setMetadata(ByteString.copyFrom(nft.getMetadata()))
				.build();
	}
}
