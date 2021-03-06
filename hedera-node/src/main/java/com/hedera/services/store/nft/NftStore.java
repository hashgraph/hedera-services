package com.hedera.services.store.nft;

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleNftType;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.Store;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftCreateTransactionBody;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.List;

public interface NftStore extends Store<NftID, MerkleNftType> {
	NftID MISSING_NFT = NftID.getDefaultInstance();


	ResponseCodeEnum mint(NftID nId, List<ByteString> serialNos);
	ResponseCodeEnum associate(AccountID aId, List<NftID> nftTypes);

	CreationResult<NftID> createProvisionally(NftCreateTransactionBody request, AccountID sponsor, long now);

	ResponseCodeEnum transferOwnership(NftID nId, ByteString serialNo, AccountID from, AccountID to);

	default NftID resolve(NftID nId) {
		return exists(nId) ? nId : MISSING_NFT;
	}
}
