package com.hedera.services.store.tokens.utils;

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import java.util.Optional;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;

public class GrpcUtils {
	public TokenNftInfo reprOf(TokenID type, long serialNo, MerkleUniqueToken nft, Optional<AccountID> treasury) {
		final var nftId = NftID.newBuilder()
				.setTokenID(type)
				.setSerialNumber(serialNo);

		AccountID effectiveOwner;
		var explicitOwner = nft.getOwner();
		if (explicitOwner.equals(MISSING_ENTITY_ID)) {
			if (!treasury.isPresent()) {
				throw new IllegalArgumentException(EntityIdUtils.readableId(type)
						+ "." + serialNo
						+ " has wildcard owner, but no treasury information was provided");
			}
			effectiveOwner = treasury.get();
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
