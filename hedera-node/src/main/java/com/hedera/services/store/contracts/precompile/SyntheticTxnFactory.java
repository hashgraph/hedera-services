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
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static com.hedera.services.txns.crypto.TopLevelAutoCreation.AUTO_MEMO;
import static com.hedera.services.txns.crypto.TopLevelAutoCreation.THREE_MONTHS_IN_SECONDS;
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

	public TransactionBody.Builder createCryptoTransfer(
			final List<TokenTransferList> tokenTransferLists
	) {
		final var builder = CryptoTransferTransactionBody.newBuilder();

		for (final TokenTransferList tokenTransferList : tokenTransferLists) {
			for (final var nftExchange : tokenTransferList.getNftExchanges()) {
				builder.addTokenTransfers(com.hederahashgraph.api.proto.java.TokenTransferList.newBuilder()
						.setToken(nftExchange.getTokenType())
						.addNftTransfers(nftExchange.nftTransfer()));
			}
			for (final var fungibleTransfer : tokenTransferList.getFungibleTransfers()) {
				final var tokenTransferListBuilder = com.hederahashgraph.api.proto.java.TokenTransferList.newBuilder()
						.setToken(fungibleTransfer.getDenomination());

				if (fungibleTransfer.sender != null) {
					tokenTransferListBuilder.addTransfers(fungibleTransfer.senderAdjustment());
				}
				if (fungibleTransfer.receiver != null) {
					tokenTransferListBuilder.addTransfers(fungibleTransfer.receiverAdjustment());
				}
				builder.addTokenTransfers(tokenTransferListBuilder);
			}
		}
		return TransactionBody.newBuilder().setCryptoTransfer(builder);
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

		public NftTransfer nftTransfer() {
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

	public TransactionBody.Builder cryptoCreate(Key alias, long balance) {
		final var txnBody = CryptoCreateTransactionBody.newBuilder()
				.setKey(alias)
				.setMemo(AUTO_MEMO)
				.setInitialBalance(balance)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
				.build();

		return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody);
	}

	public static Dissociation multiDissociation(final AccountID accountId, final List<TokenID> tokenIds) {
		return new Dissociation(accountId, tokenIds);
	}


	public static class TokenTransferList {
		private final List<SyntheticTxnFactory.NftExchange> nftExchanges;
		private final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers;

		public TokenTransferList(final List<SyntheticTxnFactory.NftExchange> nftExchanges,
								 final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers) {
			this.nftExchanges = nftExchanges;
			this.fungibleTransfers = fungibleTransfers;
		}

		public List<SyntheticTxnFactory.NftExchange> getNftExchanges() {
			return nftExchanges;
		}

		public List<SyntheticTxnFactory.FungibleTokenTransfer> getFungibleTransfers() {
			return fungibleTransfers;
		}
	}
}