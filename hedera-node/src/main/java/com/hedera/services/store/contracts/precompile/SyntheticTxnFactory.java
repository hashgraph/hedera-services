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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class SyntheticTxnFactory {
	@Inject
	public SyntheticTxnFactory() {
	}

	public TransactionBody.Builder createBurn(final BurnWrapper burnWrapper) {
		final var builder = TokenBurnTransactionBody.newBuilder();

		builder.setToken(burnWrapper.getTokenType());
		if (burnWrapper.type() == NON_FUNGIBLE_UNIQUE) {
			builder.addAllSerialNumbers(burnWrapper.getSerialNos());
		} else {
			builder.setAmount(burnWrapper.getAmount());
		}

		return TransactionBody.newBuilder().setTokenBurn(builder);
	}

	public TransactionBody.Builder createMint(final MintWrapper mintWrapper) {
		final var builder = TokenMintTransactionBody.newBuilder();

		builder.setToken(mintWrapper.getTokenType());
		if (mintWrapper.type() == NON_FUNGIBLE_UNIQUE) {
			builder.addAllMetadata(mintWrapper.getMetadata());
		} else {
			builder.setAmount(mintWrapper.getAmount());
		}

		return TransactionBody.newBuilder().setTokenMint(builder);
	}

	public TransactionBody.Builder createCryptoTransfer(
			final List<NftExchange> nftExchanges,
			final List<FungibleTokenTransfer> fungibleTransfers
	) {
		final var builder = CryptoTransferTransactionBody.newBuilder();

		for (final var nftExchange : nftExchanges) {
			builder.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(nftExchange.getTokenType())
					.addNftTransfers(nftExchange.nftTransfer()));
		}
		for (final var fungibleTransfer : fungibleTransfers) {
			final var tokenTransferList = TokenTransferList.newBuilder()
					.setToken(fungibleTransfer.getDenomination());

			if (fungibleTransfer.sender != null) {
				tokenTransferList.addTransfers(fungibleTransfer.senderAdjustment());
			}
			if (fungibleTransfer.receiver != null) {
				tokenTransferList.addTransfers(fungibleTransfer.receiverAdjustment());
			}
			builder.addTokenTransfers(tokenTransferList);
		}

		return TransactionBody.newBuilder().setCryptoTransfer(builder);
	}

	public TransactionBody.Builder createAssociate(final Association association) {
		final var builder = TokenAssociateTransactionBody.newBuilder();

		builder.setAccount(association.getAccountId());
		builder.addAllTokens(association.getTokenIds());

		return TransactionBody.newBuilder().setTokenAssociate(builder);
	}

	public TransactionBody.Builder createDissociate(final Dissociation dissociation) {
		final var builder = TokenDissociateTransactionBody.newBuilder();

		builder.setAccount(dissociation.getAccountId());
		builder.addAllTokens(dissociation.getTokenIds());

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

	public static class MintWrapper {
		private static final long NONFUNGIBLE_MINT_AMOUNT = -1;
		private static final List<ByteString> FUNGIBLE_MINT_METADATA = Collections.emptyList();

		private final long amount;
		private final TokenID tokenType;
		private final List<ByteString> metadata;

		public static MintWrapper forNonFungible(final TokenID tokenType, final List<ByteString> metadata) {
			return new MintWrapper(NONFUNGIBLE_MINT_AMOUNT, tokenType, metadata);
		}

		public static MintWrapper forFungible(final TokenID tokenType, final long amount) {
			return new MintWrapper(amount, tokenType, FUNGIBLE_MINT_METADATA);
		}

		public MintWrapper(long amount, TokenID tokenType, List<ByteString> metadata) {
			this.tokenType = tokenType;
			this.amount = amount;
			this.metadata = metadata;
		}

		public TokenType type() {
			return (amount == NONFUNGIBLE_MINT_AMOUNT) ? NON_FUNGIBLE_UNIQUE : FUNGIBLE_COMMON;
		}

		public TokenID getTokenType() {
			return tokenType;
		}

		public List<ByteString> getMetadata() {
			return metadata;
		}

		public long getAmount() {
			return amount;
		}
	}

	public static class BurnWrapper {
		private static final long NONFUNGIBLE_BURN_AMOUNT = -1;
		private static final List<Long> FUNGIBLE_BURN_SERIAL_NOS = Collections.emptyList();

		private final long amount;
		private final TokenID tokenType;
		private final List<Long> serialNos;

		public static BurnWrapper forNonFungible(final TokenID tokenType, final List<Long> serialNos) {
			return new BurnWrapper(NONFUNGIBLE_BURN_AMOUNT, tokenType, serialNos);
		}

		public static BurnWrapper forFungible(final TokenID tokenType, final long amount) {
			return new BurnWrapper(amount, tokenType, FUNGIBLE_BURN_SERIAL_NOS);
		}

		public BurnWrapper(long amount, TokenID tokenType, List<Long> serialNos) {
			this.amount = amount;
			this.tokenType = tokenType;
			this.serialNos = serialNos;
		}

		public TokenType type() {
			return (amount == NONFUNGIBLE_BURN_AMOUNT) ? NON_FUNGIBLE_UNIQUE : FUNGIBLE_COMMON;
		}

		public long getAmount() {
			return amount;
		}

		public TokenID getTokenType() {
			return tokenType;
		}

		public List<Long> getSerialNos() {
			return serialNos;
		}
	}

	public static class TokenRelChange {
		private final AccountID accountId;
		private final List<TokenID> tokenIds;

		TokenRelChange(final AccountID accountId, final List<TokenID> tokenIds) {
			this.tokenIds = tokenIds;
			this.accountId = accountId;
		}

		public AccountID getAccountId() {
			return accountId;
		}

		public List<TokenID> getTokenIds() {
			return tokenIds;
		}
	}

	public static class Association extends TokenRelChange {
		private Association(final AccountID accountId, final List<TokenID> tokenIds) {
			super(accountId, tokenIds);
		}
		public static Association singleAssociation(final AccountID accountId, final TokenID tokenId) {
			return new Association(accountId, List.of(tokenId));
		}

		public static Association multiAssociation(final AccountID accountId, final List<TokenID> tokenIds) {
			return new Association(accountId, tokenIds);
		}
	}

	public static class Dissociation extends TokenRelChange {
		private Dissociation(final AccountID accountId, final List<TokenID> tokenIds) {
			super(accountId, tokenIds);
		}

		public static Dissociation singleDissociation(final AccountID accountId, final TokenID tokenId) {
			return new Dissociation(accountId, List.of(tokenId));
		}

		public static Dissociation multiDissociation(final AccountID accountId, final List<TokenID> tokenIds) {
			return new Dissociation(accountId, tokenIds);
		}
	}

	public static class TokenTransferLists {
		private final List<NftExchange> nftExchanges;
		private final List<FungibleTokenTransfer> fungibleTransfers;

		public TokenTransferLists(final List<NftExchange> nftExchanges,
								  final List<FungibleTokenTransfer> fungibleTransfers) {
			this.nftExchanges = nftExchanges;
			this.fungibleTransfers = fungibleTransfers;
		}

		public List<NftExchange> getNftExchanges() {
			return nftExchanges;
		}

		public List<FungibleTokenTransfer> getFungibleTransfers() {
			return fungibleTransfers;
		}
	}
}