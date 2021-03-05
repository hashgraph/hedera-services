package com.hedera.services.store.nft;

import com.hedera.services.state.merkle.MerkleNft;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.Store;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftCreateTransactionBody;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;

public interface NftStore extends Store<NftID, MerkleNft> {
	NftID MISSING_NFT = NftID.getDefaultInstance();

	CreationResult<NftID> createProvisionally(NftCreateTransactionBody request, AccountID sponsor, long now);

	ResponseCodeEnum transferOwnership(NftID nft, String serialNo, AccountID from, AccountID to);

	default NftID resolve(NftID id) {
		return exists(id) ? id : MISSING_NFT;
	}
}
