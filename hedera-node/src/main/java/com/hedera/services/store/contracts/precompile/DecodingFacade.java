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

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
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

@Singleton
public class DecodingFacade {
	private static final int ADDRESS_BYTES_LENGTH = 20;
	private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;
	private static final String INT_OUTPUT = "(int)";

	private static final Function CRYPTO_TRANSFER_FUNCTION = new Function(
			"cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])", INT_OUTPUT);
	private static final Bytes CRYPTO_TRANSFER_SELECTOR = Bytes.wrap(CRYPTO_TRANSFER_FUNCTION.selector());
	private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER = TypeFactory.create(
			"((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])");

	private static final Function TRANSFER_TOKENS_FUNCTION =
			new Function("transferTokens(address,address[],int64[])", INT_OUTPUT);
	private static final Bytes TRANSFER_TOKENS_SELECTOR = Bytes.wrap(TRANSFER_TOKENS_FUNCTION.selector());
	private static final ABIType<Tuple> TRANSFER_TOKENS_DECODER = TypeFactory.create("(bytes32,bytes32[],int64[])");

	private static final Function TRANSFER_TOKEN_FUNCTION =
			new Function("transferToken(address,address,address,int64)", INT_OUTPUT);
	private static final Bytes TRANSFER_TOKEN_SELECTOR = Bytes.wrap(TRANSFER_TOKEN_FUNCTION.selector());
	private static final ABIType<Tuple> TRANSFER_TOKEN_DECODER = TypeFactory.create("(bytes32,bytes32,bytes32,int64)");

	private static final Function TRANSFER_NFTS_FUNCTION =
			new Function("transferNFTs(address,address[],address[],int64[])", INT_OUTPUT);
	private static final Bytes TRANSFER_NFTS_SELECTOR = Bytes.wrap(TRANSFER_NFTS_FUNCTION.selector());
	private static final ABIType<Tuple> TRANSFER_NFTS_DECODER = TypeFactory.create(
			"(bytes32,bytes32[],bytes32[],int64[])");

	private static final Function TRANSFER_NFT_FUNCTION =
			new Function("transferNFT(address,address,address,int64)", INT_OUTPUT);
	private static final Bytes TRANSFER_NFT_SELECTOR = Bytes.wrap(TRANSFER_NFT_FUNCTION.selector());
	private static final ABIType<Tuple> TRANSFER_NFT_DECODER = TypeFactory.create("(bytes32,bytes32,bytes32,int64)");

	private static final Function MINT_TOKEN_FUNCTION = new Function("mintToken(address,uint64,bytes[])", INT_OUTPUT);
	private static final Bytes MINT_TOKEN_SELECTOR = Bytes.wrap(MINT_TOKEN_FUNCTION.selector());
	private static final ABIType<Tuple> MINT_TOKEN_DECODER = TypeFactory.create("(bytes32,int64,bytes[])");

	private static final Function BURN_TOKEN_FUNCTION =
			new Function("burnToken(address,uint64,int64[])", INT_OUTPUT);
	private static final Bytes BURN_TOKEN_SELECTOR = Bytes.wrap(BURN_TOKEN_FUNCTION.selector());
	private static final ABIType<Tuple> BURN_TOKEN_DECODER = TypeFactory.create("(bytes32,int64,int64[])");

	private static final Function ASSOCIATE_TOKENS_FUNCTION =
			new Function("associateTokens(address,address[])", INT_OUTPUT);
	private static final Bytes ASSOCIATE_TOKENS_SELECTOR = Bytes.wrap(ASSOCIATE_TOKENS_FUNCTION.selector());
	private static final ABIType<Tuple> ASSOCIATE_TOKENS_DECODER = TypeFactory.create("(bytes32,bytes32[])");

	private static final Function ASSOCIATE_TOKEN_FUNCTION =
			new Function("associateToken(address,address)", INT_OUTPUT);
	private static final Bytes ASSOCIATE_TOKEN_SELECTOR = Bytes.wrap(ASSOCIATE_TOKEN_FUNCTION.selector());
	private static final ABIType<Tuple> ASSOCIATE_TOKEN_DECODER = TypeFactory.create("(bytes32,bytes32)");

	private static final Function DISSOCIATE_TOKENS_FUNCTION =
			new Function("dissociateTokens(address,address[])", INT_OUTPUT);
	private static final Bytes DISSOCIATE_TOKENS_SELECTOR = Bytes.wrap(DISSOCIATE_TOKENS_FUNCTION.selector());
	private static final ABIType<Tuple> DISSOCIATE_TOKENS_DECODER = TypeFactory.create("(bytes32,bytes32[])");

