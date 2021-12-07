package com.hedera.services.store.contracts.precompile;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DecodingFacadeTest {
	private final DecodingFacade subject = new DecodingFacade();

	private static final Bytes CRYPTO_TRANSFER_INPUT = Bytes.fromHexString("0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004a4000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a1000000000000000000000000000000000000000000000000000000000000002b000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a100000000000000000000000000000000000000000000000000000000000004a10000000000000000000000000000000000000000000000000000000000000048");
	private static final Bytes FUNGIBLE_BURN_INPUT = Bytes.fromHexString(
			"0xacb9cff90000000000000000000000000000000000000000000000000000000000000498000000000000000000000000000000000000000000000000000000000000002100000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes NON_FUNGIBLE_BURN_INPUT = Bytes.fromHexString("0xacb9cff9000000000000000000000000000000000000000000000000000000000000049e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");
	private static final Bytes FUNGIBLE_MINT_INPUT = Bytes.fromHexString(
			"0x36dcedf0000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000f00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes NON_FUNGIBLE_MINT_INPUT = Bytes.fromHexString("0x36dcedf000000000000000000000000000000000000000000000000000000000000004320000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000114e4654206d657461646174612074657374000000000000000000000000000000");
	private static final Bytes TRANSFER_TOKEN_INPUT = Bytes.fromHexString("0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043a0000000000000000000000000000000000000000000000000000000000000014");
	private static final Bytes TRANSFER_TOKENS_INPUT = Bytes.fromHexString("0x82bba4930000000000000000000000000000000000000000000000000000000000000444000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000044100000000000000000000000000000000000000000000000000000000000004410000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014");
	private static final Bytes TRANSFER_NFT_INPUT = Bytes.fromHexString("0x5cfc901100000000000000000000000000000000000000000000000000000000000004680000000000000000000000000000000000000000000000000000000000000465000000000000000000000000000000000000000000000000000000000000046a0000000000000000000000000000000000000000000000000000000000000065");
	private static final Bytes TRANSFER_NFTS_INPUT = Bytes.fromHexString("0x2c4ba191000000000000000000000000000000000000000000000000000000000000047a000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047700000000000000000000000000000000000000000000000000000000000004770000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047c000000000000000000000000000000000000000000000000000000000000047c0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");
	private static final Bytes ASSOCIATE_INPUT = Bytes.fromHexString("0x49146bde00000000000000000000000000000000000000000000000000000000000004820000000000000000000000000000000000000000000000000000000000000480");
	private static final Bytes MULTIPLE_ASSOCIATE_INPUT = Bytes.fromHexString("0x2e63879b00000000000000000000000000000000000000000000000000000000000004880000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004860000000000000000000000000000000000000000000000000000000000000486");
	private static final Bytes DISSOCIATE_INPUT = Bytes.fromHexString("0x099794e8000000000000000000000000000000000000000000000000000000000000048e000000000000000000000000000000000000000000000000000000000000048c");
	private static final Bytes MULTIPLE_DISSOCIATE_INPUT = Bytes.fromHexString("0x78b6391800000000000000000000000000000000000000000000000000000000000004940000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004920000000000000000000000000000000000000000000000000000000000000492");

	@Test
	void decodeCryptoTransfer() {
		final var decodedInput = subject.decodeCryptoTransfer(CRYPTO_TRANSFER_INPUT);
		final var hbarTransfers = decodedInput.getHbarTransfers();
		final var fungibleTransfers = decodedInput.getFungibleTransfers();
		final var nftExchanges = decodedInput.getNftExchanges();

		assertNotNull(hbarTransfers);
		assertNotNull(fungibleTransfers);
		assertNotNull(nftExchanges);
		assertEquals(1, hbarTransfers.size());
		assertEquals(1, fungibleTransfers.size());
		assertEquals(1, nftExchanges.size());
		assertNotNull(fungibleTransfers.get(0).getDenomination().getTokenNum());
		assertNotNull(fungibleTransfers.get(0).receiver.getAccountNum());
		assertEquals(43, fungibleTransfers.get(0).receiverAdjustment().getAmount());
		assertNotNull(hbarTransfers.get(0).receiver.getAccountNum());
		assertEquals(43, hbarTransfers.get(0).receiverAdjustment().getAmount());
		assertNotNull(nftExchanges.get(0).getTokenType().getTokenNum());
		assertNotNull(nftExchanges.get(0).nftTransfer().getReceiverAccountID().getAccountNum());
		assertNotNull(nftExchanges.get(0).nftTransfer().getSenderAccountID().getAccountNum());
		assertEquals(72, nftExchanges.get(0).nftTransfer().getSerialNumber());
	}

	@Test
	void decodeFungibleBurnInput() {
		final var decodedInput = subject.decodeBurn(FUNGIBLE_BURN_INPUT);

		assertNotNull(decodedInput.getTokenType().getTokenNum());
		assertEquals(33, decodedInput.getAmount());
		assertEquals(0, decodedInput.getSerialNos().size());
	}

	@Test
	void decodeNonFungibleBurnInput() {
		final var decodedInput = subject.decodeBurn(NON_FUNGIBLE_BURN_INPUT);

		assertNotNull(decodedInput.getTokenType().getTokenNum());
		assertEquals(-1, decodedInput.getAmount());
		assertEquals(2, decodedInput.getSerialNos().size());
		assertEquals(123, decodedInput.getSerialNos().get(0));
		assertEquals(234, decodedInput.getSerialNos().get(1));
	}

	@Test
	void decodeFungibleMintInput() {
		final var decodedInput = subject.decodeMint(FUNGIBLE_MINT_INPUT);

		assertNotNull(decodedInput.getTokenType().getTokenNum());
		assertEquals(15l, decodedInput.getAmount());
	}

	@Test
	void decodeNonFungibleMintInput() {
		final var decodedInput = subject.decodeMint(NON_FUNGIBLE_MINT_INPUT);
		final var metadata = "NFT metadata test";
		final var metadataByteString = Collections.singletonList(ByteString.copyFrom(metadata.getBytes()));

		assertNotNull(decodedInput.getTokenType().getTokenNum());
		assertEquals(metadataByteString, decodedInput.getMetadata());
	}

	@Test
	void decodeTransferToken() {
		final var decodedInput = subject.decodeTransferToken(TRANSFER_TOKEN_INPUT);
		final var fungibleTransfer = decodedInput.getFungibleTransfers().get(0);

		assertNotNull(fungibleTransfer.sender.getAccountNum());
		assertNotNull(fungibleTransfer.receiver.getAccountNum());
		assertNotNull(fungibleTransfer.getDenomination().getTokenNum());
		assertEquals(20, fungibleTransfer.amount);
	}

	@Test
	void decodeTransferTokens() {
		final var decodedInput = subject.decodeTransferTokens(TRANSFER_TOKENS_INPUT);
		final var fungibleTransfers = decodedInput.getFungibleTransfers();

		assertEquals(2, fungibleTransfers.size());
		assertNotNull(fungibleTransfers.get(0).getDenomination().getTokenNum());
		assertNotNull(fungibleTransfers.get(1).getDenomination().getTokenNum());
		assertNull(fungibleTransfers.get(0).sender);
		assertNull(fungibleTransfers.get(1).sender);
		assertNotNull(fungibleTransfers.get(0).receiver.getAccountNum());
		assertNotNull(fungibleTransfers.get(1).receiver.getAccountNum());
		assertEquals(10, fungibleTransfers.get(0).amount);
		assertEquals(20, fungibleTransfers.get(1).amount);
	}

	@Test
	void decodeTransferNFT() {
		final var decodedInput = subject.decodeTransferNFT(TRANSFER_NFT_INPUT);
		final var nonFungibleTransfer = decodedInput.getNftExchanges().get(0);

		assertNotNull(nonFungibleTransfer.nftTransfer().getSenderAccountID().getAccountNum());
		assertNotNull(nonFungibleTransfer.nftTransfer().getReceiverAccountID().getAccountNum());
		assertNotNull(nonFungibleTransfer.getTokenType().getTokenNum());
		assertEquals(101, nonFungibleTransfer.nftTransfer().getSerialNumber());
	}

	@Test
	void decodeTransferNFTs() {
		final var decodedInput = subject.decodeTransferNFTs(TRANSFER_NFTS_INPUT);
		final var nonFungibleTransfers = decodedInput.getNftExchanges();

		assertEquals(2, nonFungibleTransfers.size());
		assertNotNull(nonFungibleTransfers.get(0).nftTransfer().getSenderAccountID().getAccountNum());
		assertNotNull(nonFungibleTransfers.get(1).nftTransfer().getSenderAccountID().getAccountNum());
		assertNotNull(nonFungibleTransfers.get(0).nftTransfer().getReceiverAccountID().getAccountNum());
		assertNotNull(nonFungibleTransfers.get(1).nftTransfer().getReceiverAccountID().getAccountNum());
		assertNotNull(nonFungibleTransfers.get(0).getTokenType().getTokenNum());
		assertNotNull(nonFungibleTransfers.get(1).getTokenType().getTokenNum());
		assertEquals(123, nonFungibleTransfers.get(0).nftTransfer().getSerialNumber());
		assertEquals(234, nonFungibleTransfers.get(1).nftTransfer().getSerialNumber());
	}

	@Test
	void decodeAssociateToken() {
		final var decodedInput = subject.decodeAssociation(ASSOCIATE_INPUT);

		assertNotNull(decodedInput.getAccountId().getAccountNum());
		assertNotNull(decodedInput.getTokenIds().get(0).getTokenNum());
	}

	@Test
	void decodeMultipleAssociateToken() {
		final var decodedInput = subject.decodeMultipleAssociations(MULTIPLE_ASSOCIATE_INPUT);

		assertNotNull(decodedInput.getAccountId().getAccountNum());
		assertEquals(2, decodedInput.getTokenIds().size());
		assertNotNull(decodedInput.getTokenIds().get(0).getTokenNum());
		assertNotNull(decodedInput.getTokenIds().get(1).getTokenNum());
	}

	@Test
	void decodeDissociateToken() {
		final var decodedInput = subject.decodeDissociate(DISSOCIATE_INPUT);

		assertNotNull(decodedInput.getAccountId().getAccountNum());
		assertNotNull(decodedInput.getTokenIds().get(0).getTokenNum());
	}

	@Test
	void decodeMultipleDissociateToken() {
		final var decodedInput = subject.decodeMultipleDissociations(MULTIPLE_DISSOCIATE_INPUT);

		assertNotNull(decodedInput.getAccountId().getAccountNum());
		assertEquals(2, decodedInput.getTokenIds().size());
		assertNotNull(decodedInput.getTokenIds().get(0).getTokenNum());
		assertNotNull(decodedInput.getTokenIds().get(1).getTokenNum());
	}
}