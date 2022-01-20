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
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.hedera.services.txns.crypto.AutoCreationLogic.AUTO_MEMO;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticTxnFactoryTest {
	private final SyntheticTxnFactory subject = new SyntheticTxnFactory();

	@Test
	void createsExpectedCryptoCreate() {
		final var balance = 10L;
		final var alias = KeyFactory.getDefaultInstance().newEd25519();
		final var result = subject.createAccount(alias, balance);
		final var txnBody = result.build();

		assertTrue(txnBody.hasCryptoCreateAccount());
		assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
		assertEquals(THREE_MONTHS_IN_SECONDS,
				txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
		assertEquals(10L,
				txnBody.getCryptoCreateAccount().getInitialBalance());
		assertEquals(alias.toByteString(),
				txnBody.getCryptoCreateAccount().getKey().toByteString());
	}

	@Test
	void createsExpectedAssociations() {
		final var tokens = List.of(fungible, nonFungible);
		final var associations = Association.multiAssociation(a, tokens);

		final var result = subject.createAssociate(associations);
		final var txnBody = result.build();

		assertEquals(a, txnBody.getTokenAssociate().getAccount());
		assertEquals(tokens, txnBody.getTokenAssociate().getTokensList());
	}

	@Test
	void createsExpectedDissociations() {
		final var tokens = List.of(fungible, nonFungible);
		final var associations = Dissociation.multiDissociation(a, tokens);

		final var result = subject.createDissociate(associations);
		final var txnBody = result.build();

		assertEquals(a, txnBody.getTokenDissociate().getAccount());
		assertEquals(tokens, txnBody.getTokenDissociate().getTokensList());
	}

	@Test
	void createsExpectedNftMint() {
		final var nftMints = MintWrapper.forNonFungible(nonFungible, newMetadata);

		final var result = subject.createMint(nftMints);
		final var txnBody = result.build();

		assertEquals(nonFungible, txnBody.getTokenMint().getToken());
		assertEquals(newMetadata, txnBody.getTokenMint().getMetadataList());
	}

	@Test
	void createsExpectedNftBurn() {
		final var nftBurns = BurnWrapper.forNonFungible(nonFungible, targetSerialNos);

		final var result = subject.createBurn(nftBurns);
		final var txnBody = result.build();

		assertEquals(nonFungible, txnBody.getTokenBurn().getToken());
		assertEquals(targetSerialNos, txnBody.getTokenBurn().getSerialNumbersList());
	}

	@Test
	void createsExpectedFungibleMint() {
		final var amount = 1234L;
		final var funMints = MintWrapper.forFungible(fungible, amount);

		final var result = subject.createMint(funMints);
		final var txnBody = result.build();

		assertEquals(fungible, txnBody.getTokenMint().getToken());
		assertEquals(amount, txnBody.getTokenMint().getAmount());
	}

	@Test
	void createsExpectedFungibleBurn() {
		final var amount = 1234L;
		final var funBurns = BurnWrapper.forFungible(fungible, amount);

		final var result = subject.createBurn(funBurns);
		final var txnBody = result.build();

		assertEquals(fungible, txnBody.getTokenBurn().getToken());
		assertEquals(amount, txnBody.getTokenBurn().getAmount());
	}

	@Test
	void createsExpectedCryptoTransfer() {
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, fungible, b, a);

		final var result = subject.createCryptoTransfer(
				List.of(new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer))));
		final var txnBody = result.build();

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		final var expFungibleTransfer = tokenTransfers.get(0);
		assertEquals(fungible, expFungibleTransfer.getToken());
		assertEquals(
				List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
				expFungibleTransfer.getTransfersList());
	}

	@Test
	void acceptsEmptyWrappers() {
		final var result = subject.createCryptoTransfer(List.of());

		final var txnBody = result.build();
		assertEquals(0, txnBody.getCryptoTransfer().getTokenTransfersCount());
	}

	@Test
	void mergesRepeatedTokenIds() {
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, fungible, b, a);
		final var nonFungibleTransfer = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b);

		final var result = subject.createCryptoTransfer(
				List.of(
						new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
						new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
						new TokenTransferWrapper(List.of(nonFungibleTransfer), Collections.emptyList())));

		final var txnBody = result.build();

		final var finalTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		assertEquals(2, finalTransfers.size());
		final var mergedFungible = finalTransfers.get(0);
		assertEquals(fungible, mergedFungible.getToken());
		assertEquals(
				List.of(
						aaWith(b, -2 * secondAmount),
						aaWith(a, +2 * secondAmount)),
				mergedFungible.getTransfersList());
	}

	@Test
	void createsExpectedCryptoTransferForNFTTransfer() {
		final var nftExchange = new SyntheticTxnFactory.NftExchange(serialNo, nonFungible, a, c);

		final var result = subject.createCryptoTransfer(Collections.singletonList(new TokenTransferWrapper(
				List.of(nftExchange),
				Collections.emptyList())));
		final var txnBody = result.build();

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		final var expNftTransfer = tokenTransfers.get(0);
		assertEquals(nonFungible, expNftTransfer.getToken());
		assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
		assertEquals(1, tokenTransfers.size());
	}

	@Test
	void createsExpectedCryptoTransferForFungibleTransfer() {
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, fungible, b, a);

		final var result = subject.createCryptoTransfer(Collections.singletonList(new TokenTransferWrapper(
				Collections.emptyList(),
				List.of(fungibleTransfer))));
		final var txnBody = result.build();

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		final var expFungibleTransfer = tokenTransfers.get(0);
		assertEquals(fungible, expFungibleTransfer.getToken());
		assertEquals(
				List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
				expFungibleTransfer.getTransfersList());
		assertEquals(1, tokenTransfers.size());
	}

	@Test
	void createsExpectedCryptoTransfersForMultipleTransferWrappers() {
		final var nftExchange = new SyntheticTxnFactory.NftExchange(serialNo, nonFungible, a, c);
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, fungible, b, a);

		final var result = subject.createCryptoTransfer(
				List.of(
						new TokenTransferWrapper(
								Collections.emptyList(),
								List.of(fungibleTransfer)),
						new TokenTransferWrapper(
								List.of(nftExchange),
								Collections.emptyList())));
		final var txnBody = result.build();

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();

		final var expFungibleTransfer = tokenTransfers.get(0);
		assertEquals(fungible, expFungibleTransfer.getToken());
		assertEquals(
				List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
				expFungibleTransfer.getTransfersList());

		final var expNftTransfer = tokenTransfers.get(1);
		assertEquals(nonFungible, expNftTransfer.getToken());
		assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
	}

	@Test
	void mergesFungibleTransfersAsExpected() {
		final var source = new TokenTransferWrapper(
				Collections.emptyList(),
				List.of(
						new SyntheticTxnFactory.FungibleTokenTransfer(1, fungible, a, b)
				)).asGrpcBuilder();
		final var target = new TokenTransferWrapper(
				Collections.emptyList(),
				List.of(
						new SyntheticTxnFactory.FungibleTokenTransfer(2, fungible, b, c)
				)).asGrpcBuilder();

		SyntheticTxnFactory.mergeTokenTransfers(target, source);

		assertEquals(fungible, target.getToken());
		final var transfers = target.getTransfersList();
		assertEquals(List.of(
				aaWith(b, -1),
				aaWith(c, +2),
				aaWith(a, -1)
		), transfers);
	}

	@Test
	void mergesNftExchangesAsExpected() {
		final var repeatedExchange = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b);
		final var newExchange = new SyntheticTxnFactory.NftExchange(2L, nonFungible, a, b);
		final var source = new TokenTransferWrapper(
				List.of(repeatedExchange, newExchange),
				Collections.emptyList()
		).asGrpcBuilder();
		final var target = new TokenTransferWrapper(
				List.of(repeatedExchange),
				Collections.emptyList()
		).asGrpcBuilder();

		SyntheticTxnFactory.mergeTokenTransfers(target, source);

		assertEquals(nonFungible, target.getToken());
		final var transfers = target.getNftTransfersList();
		assertEquals(List.of(
				repeatedExchange.asGrpc(),
				newExchange.asGrpc()
		), transfers);
	}

	@Test
	void distinguishesDifferentExchangeBuilders() {
		final var subject = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, b)
				.asGrpc().toBuilder();

		final var differentSerialNo = new SyntheticTxnFactory.NftExchange(2L, nonFungible, a, b);
		final var differentSender = new SyntheticTxnFactory.NftExchange(1L, nonFungible, c, b);
		final var differentReceiver = new SyntheticTxnFactory.NftExchange(1L, nonFungible, a, c);

		assertFalse(SyntheticTxnFactory.areSameBuilder(subject, differentSerialNo.asGrpc().toBuilder()));
		assertFalse(SyntheticTxnFactory.areSameBuilder(subject, differentReceiver.asGrpc().toBuilder()));
		assertFalse(SyntheticTxnFactory.areSameBuilder(subject, differentSender.asGrpc().toBuilder()));
	}

	private AccountAmount aaWith(final AccountID account, final long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(account)
				.setAmount(amount)
				.build();
	}

	private static final long serialNo = 100;
	private static final long secondAmount = 200;
	private static final AccountID a = IdUtils.asAccount("0.0.2");
	private static final AccountID b = IdUtils.asAccount("0.0.3");
	private static final AccountID c = IdUtils.asAccount("0.0.4");
	private static final TokenID fungible = IdUtils.asToken("0.0.555");
	private static final TokenID nonFungible = IdUtils.asToken("0.0.666");
	private static final List<Long> targetSerialNos = List.of(1L, 2L, 3L);
	private static final List<ByteString> newMetadata = List.of(
			ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
}
