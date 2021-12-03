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

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class DecodingFacade {
	private static final int ADDRESS_BYTES_LENGTH = 20;
	private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;
	private static final String BYTES_32_FIRST_RETURN_TYPE = "(bytes32,";

	private static final Function CRYPTO_TRANSFER_FUNCTION = new Function("cryptoTransfer((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])",
			"((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])");
	private static final Function TRANSFER_TOKENS_FUNCTION = new Function("transferTokens(address,address[],int64[])",
			"(bytes32,bytes32[],int64[])");
	private static final Function TRANSFER_TOKEN_FUNCTION = new Function("transferToken(address,address,address,int64)",
			"(bytes32,bytes32,bytes32,int64)");
	private static final Function TRANSFER_NFTS_FUNCTION = new Function("transferNFTs(address,address[],address[],int64[])",
			"(bytes32,bytes32[],bytes32[],int64[])");
	private static final Function TRANSFER_NFT_FUNCTION = new Function("transferNFT(address,address,address,int64)",
			"(bytes32,bytes32,bytes32,int64)");
	private static final Function MINT_TOKEN_FUNCTION = new Function("mintToken(address,uint64,bytes)",
			"(bytes32,int64,string)");
	private static final Function BURN_TOKEN_FUNCTION = new Function("burnToken(address,uint64,int64[])",
			"(bytes32,int64,int64[])");
	private static final Function ASSOCIATE_TOKENS_FUNCTION = new Function("associateTokens(address,address[])",
			"(bytes32,bytes32[])");
	private static final Function ASSOCIATE_TOKEN_FUNCTION = new Function("associateToken(address,address)",
			"(bytes32,bytes32)");
	private static final Function DISSOCIATE_TOKENS_FUNCTION = new Function("dissociateTokens(address,address[])",
			"(bytes32,bytes32[])");
	private static final Function DISSOCIATE_TOKEN_FUNCTION = new Function("associateToken(address,address)",
			"(bytes32,bytes32)");

	@Inject
	public DecodingFacade() {
	}

	@SuppressWarnings("unused")
	public static void decodeCryptoTransfer(final Bytes input) {
		final Tuple decodedTuples =
				CRYPTO_TRANSFER_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var builder = CryptoTransferTransactionBody.newBuilder();
		for(final var tuple: decodedTuples) {
			for(final var tupleNested: (Tuple[]) tuple) {
				final var tokenTransferBuilder = TokenTransferList.newBuilder();

				final var transfers = (Tuple[]) tupleNested.get(1);
				for(final var transfer: transfers) {
					final var accountAmountBuilder = AccountAmount.newBuilder();
					accountAmountBuilder.setAccountID(convertAddressBytesToAccountID((byte[]) transfer.get(0)));
					accountAmountBuilder.setAmount((long) transfer.get(1));
					tokenTransferBuilder.addTransfers(accountAmountBuilder.build());
				}

				final var nftTransfersDecoded = (Tuple[]) tupleNested.get(2);
				for(final var nftTransferDecoded: nftTransfersDecoded) {
					final var nftTransferBuilder = NftTransfer.newBuilder();
					nftTransferBuilder.setSenderAccountID(convertAddressBytesToAccountID((byte[]) nftTransferDecoded.get(0)));
					nftTransferBuilder.setReceiverAccountID(convertAddressBytesToAccountID((byte[]) nftTransferDecoded.get(1)));
					nftTransferBuilder.setSerialNumber((long) nftTransferDecoded.get(2));
					tokenTransferBuilder.addNftTransfers(nftTransferBuilder.build());
				}

				builder.addTokenTransfers(tokenTransferBuilder.build());
			}
		}
	}

	@SuppressWarnings("unused")
	public static void decodeTransferTokens(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var accountIDs = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(1));
		final var amountsConverted = convertLongArrayToBigIntegerArray((long[]) decodedArguments.get(2));
	}

	@SuppressWarnings("unused")
	public static void decodeTransferToken(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var fromAccountId = convertAddressBytesToAccountID((byte[]) decodedArguments.get(1));
		final var toAccountId = convertAddressBytesToAccountID((byte[]) decodedArguments.get(2));
		final var amountConverted = BigInteger.valueOf((long) decodedArguments.get(3));
	}

	@SuppressWarnings("unused")
	public static void decodeTransferNFTs(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_NFTS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var senderIDs = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(1));
		final var receiverIDs = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(2));
		final var serialNumbers = convertLongArrayToBigIntegerArray((long[]) decodedArguments.get(3));
	}

	@SuppressWarnings("unused")
	public static void decodeTransferNFT(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_NFT_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var senderID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(1));
		final var recipientID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(2));
		final var serialNumber = BigInteger.valueOf((long) decodedArguments.get(3));
	}

	@SuppressWarnings("unused")
	public static void decodeBurnToken(final Bytes input) {
		final Tuple decodedArguments =
				BURN_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var amount = BigInteger.valueOf((long) decodedArguments.get(1));
		final var serialNumbers = convertLongArrayToBigIntegerArray((long[]) decodedArguments.get(2));
	}

	@SuppressWarnings("unused")
	public static void decodeAAssociateTokens(final Bytes input) {
		final Tuple decodedArguments =
				ASSOCIATE_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));
	}

	@SuppressWarnings("unused")
	public static void decodeDissociateTokens(final Bytes input) {
		final Tuple decodedArguments =
				DISSOCIATE_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));
	}

	public SyntheticTxnFactory.MintInput decodeMint(final Bytes input) {
		final Tuple decodedArguments =
				MINT_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var amount = (long) decodedArguments.get(1);
		final var metadata = String.valueOf(decodedArguments.get(2));

		return new SyntheticTxnFactory.MintInput(
				tokenID, amount,
				Collections.singletonList(ByteString.copyFrom(metadata.getBytes())));
	}

	public SyntheticTxnFactory.AssociateToken decodeAssociate(final Bytes input) {
		final Tuple decodedArguments =
				ASSOCIATE_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		return new SyntheticTxnFactory.AssociateToken(
				accountID,
				tokenID);
	}

	public SyntheticTxnFactory.DissociateToken decodeDissociate(final Bytes input) {
		final Tuple decodedArguments =
				DISSOCIATE_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		return new SyntheticTxnFactory.DissociateToken(
				accountID,
				tokenID);
	}

	private static List<BigInteger> convertLongArrayToBigIntegerArray(final long[] longArray) {
		final List<BigInteger> bigIntegers = new ArrayList<>();
		for(final var longValue: longArray) {
			bigIntegers.add(BigInteger.valueOf(longValue));
		}

		return bigIntegers;
	}

	private static List<AccountID> decodeAccountIDsFromBytesArray(final byte[][] accountBytesArray) {
		final List<AccountID> accountIDs = new ArrayList<>();
		for(final var account: accountBytesArray) {
			accountIDs.add(convertAddressBytesToAccountID(account));
		}
		return accountIDs;
	}

	private static List<TokenID> decodeTokenIDsFromBytesArray(final byte[][] accountBytesArray) {
		final List<TokenID> accountIDs = new ArrayList<>();
		for(final var account: accountBytesArray) {
			accountIDs.add(convertAddressBytesToTokenID(account));
		}
		return accountIDs;
	}

	private static AccountID convertAddressBytesToAccountID(final byte[] addressBytes) {
		final var address = Address.wrap(Bytes.wrap(addressBytes).slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
		return EntityIdUtils.accountParsedFromSolidityAddress(address.toArray());
	}

	private static TokenID convertAddressBytesToTokenID(final byte[] addressBytes) {
		final var address = Address.wrap(Bytes.wrap(addressBytes).slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
		return EntityIdUtils.tokenParsedFromSolidityAddress(address.toArray());
	}
}