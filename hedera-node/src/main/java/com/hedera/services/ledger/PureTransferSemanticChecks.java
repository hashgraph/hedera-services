package com.hedera.services.ledger;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;

public class PureTransferSemanticChecks {
	public ResponseCodeEnum validateTokenTransfers(
			List<TokenTransferList> tokenTransfersList,
			int maxListLen
	) {
		final int numScopedTransfers = tokenTransfersList.size();
		if (numScopedTransfers == 0) {
			return OK;
		}

		if (numScopedTransfers > maxListLen) {
			return TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
		}

		int count = 0;
		for (var scopedTransfers : tokenTransfersList) {
			int transferCounts = scopedTransfers.getTransfersCount();
			if (transferCounts == 0) {
				return EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
			}

			count += transferCounts;

			if (count > maxListLen) {
				return TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
			}
		}

		return OK;
	}
}
