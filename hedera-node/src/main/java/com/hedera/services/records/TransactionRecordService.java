package com.hedera.services.records;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
	 * given token relationships. Only balance changes need to be included in
	 * the record.
	 *
	 * <b>IMPORTANT:</b> In general, the {@code TransactionRecordService} must
	 * be able to aggregate balance changes from one or more token relationships
	 * into token-scoped transfer lists for the record. Since we are beginning
	 * with just a refactor of burn and mint, the below implementation suffices
	 * for now.
	 *
	 * @param tokenRels
	 * 		List of the model of a changed token relationship
	 */
	public void includeChangesToTokenRel(List<TokenRelationship> tokenRels) {
		Map<Id, TokenTransferList.Builder> transferListMap = new HashMap<>();
		for(var tokenRel : tokenRels) {
			if (tokenRel.getBalanceChange() == 0L) {
				continue;
			}
			final var tokenId = tokenRel.getToken().getId();
			final var accountId = tokenRel.getAccount().getId();

			var tokenTransferListBuilder = transferListMap.getOrDefault(tokenId,
					TokenTransferList.newBuilder().setToken(TokenID.newBuilder()
							.setShardNum(tokenId.getShard())
							.setRealmNum(tokenId.getRealm())
							.setTokenNum(tokenId.getNum())));

			tokenTransferListBuilder.addTransfers(AccountAmount.newBuilder()
					.setAccountID(AccountID.newBuilder()
							.setShardNum(accountId.getShard())
							.setRealmNum(accountId.getRealm())
							.setAccountNum(accountId.getNum()))
					.setAmount(tokenRel.getBalanceChange()));
			transferListMap.put(tokenId, tokenTransferListBuilder);
		}

		txnCtx.setTokenTransferLists(
				transferListMap.values().stream().map(TokenTransferList.Builder::build).collect(Collectors.toList())
		);
	}
}
