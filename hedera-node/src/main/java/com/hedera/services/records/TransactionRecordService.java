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
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.fchashmap.FCOneToManyRelation;

import java.util.ArrayList;
import java.util.List;

public class TransactionRecordService {
	private final TransactionContext txnCtx;

	public TransactionRecordService(TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
	}

	/**
	 * Update the record of the active transaction with the changes to the
	 * given token. There are three cases,
	 * <ol>
	 * <li>The token was just created, and the receipt should include its id.</li>
	 * <li>The token's total supply has changed, and the receipt should include its new supply.</li>
	 * <li>The token is of type {@link com.hedera.services.state.enums.TokenType} NON_FUNGIBLE_UNIQUE and Mint operation was executed </li>
	 * </ol>
	 * Only the second and third is implemented at this time.
	 *
	 * @param token the model of a changed token
	 */
	public void includeChangesToToken(Token token) {
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
	 * @param tokenRel the model of a changed token relationship
	 */
	public void includeChangesToTokenRel(TokenRelationship tokenRel) {
		if (tokenRel.getBalanceChange() == 0L || !tokenRel.hasCommonRepresentation()) {
			return;
		}

		final var tokenId = tokenRel.getToken().getId();
		final var accountId = tokenRel.getAccount().getId();

		final var supplyChangeXfers = List.of(TokenTransferList.newBuilder()
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

	/**
	 * Update the record of the active transaction with the ownership changes produced in the context of the current transaction
	 * @param ownershipTracker the model of ownership changes
	 */
	public void includeOwnershipChanges(OwnershipTracker ownershipTracker) {
		if (ownershipTracker.isEmpty()) {
			return;
		}

		List<TokenTransferList> transferLists = new ArrayList<>();
		var changes = ownershipTracker.getChanges();
		for (Id token : changes.keySet()) {
			TokenID tokenID = TokenID.newBuilder()
					.setShardNum(token.getShard())
					.setRealmNum(token.getRealm())
					.setTokenNum(token.getNum())
					.build();

			List<NftTransfer> transfers = new ArrayList<>();
			for (OwnershipTracker.Change change : changes.get(token)) {
				Id previousOwner = change.getPreviousOwner();
				Id newOwner = change.getNewOwner();
				transfers.add(NftTransfer.newBuilder()
						.setSenderAccountID(AccountID.newBuilder()
								.setShardNum(previousOwner.getShard())
								.setRealmNum(previousOwner.getRealm())
								.setAccountNum(previousOwner.getNum()))
						.setReceiverAccountID(AccountID.newBuilder()
								.setShardNum(newOwner.getShard())
								.setRealmNum(newOwner.getRealm())
								.setAccountNum(newOwner.getNum()))
						.setSerialNumber(change.getSerialNumber()).build());
			}
			transferLists.add(TokenTransferList.newBuilder()
					.setToken(tokenID)
					.addAllNftTransfers(transfers)
					.build());
		}
		txnCtx.setTokenTransferLists(transferLists);
	}
}