	private static final Function DISSOCIATE_TOKEN_FUNCTION =
			new Function("dissociateToken(address,address)", INT_OUTPUT);
	private static final Bytes DISSOCIATE_TOKEN_SELECTOR = Bytes.wrap(DISSOCIATE_TOKEN_FUNCTION.selector());
	private static final ABIType<Tuple> DISSOCIATE_TOKEN_DECODER = TypeFactory.create("(bytes32,bytes32)");
	
	private static final List<SyntheticTxnFactory.NftExchange> NO_NFT_EXCHANGES = Collections.emptyList();
	private static final List<SyntheticTxnFactory.FungibleTokenTransfer> NO_FUNGIBLE_TRANSFERS = Collections.emptyList();

	@Inject
	public DecodingFacade() {
	}

	public List<TokenTransferWrapper> decodeCryptoTransfer(final Bytes input) {
		final Tuple decodedTuples = decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR, CRYPTO_TRANSFER_DECODER);
		final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

		for (final var tuple : decodedTuples) {
			for (final var tupleNested : (Tuple[]) tuple) {
				final var tokenType = convertAddressBytesToTokenID((byte[]) tupleNested.get(0));

				var nftExchanges = NO_NFT_EXCHANGES;
				var fungibleTransfers = NO_FUNGIBLE_TRANSFERS;

				final var abiAdjustments = (Tuple[]) tupleNested.get(1);
				if (abiAdjustments.length > 0) {
					fungibleTransfers = bindFungibleTransfersFrom(tokenType, abiAdjustments);
				}
				final var abiNftExchanges = (Tuple[]) tupleNested.get(2);
				if (abiNftExchanges.length > 0) {
					nftExchanges = bindNftExchangesFrom(tokenType, abiNftExchanges);
				}

				tokenTransferWrappers.add(new TokenTransferWrapper(nftExchanges, fungibleTransfers));
			}
		}

