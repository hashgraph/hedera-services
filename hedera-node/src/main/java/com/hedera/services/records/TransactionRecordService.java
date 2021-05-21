package com.hedera.services.records;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.util.List;

public class TransactionRecordService {
	private final TransactionContext txnCtx;

	public TransactionRecordService(TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
	}

	/**
	 * Update the record of the active transaction with the changes to the
	 * given token. There are two cases,
	 * <ol>
	 * <li>The token was just created, and the receipt should include its id.</li>
	 * <li>The token's total supply has changed, and the receipt should include its new supply.</li>
	 * </ol>
	 * Only the second is implemented at this time.
	 *
	 * @param token
	 * 		the model of a changed token
	 */
	public void includeChangesToToken(Token token) {
		if (token.hasChangedSupply()) {
			txnCtx.setNewTotalSupply(token.getTotalSupply());
		}
	}

	/**
	 * Update the record of the active transaction with the changes to the
	 * given token relationship. Only balance changes need to be included in
	 * the record.
	 *
	 * <b>IMPORTANT:</b> In general, the {@code TransactionRecordService} must
	 * be able to aggregate balance changes from one or more token relationships
	 * into token-scoped transfer lists for the record. Since we are beginning
	 * with just a refactor of burn and mint, the below implementation suffices
	 * for now.
	 *
	 * @param tokenRel
	 * 		the model of a changed token relationship
	 */
	public void includeChangesToTokenRel(TokenRelationship tokenRel) {
		if (tokenRel.getBalanceChange() == 0L) {
			return;
		}

		final var tokenId = tokenRel.getToken().getId();
		final var accountId = tokenRel.getAccount().getId();
		final var supplyChangeXfers = List.of(
				TokenTransferList.newBuilder()
						.setToken(TokenID.newBuilder()
								.setShardNum(tokenId.getShard())
								.setRealmNum(tokenId.getRealm())
								.setTokenNum(tokenId.getNum()))
						.addTransfers(AccountAmount.newBuilder()
								.setAccountID(AccountID.newBuilder()
										.setShardNum(accountId.getShard())
										.setRealmNum(accountId.getRealm())
										.setAccountNum(accountId.getNum()))
								.setAmount(tokenRel.getBalanceChange()))
						.build()
		);
		txnCtx.setTokenTransferLists(supplyChangeXfers);
	}
}
