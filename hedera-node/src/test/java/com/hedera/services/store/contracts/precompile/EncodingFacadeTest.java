package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.utils.EntityIdUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.junit.jupiter.api.Test;

import javax.swing.text.html.parser.Entity;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingFacadeTest {
	private final EncodingFacade subject = new EncodingFacade();

	private static final Bytes RETURN_FUNGIBLE_MINT_FOR_10_TOKENS = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000016" +
					"0000000000000000000000000000000000000000000000000000000000" +
					"00000a0000000000000000000000000000000000000000000000000000" +
					"0000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000016" +
					"0000000000000000000000000000000000000000000000000000000000" +
					"0000020000000000000000000000000000000000000000000000000000" +
					"00000000006000000000000000000000000000000000000000000000000" +
					"00000000000000002000000000000000000000000000000000000000000" +
					"00000000000000000000010000000000000000000000000000000000000000000000000000000000000002");
	private static final Bytes RETURN_BURN_FOR_49_TOKENS = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000016" +
					"0000000000000000000000000000000000000000000000000000000000000031");
	private static final Bytes MINT_FAILURE_FROM_INVALID_TOKEN_ID = Bytes.fromHexString(
			"0x00000000000000000000000000000000000000000000000000000000000000a7" +
					"0000000000000000000000000000000000000000000000000000000000" +
					"0000000000000000000000000000000000000000000000000000000000" +
					"0000000000600000000000000000000000000000000000000000000000000000000000000000");
	private static final Bytes BURN_FAILURE_FROM_TREASURY_NOT_OWNER = Bytes.fromHexString(
			"0x00000000000000000000000000000000000000000000000000000000000000fc" +
					"0000000000000000000000000000000000000000000000000000000000000000");

	private static final Bytes RETURN_TOTAL_SUPPLY_FOR_50_TOKENS = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000032");

	private static final Bytes RETURN_DECIMALS_10 = Bytes.fromHexString(
			"0x000000000000000000000000000000000000000000000000000000000000000a");

	private static final Bytes RETURN_BALANCE_3 = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000003");

	private static final Bytes RETURN_TOKEN_URI_FIRST = Bytes.fromHexString(
			"0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000" +
					"000000000000000000000000054649525354000000000000000000000000000000000000000000000000000000");

	private static final Bytes RETURN_NAME_TOKENA = Bytes.fromHexString(
			"0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000" +
					"00000000000000000000006546f6b656e410000000000000000000000000000000000000000000000000000");

	private static final Bytes RETURN_SYMBOL_F = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000" +
					"00000000000000000000000014600000000000000000000000000000000000000000000000000000000000000");

	private static final Bytes RETURN_TRANSFER_TRUE = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000001");

	private static final Bytes RETURN_OWNER = Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000008");

    private static final Bytes TRANSFER_EVENT = Bytes.fromHexString("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");

	final Address logger = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);

	@Test
	void decodeReturnResultForFungibleMint() {
		final var decodedResult = subject.encodeMintSuccess(10, null);
		assertEquals(RETURN_FUNGIBLE_MINT_FOR_10_TOKENS, decodedResult);
	}

	@Test
	void decodeReturnResultForNonFungibleMint() {
		final var decodedResult = subject.encodeMintSuccess(2, new long[] { 1, 2 });
		assertEquals(RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS, decodedResult);
	}

	@Test
	void decodeReturnResultForBurn() {
		final var decodedResult = subject.encodeBurnSuccess(49);
		assertEquals(RETURN_BURN_FOR_49_TOKENS, decodedResult);
	}

	@Test
	void decodeReturnResultForTotalSupply() {
		final var decodedResult = subject.encodeTotalSupply(50);
		assertEquals(RETURN_TOTAL_SUPPLY_FOR_50_TOKENS, decodedResult);
	}

	@Test
	void decodeReturnResultForDecimals() {
		final var decodedResult = subject.encodeDecimals(10);
		assertEquals(RETURN_DECIMALS_10, decodedResult);
	}

	@Test
	void decodeReturnResultForBalance() {
		final var decodedResult = subject.encodeBalance(3);
		assertEquals(RETURN_BALANCE_3, decodedResult);
	}

	@Test
	void decodeReturnResultForTokenUri() {
		final var decodedResult = subject.encodeTokenUri("FIRST");
		assertEquals(RETURN_TOKEN_URI_FIRST, decodedResult);
	}

	@Test
	void decodeReturnResultForName() {
		final var decodedResult = subject.encodeName("TokenA");
		assertEquals(RETURN_NAME_TOKENA, decodedResult);
	}

	@Test
	void decodeReturnResultForSymbol() {
		final var decodedResult = subject.encodeSymbol("F");
		assertEquals(RETURN_SYMBOL_F, decodedResult);
	}

	@Test
	void decodeReturnResultForTransfer() {
		final var decodedResult = subject.encodeEcFungibleTransfer(true);
		assertEquals(RETURN_TRANSFER_TRUE, decodedResult);
	}

	@Test
	void decodeReturnResultForOwner() {
		final var decodedResult = subject.encodeOwner(senderAddress);
		assertEquals(RETURN_OWNER, decodedResult);
	}

	@Test
	void logBuilderWithTopics() {
		final var log = EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
				.forEventSignature(TRANSFER_EVENT)
				.forIndexedArgument(senderAddress)
				.forIndexedArgument(recipientAddress)
				.build();

		final List<LogTopic> topics = new ArrayList<>();
		topics.add(LogTopic.wrap(Bytes.fromHexString("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));
		topics.add(LogTopic.wrap(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000008")));
		topics.add(LogTopic.wrap(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000006")));

		assertEquals(new Log(logger, Bytes.EMPTY, topics), log);
	}

	@Test
	void logBuilderWithTopicsWithDifferentTypes() {
		final var log = EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
				.forEventSignature(TRANSFER_EVENT)
				.forIndexedArgument(senderAddress)
				.forIndexedArgument(20L)
				.forIndexedArgument(Boolean.TRUE)
				.build();

		final List<LogTopic> topics = new ArrayList<>();
		topics.add(LogTopic.wrap(Bytes.fromHexString("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));
		topics.add(LogTopic.wrap(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000008")));
		topics.add(LogTopic.wrap(Bytes.fromHexString(
				"0x0000000000000000000000000000000000000000000000000000000000000014")));
		topics.add(LogTopic.wrap(Bytes.fromHexString(
				"0x0000000000000000000000000000000000000000000000000000000000000001")));

		assertEquals(new Log(logger, Bytes.EMPTY, topics), log);
	}

	@Test
	void logBuilderWithData() {
		final var tupleType = TupleType.parse("(address,uint256)");
		final var log = EncodingFacade.LogBuilder.logBuilder().forLogger(logger)
				.forEventSignature(TRANSFER_EVENT)
				.forDataItem(senderAddress)
				.forDataItem(9L)
				.build();


		final var dataItems = new ArrayList<>();
		dataItems.add(convertBesuAddressToHeadlongAddress(senderAddress));
		dataItems.add(BigInteger.valueOf(9));
		final var tuple = Tuple.of(dataItems.toArray());

		final List<LogTopic> topics = new ArrayList<>();
		topics.add(LogTopic.wrap(Bytes.fromHexString("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));

		assertEquals(new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), topics), log);
	}

	@Test
	void createsExpectedMintFailureResult() {
		assertEquals(MINT_FAILURE_FROM_INVALID_TOKEN_ID, subject.encodeMintFailure(INVALID_TOKEN_ID));
	}

	@Test
	void createsExpectedBurnFailureResult() {
		assertEquals(BURN_FAILURE_FROM_TREASURY_NOT_OWNER, subject.encodeBurnFailure(TREASURY_MUST_OWN_BURNED_NFT));
	}

	private com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(final Address addressToBeConverted) {
		return com.esaulpaugh.headlong.abi.Address.wrap(com.esaulpaugh.headlong.abi.Address.toChecksumAddress(addressToBeConverted.toBigInteger()));
	}
}