		return tokenTransferWrappers;
	}

	public BurnWrapper decodeBurn(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, BURN_TOKEN_SELECTOR, BURN_TOKEN_DECODER);

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var fungibleAmount = (long) decodedArguments.get(1);
		final var serialNumbers = (long[]) decodedArguments.get(2);

		if (fungibleAmount > 0) {
			return BurnWrapper.forFungible(
					tokenID, fungibleAmount);
		} else {
			return BurnWrapper.forNonFungible(
					tokenID, Arrays.stream(serialNumbers).boxed().toList());
		}
	}

	public MintWrapper decodeMint(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, MINT_TOKEN_SELECTOR, MINT_TOKEN_DECODER);

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var fungibleAmount = (long) decodedArguments.get(1);
		final var metadataList = (byte[][]) decodedArguments.get(2);
		final List<ByteString> metadata = Arrays.stream(metadataList).map(ByteString::copyFrom).toList();

		if (fungibleAmount > 0) {
			return MintWrapper.forFungible(tokenID, fungibleAmount);
		} else {
			return MintWrapper.forNonFungible(
					tokenID, metadata);
		}
	}

	public List<TokenTransferWrapper> decodeTransferToken(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKEN_SELECTOR, TRANSFER_TOKEN_DECODER);

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var sender = convertAddressBytesToAccountID((byte[]) decodedArguments.get(1));
		final var receiver = convertAddressBytesToAccountID((byte[]) decodedArguments.get(2));
		final var amount = (long) decodedArguments.get(3);

		return Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES,
				List.of(new SyntheticTxnFactory.FungibleTokenTransfer(amount, tokenID, sender, receiver))));
	}

	public List<TokenTransferWrapper> decodeTransferTokens(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKENS_SELECTOR, TRANSFER_TOKENS_DECODER);

		final var tokenType = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var accountIDs = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(1));
		final var amounts = (long[]) decodedArguments.get(2);

		final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
		for (int i = 0; i < accountIDs.size(); i++) {
			final var accountID = accountIDs.get(i);
			final var amount = amounts[i];

			addAdjustmentAsTransfer(fungibleTransfers, tokenType, accountID, amount);
		}

		return Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));
	}

	public List<TokenTransferWrapper> decodeTransferNFT(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFT_SELECTOR, TRANSFER_NFT_DECODER);

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var sender = convertAddressBytesToAccountID((byte[]) decodedArguments.get(1));
		final var receiver = convertAddressBytesToAccountID((byte[]) decodedArguments.get(2));
		final var serialNumber = (long) decodedArguments.get(3);

		return Collections.singletonList(
				new TokenTransferWrapper(
						List.of(new SyntheticTxnFactory.NftExchange(serialNumber, tokenID, sender, receiver)),
						NO_FUNGIBLE_TRANSFERS));
	}

	public List<TokenTransferWrapper> decodeTransferNFTs(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFTS_SELECTOR, TRANSFER_NFTS_DECODER);

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var senders = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(1));
		final var receivers = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(2));
		final var serialNumbers = ((long[]) decodedArguments.get(3));

		final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
		for (var i = 0; i < senders.size(); i++) {
			final var nftExchange = new SyntheticTxnFactory.NftExchange(
					serialNumbers[i], tokenID, senders.get(i), receivers.get(i));
			nftExchanges.add(nftExchange);
		}

		return Collections.singletonList(new TokenTransferWrapper(nftExchanges, NO_FUNGIBLE_TRANSFERS));
	}

	public Association decodeAssociation(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, ASSOCIATE_TOKEN_SELECTOR, ASSOCIATE_TOKEN_DECODER);

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		return Association.singleAssociation(
				accountID, tokenID);
	}

	public Association decodeMultipleAssociations(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, ASSOCIATE_TOKENS_SELECTOR, ASSOCIATE_TOKENS_DECODER);

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));

		return Association.multiAssociation(accountID, tokenIDs);
	}

	public Dissociation decodeDissociate(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, DISSOCIATE_TOKEN_SELECTOR, DISSOCIATE_TOKEN_DECODER);

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		return Dissociation.singleDissociation(accountID, tokenID);
	}

	public Dissociation decodeMultipleDissociations(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, DISSOCIATE_TOKENS_SELECTOR, DISSOCIATE_TOKENS_DECODER);

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));

		return Dissociation.multiDissociation(accountID, tokenIDs);
	}

	private Tuple decodeFunctionCall(final Bytes input, final Bytes selector, final ABIType<Tuple> decoder) {
		if (!selector.equals(input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH))) {
			throw new IllegalArgumentException(
					"Selector does not match, expected " + selector
							+ " actual " + input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH));
		}
		return decoder.decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH).toArray());
	}

	private static List<AccountID> decodeAccountIDsFromBytesArray(final byte[][] accountBytesArray) {
		final List<AccountID> accountIDs = new ArrayList<>();
		for (final var account : accountBytesArray) {
			accountIDs.add(convertAddressBytesToAccountID(account));
		}
		return accountIDs;
	}

	private static List<TokenID> decodeTokenIDsFromBytesArray(final byte[][] accountBytesArray) {
		final List<TokenID> accountIDs = new ArrayList<>();
		for (final var account : accountBytesArray) {
			accountIDs.add(convertAddressBytesToTokenID(account));
		}
		return accountIDs;
	}

	private static AccountID convertAddressBytesToAccountID(final byte[] addressBytes) {
		final var address = Address.wrap(
				Bytes.wrap(addressBytes).slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
		return EntityIdUtils.accountParsedFromSolidityAddress(address.toArray());
	}

	private static TokenID convertAddressBytesToTokenID(final byte[] addressBytes) {
		final var address = Address.wrap(
				Bytes.wrap(addressBytes).slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
		return EntityIdUtils.tokenParsedFromSolidityAddress(address.toArray());
	}

	private List<SyntheticTxnFactory.NftExchange> bindNftExchangesFrom(
			final TokenID tokenType,
			final Tuple[] abiExchanges
	) {
		final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
		for (final var exchange : abiExchanges) {
			final var sender = convertAddressBytesToAccountID((byte[]) exchange.get(0));
			final var receiver = convertAddressBytesToAccountID((byte[]) exchange.get(1));
			final var serialNo = (long) exchange.get(2);
			nftExchanges.add(new SyntheticTxnFactory.NftExchange(serialNo, tokenType, sender, receiver));
		}
		return nftExchanges;
	}

	private List<SyntheticTxnFactory.FungibleTokenTransfer> bindFungibleTransfersFrom(
			final TokenID tokenType,
			final Tuple[] abiTransfers
	) {
		final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
		for (final var transfer : abiTransfers) {
			final AccountID accountID = convertAddressBytesToAccountID((byte[]) transfer.get(0));
			final long amount = (long) transfer.get(1);

			addAdjustmentAsTransfer(fungibleTransfers, tokenType, accountID, amount);
		}
		return fungibleTransfers;
	}

	private void addAdjustmentAsTransfer(
			final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers,
			final TokenID tokenType,
			final AccountID accountID,
			final long amount
	) {
		if (amount > 0) {
			fungibleTransfers.add(new SyntheticTxnFactory.FungibleTokenTransfer(amount, tokenType, null, accountID));
		} else {
			fungibleTransfers.add(new SyntheticTxnFactory.FungibleTokenTransfer(-amount, tokenType, accountID, null));
		}
	}
}