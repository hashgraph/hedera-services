package com.hedera.services.store.contracts.precompile;

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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.txns.crypto.AutoCreationLogic.AUTO_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class SyntheticTxnFactory {
	@Inject
	public SyntheticTxnFactory() {
	}

	public TransactionBody.Builder createBurn(final BurnWrapper burnWrapper) {
		final var builder = TokenBurnTransactionBody.newBuilder();

		builder.setToken(burnWrapper.tokenType());
		if (burnWrapper.type() == NON_FUNGIBLE_UNIQUE) {
			builder.addAllSerialNumbers(burnWrapper.serialNos());
		} else {
			builder.setAmount(burnWrapper.amount());
		}

		return TransactionBody.newBuilder().setTokenBurn(builder);
	}

	public TransactionBody.Builder createMint(final MintWrapper mintWrapper) {
		final var builder = TokenMintTransactionBody.newBuilder();

		builder.setToken(mintWrapper.tokenType());
		if (mintWrapper.type() == NON_FUNGIBLE_UNIQUE) {
			builder.addAllMetadata(mintWrapper.metadata());
		} else {
			builder.setAmount(mintWrapper.amount());
		}

		return TransactionBody.newBuilder().setTokenMint(builder);
	}

	/**
	 * Given a list of {@link TokenTransferWrapper}s, where each wrapper gives changes scoped to a particular
	 * {@link TokenID}, returns a synthetic {@code CryptoTransfer} whose {@link CryptoTransferTransactionBody}
	 * consolidates the wrappers.
	 *
	 * If two wrappers both refer to the same token, their transfer lists are merged as specified in the
	 * {@link SyntheticTxnFactory#mergeTokenTransfers(TokenTransferList.Builder, TokenTransferList.Builder)}
	 * helper method.
	 *
	 * @param wrappers
	 * 		the wrappers to consolidate in a synthetic transaction
	 * @return the synthetic transaction
	 */
	public TransactionBody.Builder createCryptoTransfer(final List<TokenTransferWrapper> wrappers) {
		final var opBuilder = CryptoTransferTransactionBody.newBuilder();
		if (wrappers.size() == 1) {
			opBuilder.addTokenTransfers(wrappers.get(0).asGrpcBuilder());
		} else if (wrappers.size() > 1) {
			final List<TokenTransferList.Builder> builders = new ArrayList<>();
			final Map<TokenID, TokenTransferList.Builder> listBuilders = new HashMap<>();
			for (final TokenTransferWrapper wrapper : wrappers) {
				final var builder = wrapper.asGrpcBuilder();
				final var merged = listBuilders.merge(
						builder.getToken(), builder, SyntheticTxnFactory::mergeTokenTransfers);
				/* If merge() returns a builder other than the one we just created, it is already in the list */
				if (merged == builder) {
					builders.add(builder);
				}
			}
			builders.forEach(opBuilder::addTokenTransfers);
		}
		return TransactionBody.newBuilder().setCryptoTransfer(opBuilder);
	}

	public TransactionBody.Builder createAssociate(final Association association) {
		final var builder = TokenAssociateTransactionBody.newBuilder();

		builder.setAccount(association.accountId());
		builder.addAllTokens(association.tokenIds());

		return TransactionBody.newBuilder().setTokenAssociate(builder);
	}

	public TransactionBody.Builder createDissociate(final Dissociation dissociation) {
		final var builder = TokenDissociateTransactionBody.newBuilder();

		builder.setAccount(dissociation.accountId());
		builder.addAllTokens(dissociation.tokenIds());

		return TransactionBody.newBuilder().setTokenDissociate(builder);
	}

	public TransactionBody.Builder createAccount(final Key alias, final long balance) {
		final var txnBody = CryptoCreateTransactionBody.newBuilder()
				.setKey(alias)
				.setMemo(AUTO_MEMO)
				.setInitialBalance(balance)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
				.build();
		return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody);
	}

	public static class HbarTransfer {
		protected final long amount;
		protected final AccountID sender;
		protected final AccountID receiver;

		public HbarTransfer(long amount, AccountID sender, AccountID receiver) {
			this.amount = amount;
			this.sender = sender;
			this.receiver = receiver;
		}

		public AccountAmount senderAdjustment() {
			return AccountAmount.newBuilder().setAccountID(sender).setAmount(-amount).build();
		}

		public AccountAmount receiverAdjustment() {
			return AccountAmount.newBuilder().setAccountID(receiver).setAmount(+amount).build();
		}
	}

	public static class FungibleTokenTransfer extends HbarTransfer {
		private final TokenID denomination;

		public FungibleTokenTransfer(long amount, TokenID denomination, AccountID sender, AccountID receiver) {
			super(amount, sender, receiver);
			this.denomination = denomination;
		}

		public TokenID getDenomination() {
			return denomination;
		}
	}

	public static class NftExchange {
		private final long serialNo;
		private final TokenID tokenType;
		private final AccountID sender;
		private final AccountID receiver;

		public NftExchange(long serialNo, TokenID tokenType, AccountID sender, AccountID receiver) {
			this.serialNo = serialNo;
			this.tokenType = tokenType;
			this.sender = sender;
			this.receiver = receiver;
		}

		public NftTransfer asGrpc() {
			return NftTransfer.newBuilder()
					.setSenderAccountID(sender)
					.setReceiverAccountID(receiver)
					.setSerialNumber(serialNo)
					.build();
		}

		public TokenID getTokenType() {
			return tokenType;
		}
	}

	/**
	 * Merges the fungible and non-fungible transfers from one token transfer list into another. (Of course,
	 * at most one of these merges can be sensible; a token cannot be both fungible _and_ non-fungible.)
	 *
	 * Fungible transfers are "merged" by summing up all the amount fields for each unique account id that
	 * appears in either list.  NFT exchanges are "merged" by checking that each exchange from either list
	 * appears at most once.
	 *
	 * @param to
	 * 		the builder to merge source transfers into
	 * @param from
	 * 		a source of fungible transfers and NFT exchanges
	 * @return the consolidated target builder
	 */
	static TokenTransferList.Builder mergeTokenTransfers(
			final TokenTransferList.Builder to,
			final TokenTransferList.Builder from
	) {
		mergeFungible(from, to);
		mergeNonFungible(from, to);
		return to;
	}

	private static void mergeFungible(final TokenTransferList.Builder from, final TokenTransferList.Builder to) {
		for (int i = 0, n = from.getTransfersCount(); i < n; i++) {
			final var transfer = from.getTransfers(i);
			final var targetId = transfer.getAccountID();
			var merged = false;
			for (int j = 0, m = to.getTransfersCount(); j < m; j++) {
				final var transferBuilder = to.getTransfersBuilder(j);
				if (targetId.equals(transferBuilder.getAccountID())) {
					final var prevAmount = transferBuilder.getAmount();
					transferBuilder.setAmount(prevAmount + transfer.getAmount());
					merged = true;
					break;
				}
			}
			if (!merged) {
				to.addTransfers(transfer);
			}
		}
	}

	private static void mergeNonFungible(final TokenTransferList.Builder from, final TokenTransferList.Builder to) {
		for (int i = 0, n = from.getNftTransfersCount(); i < n; i++) {
			final var fromExchange = from.getNftTransfersBuilder(i);
			var alreadyPresent = false;
			for (int j = 0, m = to.getNftTransfersCount(); j < m; j++) {
				final var toExchange = to.getNftTransfersBuilder(j);
				if (areSameBuilder(fromExchange, toExchange)) {
					alreadyPresent = true;
					break;
				}
			}
			if (!alreadyPresent) {
				to.addNftTransfers(fromExchange);
			}
		}
	}

	static boolean areSameBuilder(final NftTransfer.Builder a, final NftTransfer.Builder b) {
		return a.getSerialNumber() == b.getSerialNumber()
				&& a.getSenderAccountID().equals(b.getSenderAccountID())
				&& a.getReceiverAccountID().equals(b.getReceiverAccountID());
	}
}
