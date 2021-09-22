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
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.Topic;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class TransactionRecordService {
	private final TransactionContext txnCtx;

	@Inject
	public TransactionRecordService(TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
	}

	/**
	 * Update the record of the active transaction with the changes to the
	 * given token. There are three cases,
	 * <ol>
	 * <li>The token was just created, and the receipt should include its id.</li>
	 * <li>The token's total supply has changed, and the receipt should include its new supply.</li>
	 * <li>The token is of type {@link com.hedera.services.state.enums.TokenType} NON_FUNGIBLE_UNIQUE and Mint
	 * operation was executed </li>
	 * </ol>
	 *
	 *
	 * @param token
	 * 				the model of a changed token
	 */
	public void includeChangesToToken(Token token) {
		if (token.isNew()) {
			txnCtx.setCreated(token.getId().asGrpcToken());
		}
		if (token.hasChangedSupply()) {
			txnCtx.setNewTotalSupply(token.getTotalSupply());
		}
		if (token.hasMintedUniqueTokens()) {
			List<Long> serialNumbers = new ArrayList<>();
			for (UniqueToken uniqueToken : token.mintedUniqueTokens()) {
				serialNumbers.add(uniqueToken.getSerialNumber());
			}
			txnCtx.setCreated(serialNumbers);
		}
	}

	/**
	 * Updates the record of the active transaction with the fungible token balance adjustments it caused.
	 *
	 * <b>MAJOR CAVEAT:</b> Assumes the active transaction doesn't change any non-fungible
	 * unique token ownerships. This is valid for the current set of refactored HTS
	 * operations, which apply to <i>either</i> unique or common tokens. But it will not
	 * valid when CryptoTransfer is refactored!
	 *
	 * @param tokenRels
	 * 					List of models of the changed relationships
	 */
	public void includeChangesToTokenRels(final List<TokenRelationship> tokenRels) {
		final Map<Id, TokenTransferList.Builder> transferListMap = new HashMap<>();
		for (final var tokenRel : tokenRels) {
			if (!tokenRel.hasChangesForRecord()) {
				continue;
			}
			final var tokenId = tokenRel.getToken().getId();
			final var accountId = tokenRel.getAccount().getId();

			final var builder = transferListMap.computeIfAbsent(
					tokenId, ignore -> TokenTransferList.newBuilder().setToken(tokenId.asGrpcToken()));
			builder.addTransfers(AccountAmount.newBuilder()
					.setAccountID(accountId.asGrpcAccount())
					.setAmount(tokenRel.getBalanceChange()));
			transferListMap.put(tokenId, builder);
		}
		if (!transferListMap.isEmpty()) {
			var transferList = new ArrayList<TokenTransferList>();
			for (final var transferBuilder : transferListMap.values()) {
				transferList.add(transferBuilder.build());
			}
			txnCtx.setTokenTransferLists(transferList);
		}
	}

	/**
	 * Updates the record of the active transaction with the ownership changes it caused.
	 *
	 * <b>MAJOR CAVEAT:</b> Assumes the active transaction doesn't adjust any fungible
	 * common token balances. This is valid for the current set of refactored HTS
	 * operations, which apply to <i>either</i> unique or common tokens. But it will not
	 * valid when CryptoTransfer is refactored!
	 *
	 * @param ownershipTracker
	 * 					the model of ownership changes
	 */
	public void includeOwnershipChanges(OwnershipTracker ownershipTracker) {
		if (ownershipTracker.isEmpty()) {
			return;
		}

		List<TokenTransferList> transferLists = new ArrayList<>();
		var changes = ownershipTracker.getChanges();
		for (Id token : changes.keySet()) {
			TokenID tokenID = token.asGrpcToken();

			List<NftTransfer> transfers = new ArrayList<>();
			for (OwnershipTracker.Change change : changes.get(token)) {
				Id previousOwner = change.getPreviousOwner();
				Id newOwner = change.getNewOwner();
				transfers.add(NftTransfer.newBuilder()
						.setSenderAccountID(previousOwner.asGrpcAccount())
						.setReceiverAccountID(newOwner.asGrpcAccount())
						.setSerialNumber(change.getSerialNumber()).build());
			}
			transferLists.add(TokenTransferList.newBuilder()
					.setToken(tokenID)
					.addAllNftTransfers(transfers)
					.build());
		}
		txnCtx.setTokenTransferLists(transferLists);
	}

	/**
	 * Updates the record of the current transaction with the changes in the given {@link Topic}.
	 * Currently, the only operation refactored is the TopicCreate.
	 * This function should be updated correspondingly while refactoring the other Topic operations.
	 * 
	 * @param topic - the Topic, whose changes have to be included in the receipt
	 */
	public void includeChangesToTopic(Topic topic) {
		if (topic.isNew()) {
			txnCtx.setCreated(topic.getId().asGrpcTopic());
		}
	}
}
