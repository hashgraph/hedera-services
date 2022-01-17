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
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecodingFacadeTest {
	private final DecodingFacade subject = new DecodingFacade();

	private static final Bytes POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT = Bytes.fromHexString(
			"0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004a4000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a1000000000000000000000000000000000000000000000000000000000000002b000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a100000000000000000000000000000000000000000000000000000000000004a10000000000000000000000000000000000000000000000000000000000000048");
	private static final Bytes NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT = Bytes.fromHexString(
			"0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004c0000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004bdffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffce0000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes FUNGIBLE_BURN_INPUT = Bytes.fromHexString(
			"0xacb9cff90000000000000000000000000000000000000000000000000000000000000498000000000000000000000000000000000000000000000000000000000000002100000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes NON_FUNGIBLE_BURN_INPUT = Bytes.fromHexString(
			"0xacb9cff9000000000000000000000000000000000000000000000000000000000000049e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");
	private static final Bytes FUNGIBLE_MINT_INPUT = Bytes.fromHexString(
			"0x278e0b88000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000f00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes NON_FUNGIBLE_MINT_INPUT = Bytes.fromHexString(
			"0x278e0b88000000000000000000000000000000000000000000000000000000000000042e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000124e4654206d65746164617461207465737431000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000124e4654206d657461646174612074657374320000000000000000000000000000");
	private static final Bytes TRANSFER_TOKEN_INPUT = Bytes.fromHexString(
			"0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043a0000000000000000000000000000000000000000000000000000000000000014");
	private static final Bytes POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT = Bytes.fromHexString(
			"0x82bba4930000000000000000000000000000000000000000000000000000000000000444000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000044100000000000000000000000000000000000000000000000000000000000004410000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014");
	private static final Bytes POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT = Bytes.fromHexString(
			"0x82bba49300000000000000000000000000000000000000000000000000000000000004d8000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000014ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec");
	private static final Bytes TRANSFER_NFT_INPUT = Bytes.fromHexString(
			"0x5cfc901100000000000000000000000000000000000000000000000000000000000004680000000000000000000000000000000000000000000000000000000000000465000000000000000000000000000000000000000000000000000000000000046a0000000000000000000000000000000000000000000000000000000000000065");
	private static final Bytes TRANSFER_NFTS_INPUT = Bytes.fromHexString(
			"0x2c4ba191000000000000000000000000000000000000000000000000000000000000047a000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047700000000000000000000000000000000000000000000000000000000000004770000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047c000000000000000000000000000000000000000000000000000000000000047c0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");
	private static final Bytes ASSOCIATE_INPUT = Bytes.fromHexString(
			"0x49146bde00000000000000000000000000000000000000000000000000000000000004820000000000000000000000000000000000000000000000000000000000000480");
	private static final Bytes MULTIPLE_ASSOCIATE_INPUT = Bytes.fromHexString(
			"0x2e63879b00000000000000000000000000000000000000000000000000000000000004880000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004860000000000000000000000000000000000000000000000000000000000000486");
	private static final Bytes DISSOCIATE_INPUT = Bytes.fromHexString(
			"0x099794e8000000000000000000000000000000000000000000000000000000000000048e000000000000000000000000000000000000000000000000000000000000048c");
	private static final Bytes MULTIPLE_DISSOCIATE_INPUT = Bytes.fromHexString(
			"0x78b6391800000000000000000000000000000000000000000000000000000000000004940000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004920000000000000000000000000000000000000000000000000000000000000492");
	private static final Bytes INVALID_INPUT = Bytes.fromHexString("0x00000000");

	@Test
	void decodeCryptoTransferPositiveFungibleAmountAndNftTransfer() {
		final var decodedInput =
				subject.decodeCryptoTransfer(POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT);
		final var fungibleTransfers = decodedInput.get(0).fungibleTransfers();
		final var nftExchanges = decodedInput.get(0).nftExchanges();

		assertNotNull(fungibleTransfers);
		assertNotNull(nftExchanges);
		assertEquals(1, fungibleTransfers.size());
		assertEquals(1, nftExchanges.size());
		assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
		assertTrue(fungibleTransfers.get(0).receiver.getAccountNum() > 0);
		assertEquals(43, fungibleTransfers.get(0).receiverAdjustment().getAmount());
		assertTrue(nftExchanges.get(0).getTokenType().getTokenNum() > 0);
		assertTrue(nftExchanges.get(0).asGrpc().getReceiverAccountID().getAccountNum() > 0);
		assertTrue(nftExchanges.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
		assertEquals(72, nftExchanges.get(0).asGrpc().getSerialNumber());
	}

	@Test
	void decodeCryptoTransferNegativeFungibleAmount() {
		final var decodedInput = subject.decodeCryptoTransfer(NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT);
		final var fungibleTransfers = decodedInput.get(0).fungibleTransfers();

		assertNotNull(fungibleTransfers);
		assertEquals(1, fungibleTransfers.size());
		assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
		assertTrue(fungibleTransfers.get(0).sender.getAccountNum() > 0);
		assertEquals(50, fungibleTransfers.get(0).amount);
	}

	@Test
	void decodeFungibleBurnInput() {
		final var decodedInput = subject.decodeBurn(FUNGIBLE_BURN_INPUT);

		assertTrue(decodedInput.tokenType().getTokenNum() > 0);
		assertEquals(33, decodedInput.amount());
		assertEquals(0, decodedInput.serialNos().size());
	}

	@Test
	void decodeNonFungibleBurnInput() {
		final var decodedInput = subject.decodeBurn(NON_FUNGIBLE_BURN_INPUT);

		assertTrue(decodedInput.tokenType().getTokenNum() > 0);
		assertEquals(-1, decodedInput.amount());
		assertEquals(2, decodedInput.serialNos().size());
		assertEquals(123, decodedInput.serialNos().get(0));
		assertEquals(234, decodedInput.serialNos().get(1));
	}

	@Test
	void decodeFungibleMintInput() {
		final var decodedInput = subject.decodeMint(FUNGIBLE_MINT_INPUT);

		assertTrue(decodedInput.tokenType().getTokenNum() > 0);
		assertEquals(15, decodedInput.amount());
	}

	@Test
	void decodeNonFungibleMintInput() {
		final var decodedInput = subject.decodeMint(NON_FUNGIBLE_MINT_INPUT);
		final var metadata1 = ByteString.copyFrom("NFT metadata test1".getBytes());
		final var metadata2 = ByteString.copyFrom("NFT metadata test2".getBytes());
		final List<ByteString> metadata = Arrays.asList(metadata1, metadata2);

		assertTrue(decodedInput.tokenType().getTokenNum() > 0);
		assertEquals(metadata, decodedInput.metadata());
	}

	@Test
	void decodeTransferToken() {
		final var decodedInput = subject.decodeTransferToken(TRANSFER_TOKEN_INPUT);
		final var fungibleTransfer = decodedInput.get(0).fungibleTransfers().get(0);

		assertTrue(fungibleTransfer.sender.getAccountNum() > 0);
		assertTrue(fungibleTransfer.receiver.getAccountNum() > 0);
		assertTrue(fungibleTransfer.getDenomination().getTokenNum() > 0);
		assertEquals(20, fungibleTransfer.amount);
	}

	@Test
	void decodeTransferTokensPositiveAmounts() {
		final var decodedInput = subject.decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT);
		final var fungibleTransfers = decodedInput.get(0).fungibleTransfers();

		assertEquals(2, fungibleTransfers.size());
		assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
		assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
		assertNull(fungibleTransfers.get(0).sender);
		assertNull(fungibleTransfers.get(1).sender);
		assertTrue(fungibleTransfers.get(0).receiver.getAccountNum() > 0);
		assertTrue(fungibleTransfers.get(1).receiver.getAccountNum() > 0);
		assertEquals(10, fungibleTransfers.get(0).amount);
		assertEquals(20, fungibleTransfers.get(1).amount);
	}

	@Test
	void decodeTransferTokensPositiveNegativeAmount() {
		final var decodedInput = subject.decodeTransferTokens(POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT);
		final var fungibleTransfers = decodedInput.get(0).fungibleTransfers();

		assertEquals(2, fungibleTransfers.size());
		assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
		assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
		assertNull(fungibleTransfers.get(0).sender);
		assertNull(fungibleTransfers.get(1).receiver);
		assertTrue(fungibleTransfers.get(0).receiver.getAccountNum() > 0);
		assertTrue(fungibleTransfers.get(1).sender.getAccountNum() > 0);
		assertEquals(20, fungibleTransfers.get(0).amount);
		assertEquals(20, fungibleTransfers.get(1).amount);
	}

	@Test
	void decodeTransferNFT() {
		final var decodedInput = subject.decodeTransferNFT(TRANSFER_NFT_INPUT);
		final var nonFungibleTransfer = decodedInput.get(0).nftExchanges().get(0);

		assertTrue(nonFungibleTransfer.asGrpc().getSenderAccountID().getAccountNum() > 0);
		assertTrue(nonFungibleTransfer.asGrpc().getReceiverAccountID().getAccountNum() > 0);
		assertTrue(nonFungibleTransfer.getTokenType().getTokenNum() > 0);
		assertEquals(101, nonFungibleTransfer.asGrpc().getSerialNumber());
	}

	@Test
	void decodeTransferNFTs() {
		final var decodedInput = subject.decodeTransferNFTs(TRANSFER_NFTS_INPUT);
		final var nonFungibleTransfers = decodedInput.get(0).nftExchanges();

		assertEquals(2, nonFungibleTransfers.size());
		assertTrue(nonFungibleTransfers.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
		assertTrue(nonFungibleTransfers.get(1).asGrpc().getSenderAccountID().getAccountNum() > 0);
		assertTrue(nonFungibleTransfers.get(0).asGrpc().getReceiverAccountID().getAccountNum() > 0);
		assertTrue(nonFungibleTransfers.get(1).asGrpc().getReceiverAccountID().getAccountNum() > 0);
		assertTrue(nonFungibleTransfers.get(0).getTokenType().getTokenNum() > 0);
		assertTrue(nonFungibleTransfers.get(1).getTokenType().getTokenNum() > 0);
		assertEquals(123, nonFungibleTransfers.get(0).asGrpc().getSerialNumber());
		assertEquals(234, nonFungibleTransfers.get(1).asGrpc().getSerialNumber());
	}

	@Test
	void decodeAssociateToken() {
		final var decodedInput = subject.decodeAssociation(ASSOCIATE_INPUT);

		assertTrue(decodedInput.accountId().getAccountNum() > 0);
		assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
	}

	@Test
	void decodeMultipleAssociateToken() {
		final var decodedInput = subject.decodeMultipleAssociations(MULTIPLE_ASSOCIATE_INPUT);

		assertTrue(decodedInput.accountId().getAccountNum() > 0);
		assertEquals(2, decodedInput.tokenIds().size());
		assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
		assertTrue(decodedInput.tokenIds().get(1).getTokenNum() > 0);
	}

	@Test
	void decodeDissociateToken() {
		final var decodedInput = subject.decodeDissociate(DISSOCIATE_INPUT);

		assertTrue(decodedInput.accountId().getAccountNum() > 0);
		assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
	}

	@Test
	void decodeMultipleDissociateToken() {
		final var decodedInput = subject.decodeMultipleDissociations(MULTIPLE_DISSOCIATE_INPUT);

		assertTrue(decodedInput.accountId().getAccountNum() > 0);
		assertEquals(2, decodedInput.tokenIds().size());
		assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
		assertTrue(decodedInput.tokenIds().get(1).getTokenNum() > 0);
	}

	@Test
	void decodeMultipleDissociateTokenInvalidInput() {
		assertThrows(IllegalArgumentException.class,
				() -> subject.decodeMultipleDissociations(INVALID_INPUT));
	}
}
