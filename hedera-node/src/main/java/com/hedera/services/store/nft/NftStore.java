package com.hedera.services.store.nft;

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleNftType;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.Store;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftCreateTransactionBody;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public interface NftStore extends Store<NftID, MerkleNftType> {
	NftID MISSING_NFT = NftID.getDefaultInstance();

	CreationResult<NftID> createProvisionally(NftCreateTransactionBody request, AccountID sponsor, long now);

	ResponseCodeEnum transferOwnership(NftID nft, ByteString serialNo, AccountID from, AccountID to);

	default NftID resolve(NftID id) {
		return exists(id) ? id : MISSING_NFT;
	}
}
