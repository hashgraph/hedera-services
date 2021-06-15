package com.hedera.services.ledger;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static java.math.BigInteger.ZERO;

public class PureTransferSemanticChecks {
	public ResponseCodeEnum fullPureValidation(
			int maxHbarAdjusts,
			int maxTokenAdjusts,
			TransferList hbarAdjustsWrapper,
			List<TokenTransferList> tokenAdjustsList
	) {
		final var hbarAdjusts = hbarAdjustsWrapper.getAccountAmountsList();

		if (hasRepeatedAccount(hbarAdjusts)) {
			return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
		}
		if (!isNetZeroAdjustment(hbarAdjusts)) {
			return INVALID_ACCOUNT_AMOUNTS;
		}
		if (!isAcceptableSize(hbarAdjusts, maxHbarAdjusts)) {
			return TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
		}

		final var tokenValidity = validateTokenTransferSizes(tokenAdjustsList, maxTokenAdjusts);
		if (tokenValidity != OK) {
			return tokenValidity;
		}
		return validateTokenTransferSemantics(tokenAdjustsList);
	}

	public ResponseCodeEnum validateTokenTransferSizes(
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

	ResponseCodeEnum validateTokenTransferSemantics(List<TokenTransferList> tokenTransfersList) {
		if (tokenTransfersList.isEmpty()) {
			return OK;
		}
		Set<TokenID> uniqueTokens = new HashSet<>();
		for (var tokenTransfers : tokenTransfersList) {
			if (!tokenTransfers.hasToken()) {
				return INVALID_TOKEN_ID;
			}
			uniqueTokens.add(tokenTransfers.getToken());
			final var adjusts = tokenTransfers.getTransfersList();
			for (var adjust : adjusts) {
				if (!adjust.hasAccountID()) {
					return INVALID_ACCOUNT_ID;
				}
				if (adjust.getAmount() == 0) {
					return INVALID_ACCOUNT_AMOUNTS;
				}
			}
			if (hasRepeatedAccount(adjusts)) {
				return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
			}
			if (!isNetZeroAdjustment(adjusts)) {
				return TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
			}
		}
		if (uniqueTokens.size() < tokenTransfersList.size()) {
			return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
		}
		return OK;
	}

	boolean hasRepeatedAccount(List<AccountAmount> adjusts) {
		final int n = adjusts.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				if (adjusts.get(i).getAccountID().equals(adjusts.get(j).getAccountID())) {
					return true;
				}
			}
		}
		return false;
	}

	boolean isNetZeroAdjustment(List<AccountAmount> adjusts) {
		var net = ZERO;
		for (var adjust : adjusts) {
			net = net.add(BigInteger.valueOf(adjust.getAmount()));
		}
		return net.equals(ZERO);
	}

	boolean isAcceptableSize(List<AccountAmount> hbarAdjusts, int maxHbarAdjusts) {
		return hbarAdjusts.size() <= maxHbarAdjusts;
	}
}
