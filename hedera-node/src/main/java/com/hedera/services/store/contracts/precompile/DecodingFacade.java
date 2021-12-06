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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class DecodingFacade {
	private static final int ADDRESS_BYTES_LENGTH = 20;
	private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;

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
	public static SyntheticTxnFactory.TokenTransferLists decodeCryptoTransfer(final Bytes input) {
		final Tuple decodedTuples =
				CRYPTO_TRANSFER_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
		final List<SyntheticTxnFactory.HbarTransfer> hbarTransfers = new ArrayList<>();
		final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
		for(final var tuple: decodedTuples) {
			for(final var tupleNested: (Tuple[]) tuple) {
				final var tokenType = convertAddressBytesToTokenID((byte[]) tupleNested.get(0));

				final var transfers = (Tuple[]) tupleNested.get(1);
				for(final var transfer: transfers) {
					final AccountID accountID = convertAddressBytesToAccountID((byte[]) transfer.get(0));
					final long amount = (long) transfer.get(1);
					if(amount>0) {
						fungibleTransfers.add(new SyntheticTxnFactory.FungibleTokenTransfer(amount, tokenType, null,
								accountID));
						hbarTransfers.add(new SyntheticTxnFactory.HbarTransfer(amount, null,
								accountID));
					} else {
						fungibleTransfers.add(new SyntheticTxnFactory.FungibleTokenTransfer(amount, tokenType, accountID,
								null));
						hbarTransfers.add(new SyntheticTxnFactory.HbarTransfer(amount, accountID,
								null));
					}
				}

				final var nftTransfersDecoded = (Tuple[]) tupleNested.get(2);
				for(final var nftTransferDecoded: nftTransfersDecoded) {
					nftExchanges.add(new SyntheticTxnFactory.NftExchange((long) nftTransferDecoded.get(2),
							tokenType, convertAddressBytesToAccountID((byte[]) nftTransferDecoded.get(0)),
							convertAddressBytesToAccountID((byte[]) nftTransferDecoded.get(1))));
				}
			}
		}

		final SyntheticTxnFactory.TokenTransferLists tokenTransferLists =
				new SyntheticTxnFactory.TokenTransferLists(nftExchanges, hbarTransfers, fungibleTransfers);
		return tokenTransferLists;
	}

	public SyntheticTxnFactory.BurnWrapper decodeBurn(final Bytes input) {
		final Tuple decodedArguments =
				BURN_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var fungibleAmount = (long) decodedArguments.get(1);
		final var serialNumbers = (long[]) decodedArguments.get(2);

		if (fungibleAmount > 0) {
			return SyntheticTxnFactory.BurnWrapper.forFungible(
					tokenID, fungibleAmount);
		} else {
			return SyntheticTxnFactory.BurnWrapper.forNonFungible(
					tokenID, Arrays.stream(serialNumbers).boxed().collect(Collectors.toList()));
		}
	}

	public SyntheticTxnFactory.MintWrapper decodeMint(final Bytes input) {
		final Tuple decodedArguments =
				MINT_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var fungibleAmount = (long) decodedArguments.get(1);
		final var metadata = String.valueOf(decodedArguments.get(2));

		if (fungibleAmount > 0) {
			return SyntheticTxnFactory.MintWrapper.forFungible(
					tokenID, fungibleAmount);
		} else {
			return SyntheticTxnFactory.MintWrapper.forNonFungible(
					tokenID, Collections.singletonList(ByteString.copyFrom(metadata.getBytes())));
		}
	}

	@SuppressWarnings("unused")
	public static SyntheticTxnFactory.FungibleTokenTransfer decodeTransferToken(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var sender = convertAddressBytesToAccountID((byte[]) decodedArguments.get(1));
		final var receiver = convertAddressBytesToAccountID((byte[]) decodedArguments.get(2));
		final var amount = (long) decodedArguments.get(3);

		return new SyntheticTxnFactory.FungibleTokenTransfer(
				amount, tokenID,
				sender, receiver);
	}

	@SuppressWarnings("unused")
	public static SyntheticTxnFactory.FungibleTokensTransfer decodeTransferTokens(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var accountIDs = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(1));
		final var amounts = (long[]) decodedArguments.get(2);

		return new SyntheticTxnFactory.FungibleTokensTransfer(tokenID,
				accountIDs, amounts);
	}

	@SuppressWarnings("unused")
	public static SyntheticTxnFactory.NftExchange decodeTransferNFT(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_NFT_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var sender = convertAddressBytesToAccountID((byte[]) decodedArguments.get(1));
		final var receiver = convertAddressBytesToAccountID((byte[]) decodedArguments.get(2));
		final var serialNumber = (long) decodedArguments.get(3);

		return new SyntheticTxnFactory.NftExchange(
				serialNumber, tokenID,
				sender, receiver);
	}

	@SuppressWarnings("unused")
	public static List<SyntheticTxnFactory.NftExchange> decodeTransferNFTs(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_NFTS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var senders = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(1));
		final var receivers = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(2));
		final var serialNumbers = ((long[]) decodedArguments.get(3));

		final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
		for(var i = 0; i < senders.size(); i++) {
			final var nftExchange = new SyntheticTxnFactory.NftExchange(
					serialNumbers[i], tokenID,
					senders.get(i), receivers.get(i));
			nftExchanges.add(nftExchange);
		}

		return nftExchanges;
	}

	public SyntheticTxnFactory.Association decodeAssociation(final Bytes input) {
		final Tuple decodedArguments =
				ASSOCIATE_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		return SyntheticTxnFactory.Association.singleAssociation(
				accountID, tokenID);
	}

	public SyntheticTxnFactory.Association decodeMultipleAssociations(final Bytes input) {
		final Tuple decodedArguments =
				ASSOCIATE_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));

		return SyntheticTxnFactory.Association.multiAssociation(
				accountID, tokenIDs);
	}

	public SyntheticTxnFactory.Dissociation decodeDissociate(final Bytes input) {
		final Tuple decodedArguments =
				DISSOCIATE_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		return SyntheticTxnFactory.Dissociation.singleDissociation(
				accountID, tokenID);
	}

	public SyntheticTxnFactory.Dissociation decodeMultipleDissociations(final Bytes input) {
		final Tuple decodedArguments =
				DISSOCIATE_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));

		return SyntheticTxnFactory.Dissociation.multiDissociation(
					accountID, tokenIDs);
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