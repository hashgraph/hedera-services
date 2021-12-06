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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.txns.crypto.AutoAccountCreateLogic.AUTO_CREATED_ACCOUNT_MEMO;
import static com.hedera.services.txns.crypto.AutoAccountCreateLogic.THREE_MONTHS_IN_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticTxnFactoryTest {
	private final SyntheticTxnFactory subject = new SyntheticTxnFactory();

	@Test
	void createsExpectedNftMint() {
		final var nftMints = new SyntheticTxnFactory.NftMint(nonFungible, newMetadata);

		final var result = subject.createNonFungibleMint(nftMints);
		final var txnBody = result.build();

		assertEquals(nonFungible, txnBody.getTokenMint().getToken());
		assertEquals(newMetadata, txnBody.getTokenMint().getMetadataList());
	}

	@Test
	void createsExpectedCryptoTransfer() {
		final var nftExchange = new SyntheticTxnFactory.NftExchange(serialNo, nonFungible, a, d);
		final var firstHbarTransfer = new SyntheticTxnFactory.HbarTransfer(firstAmount, a, b);
		final var secondHbarTransfer = new SyntheticTxnFactory.HbarTransfer(secondAmount, b, c);
		final var fungibleTransfer = new SyntheticTxnFactory.FungibleTokenTransfer(secondAmount, fungible, c, a);

		final var result = subject.createCryptoTransfer(
				List.of(nftExchange),
				List.of(firstHbarTransfer, secondHbarTransfer),
				List.of(fungibleTransfer));
		final var txnBody = result.build();

		final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers().getAccountAmountsList();
		assertEquals(List.of(
				firstHbarTransfer.senderAdjustment(),
				firstHbarTransfer.receiverAdjustment(),
				secondHbarTransfer.senderAdjustment(),
				secondHbarTransfer.receiverAdjustment()), hbarTransfers);

		final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
		final var expNftTransfer = tokenTransfers.get(0);
		assertEquals(nonFungible, expNftTransfer.getToken());
		assertEquals(List.of(nftExchange.nftTransfer()), expNftTransfer.getNftTransfersList());
		final var expFungibleTransfer = tokenTransfers.get(1);
		assertEquals(fungible, expFungibleTransfer.getToken());
		assertEquals(
				List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
				expFungibleTransfer.getTransfersList());
	}

	@Test
	void createsExpectedCryptoCreate() {
		final var balance = 10L;
		final var alias = KeyFactory.getDefaultInstance().newEd25519();
		final var result = subject.cryptoCreate(alias, balance);
		final var txnBody = result.build();

		assertTrue(txnBody.hasCryptoCreateAccount());
		assertEquals(AUTO_CREATED_ACCOUNT_MEMO, txnBody.getCryptoCreateAccount().getMemo());
		assertEquals(THREE_MONTHS_IN_SECONDS,
				txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
		assertEquals(10L,
				txnBody.getCryptoCreateAccount().getInitialBalance());
		assertEquals(alias.toByteString(),
				txnBody.getCryptoCreateAccount().getKey().toByteString());
	}

	private static final long serialNo = 100;
	private static final long firstAmount = 100;
	private static final long secondAmount = 200;
	private static final AccountID a = IdUtils.asAccount("0.0.2");
	private static final AccountID b = IdUtils.asAccount("0.0.3");
	private static final AccountID c = IdUtils.asAccount("0.0.4");
	private static final AccountID d = IdUtils.asAccount("0.0.5");
	private static final TokenID fungible = IdUtils.asToken("0.0.666");
	private static final TokenID nonFungible = IdUtils.asToken("0.0.777");
	private static final List<ByteString> newMetadata = List.of(
			ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
}
