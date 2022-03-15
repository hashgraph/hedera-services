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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;

@Singleton
public class DecodingFacade {
	private static final int WORD_LENGTH = 32;
	private static final int ADDRESS_BYTES_LENGTH = 20;
	private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;
	private static final String INT_OUTPUT = "(int)";
	private static final String BOOL_OUTPUT = "(bool)";
	private static final String STRING_OUTPUT = "(string)";

	private static final List<SyntheticTxnFactory.NftExchange> NO_NFT_EXCHANGES = Collections.emptyList();
	private static final List<SyntheticTxnFactory.FungibleTokenTransfer> NO_FUNGIBLE_TRANSFERS = Collections.emptyList();

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

	private static final Function TOKEN_URI_NFT_FUNCTION =
			new Function("tokenURI(uint256)", STRING_OUTPUT);
	private static final Bytes TOKEN_URI_NFT_SELECTOR = Bytes.wrap(TOKEN_URI_NFT_FUNCTION.selector());
	private static final ABIType<Tuple> TOKEN_URI_NFT_DECODER = TypeFactory.create("(uint256)");

	private static final Function BALANCE_OF_TOKEN_FUNCTION =
			new Function("balanceOf(address)", INT_OUTPUT);
	private static final Bytes BALANCE_OF_TOKEN_SELECTOR = Bytes.wrap(BALANCE_OF_TOKEN_FUNCTION.selector());
	private static final ABIType<Tuple> BALANCE_OF_TOKEN_DECODER = TypeFactory.create("(bytes32)");

	private static final Function OWNER_OF_NFT_FUNCTION =
			new Function("ownerOf(uint256)", INT_OUTPUT);
	private static final Bytes OWNER_OF_NFT_SELECTOR = Bytes.wrap(OWNER_OF_NFT_FUNCTION.selector());
	private static final ABIType<Tuple> OWNER_OF_NFT_DECODER = TypeFactory.create("(uint256)");

	private static final Function ERC_TRANSFER_FUNCTION =
			new Function("transfer(address,uint256)", BOOL_OUTPUT);
	private static final Bytes ERC_TRANSFER_SELECTOR = Bytes.wrap(ERC_TRANSFER_FUNCTION.selector());
	private static final ABIType<Tuple> ERC_TRANSFER_DECODER = TypeFactory.create("(bytes32,uint256)");

	private static final Function ERC_TRANSFER_FROM_FUNCTION =
			new Function("transferFrom(address,address,uint256)");
	private static final Bytes ERC_TRANSFER_FROM_SELECTOR = Bytes.wrap(ERC_TRANSFER_FROM_FUNCTION.selector());
	private static final ABIType<Tuple> ERC_TRANSFER_FROM_DECODER = TypeFactory.create("(bytes32,bytes32,uint256)");

	private static final Function TOKEN_ALLOWANCE_FUNCTION =
			new Function("allowance(address,address)", INT_OUTPUT);
	private static final Bytes TOKEN_ALLOWANCE_SELECTOR = Bytes.wrap(TOKEN_ALLOWANCE_FUNCTION.selector());
	private static final ABIType<Tuple> TOKEN_ALLOWANCE_DECODER = TypeFactory.create("(bytes32,bytes32)");

	private static final Function GET_APPROVED_FUNCTION =
			new Function("getApproved(uint256)", INT_OUTPUT);
	private static final Bytes GET_APPROVED_FUNCTION_SELECTOR = Bytes.wrap(GET_APPROVED_FUNCTION.selector());
	private static final ABIType<Tuple> GET_APPROVED_FUNCTION_DECODER = TypeFactory.create("(uint256)");

	private static final Function IS_APPROVED_FOR_ALL =
			new Function("isApprovedForAll(address,address)");
	private static final Bytes IS_APPROVED_FOR_ALL_SELECTOR = Bytes.wrap(IS_APPROVED_FOR_ALL.selector());
	private static final ABIType<Tuple> IS_APPROVED_FOR_ALL_DECODER = TypeFactory.create("(bytes32,bytes32)");

	private static final Function SET_APPROVAL_FOR_ALL =
			new Function("setApprovalForAll(address,bool)");
	private static final Bytes SET_APPROVAL_FOR_ALL_SELECTOR = Bytes.wrap(SET_APPROVAL_FOR_ALL.selector());
	private static final ABIType<Tuple> SET_APPROVAL_FOR_ALL_DECODER = TypeFactory.create("(bytes32,bool)");

	private static final Function TOKEN_APPROVE_FUNCTION =
			new Function("approve(address,uint256)", BOOL_OUTPUT);
	private static final Bytes TOKEN_APPROVE_SELECTOR = Bytes.wrap(TOKEN_APPROVE_FUNCTION.selector());
	private static final ABIType<Tuple> TOKEN_APPROVE_DECODER = TypeFactory.create("(bytes32,uint256)");

	@Inject
	public DecodingFacade() {
	}

	public List<TokenTransferWrapper> decodeCryptoTransfer(
			final Bytes input,
			final UnaryOperator<byte[]> aliasResolver
	) {
		final Tuple decodedTuples = decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR, CRYPTO_TRANSFER_DECODER);
		final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

		for (final var tuple : decodedTuples) {
			for (final var tupleNested : (Tuple[]) tuple) {
				final var tokenType = convertAddressBytesToTokenID((byte[]) tupleNested.get(0));

				var nftExchanges = NO_NFT_EXCHANGES;
				var fungibleTransfers = NO_FUNGIBLE_TRANSFERS;

				final var abiAdjustments = (Tuple[]) tupleNested.get(1);
				if (abiAdjustments.length > 0) {
					fungibleTransfers = bindFungibleTransfersFrom(tokenType, abiAdjustments, aliasResolver);
				}
				final var abiNftExchanges = (Tuple[]) tupleNested.get(2);
				if (abiNftExchanges.length > 0) {
					nftExchanges = bindNftExchangesFrom(tokenType, abiNftExchanges, aliasResolver);
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

	public BalanceOfWrapper decodeBalanceOf(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, BALANCE_OF_TOKEN_SELECTOR, BALANCE_OF_TOKEN_DECODER);

		final var account = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);

		return new BalanceOfWrapper(account);
	}

	public List<TokenTransferWrapper> decodeErcTransfer(final Bytes input, final TokenID token,
														final AccountID caller, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, ERC_TRANSFER_SELECTOR, ERC_TRANSFER_DECODER);

		final var recipient = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var amount = (BigInteger) decodedArguments.get(1);

		final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
		addAdjustmentAsTransfer(fungibleTransfers, token, recipient, amount.longValue());
		addAdjustmentAsTransfer(fungibleTransfers, token, caller, -amount.longValue());

		return Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));
	}

	public List<TokenTransferWrapper> decodeERCTransferFrom(final Bytes input,
															final TokenID token, final boolean isFungible,
															final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, ERC_TRANSFER_FROM_SELECTOR, ERC_TRANSFER_FROM_DECODER);

		final var from = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var to = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(1), aliasResolver);
		if (isFungible) {
			final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
			final var amount = (BigInteger) decodedArguments.get(2);
			addAdjustmentAsTransfer(fungibleTransfers, token, to, amount.longValue());
			addAdjustmentAsTransfer(fungibleTransfers, token, from, -amount.longValue());
			return Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));
		} else {
			final List<SyntheticTxnFactory.NftExchange> nonFungibleTransfers = new ArrayList<>();
			final var serialNumber = (BigInteger) decodedArguments.get(2);
			nonFungibleTransfers.add(new SyntheticTxnFactory.NftExchange(serialNumber.longValue(), token, from, to));
			return Collections.singletonList(new TokenTransferWrapper(nonFungibleTransfers, NO_FUNGIBLE_TRANSFERS));
		}
	}

	public OwnerOfAndTokenURIWrapper decodeOwnerOf(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, OWNER_OF_NFT_SELECTOR, OWNER_OF_NFT_DECODER);

		final var tokenId = (BigInteger) decodedArguments.get(0);

		return new OwnerOfAndTokenURIWrapper(tokenId.longValue());
	}

	public OwnerOfAndTokenURIWrapper decodeTokenUriNFT(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, TOKEN_URI_NFT_SELECTOR, TOKEN_URI_NFT_DECODER);

		final var tokenId = (BigInteger) decodedArguments.get(0);

		return new OwnerOfAndTokenURIWrapper(tokenId.longValue());
	}

	public GetApprovedWrapper decodeGetApproved(final Bytes input) {
		final Tuple decodedArguments = decodeFunctionCall(input, GET_APPROVED_FUNCTION_SELECTOR, GET_APPROVED_FUNCTION_DECODER);

		final var tokenId = (BigInteger) decodedArguments.get(0);

		return new GetApprovedWrapper(tokenId.longValue());
	}

	public TokenAllowanceWrapper decodeTokenAllowance(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, TOKEN_ALLOWANCE_SELECTOR, TOKEN_ALLOWANCE_DECODER);

		final var owner = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var spender = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(1), aliasResolver);

		return new TokenAllowanceWrapper(owner, spender);
	}

	public ApproveWrapper decodeTokenApprove(final Bytes input, final TokenID token, final boolean isFungible, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, TOKEN_APPROVE_SELECTOR, TOKEN_APPROVE_DECODER);

		final var spender = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var amount = (BigInteger) decodedArguments.get(1);

		return new ApproveWrapper(token, spender, amount, BigInteger.ZERO, isFungible);
	}

	public SetApprovalForAllWrapper decodeSetApprovalForAll(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, SET_APPROVAL_FOR_ALL_SELECTOR, SET_APPROVAL_FOR_ALL_DECODER);

		final var to = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var approved = (boolean) decodedArguments.get(1);

		return new SetApprovalForAllWrapper(to, approved);
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

	public List<TokenTransferWrapper> decodeTransferToken(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKEN_SELECTOR, TRANSFER_TOKEN_DECODER);

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var sender = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(1), aliasResolver);
		final var receiver = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(2), aliasResolver);
		final var amount = (long) decodedArguments.get(3);

		return Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES,
				List.of(new SyntheticTxnFactory.FungibleTokenTransfer(amount, tokenID, sender, receiver))));
	}

	public IsApproveForAllWrapper decodeIsApprovedForAll(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, IS_APPROVED_FOR_ALL_SELECTOR, IS_APPROVED_FOR_ALL_DECODER);

		final var owner = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var operator = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(1), aliasResolver);

		return new IsApproveForAllWrapper(owner, operator);
	}

	public List<TokenTransferWrapper> decodeTransferTokens(
			final Bytes input,
			final UnaryOperator<byte[]> aliasResolver
	) {
		final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKENS_SELECTOR, TRANSFER_TOKENS_DECODER);

		final var tokenType = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var accountIDs = decodeAccountIds((byte[][]) decodedArguments.get(1), aliasResolver);
		final var amounts = (long[]) decodedArguments.get(2);

		final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
		for (int i = 0; i < accountIDs.size(); i++) {
			final var accountID = accountIDs.get(i);
			final var amount = amounts[i];

			addAdjustmentAsTransfer(fungibleTransfers, tokenType, accountID, amount);
		}

		return Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));
	}

	public List<TokenTransferWrapper> decodeTransferNFT(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFT_SELECTOR, TRANSFER_NFT_DECODER);

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var sender = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(1), aliasResolver);
		final var receiver = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(2), aliasResolver);
		final var serialNumber = (long) decodedArguments.get(3);

		return Collections.singletonList(
				new TokenTransferWrapper(
						List.of(new SyntheticTxnFactory.NftExchange(serialNumber, tokenID, sender, receiver)),
						NO_FUNGIBLE_TRANSFERS));
	}

	public List<TokenTransferWrapper> decodeTransferNFTs(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFTS_SELECTOR, TRANSFER_NFTS_DECODER);

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var senders = decodeAccountIds((byte[][]) decodedArguments.get(1), aliasResolver);
		final var receivers = decodeAccountIds((byte[][]) decodedArguments.get(2), aliasResolver);
		final var serialNumbers = ((long[]) decodedArguments.get(3));

		final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
		for (var i = 0; i < senders.size(); i++) {
			final var nftExchange = new SyntheticTxnFactory.NftExchange(
					serialNumbers[i], tokenID, senders.get(i), receivers.get(i));
			nftExchanges.add(nftExchange);
		}

		return Collections.singletonList(new TokenTransferWrapper(nftExchanges, NO_FUNGIBLE_TRANSFERS));
	}

	public Association decodeAssociation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, ASSOCIATE_TOKEN_SELECTOR, ASSOCIATE_TOKEN_DECODER);

		final var accountID = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		return Association.singleAssociation(
				accountID, tokenID);
	}

	public Association decodeMultipleAssociations(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, ASSOCIATE_TOKENS_SELECTOR, ASSOCIATE_TOKENS_DECODER);

		final var accountID = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));

		return Association.multiAssociation(accountID, tokenIDs);
	}

	public Dissociation decodeDissociate(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, DISSOCIATE_TOKEN_SELECTOR, DISSOCIATE_TOKEN_DECODER);

		final var accountID = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		return Dissociation.singleDissociation(accountID, tokenID);
	}

	public Dissociation decodeMultipleDissociations(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final Tuple decodedArguments = decodeFunctionCall(input, DISSOCIATE_TOKENS_SELECTOR, DISSOCIATE_TOKENS_DECODER);

		final var accountID = convertLeftPaddedAddressToAccountId((byte[]) decodedArguments.get(0), aliasResolver);
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

	private static List<AccountID> decodeAccountIds(
			final byte[][] accountBytesArray,
			final UnaryOperator<byte[]> aliasResolver
	) {
		final List<AccountID> accountIDs = new ArrayList<>();
		for (final var account : accountBytesArray) {
			accountIDs.add(convertLeftPaddedAddressToAccountId(account, aliasResolver));
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

	private static AccountID convertLeftPaddedAddressToAccountId(
			final byte[] leftPaddedAddress,
			final UnaryOperator<byte[]> aliasResolver
	) {
		final var addressOrAlias = Arrays.copyOfRange(leftPaddedAddress, ADDRESS_SKIP_BYTES_LENGTH, WORD_LENGTH);
		return accountIdFromEvmAddress(aliasResolver.apply(addressOrAlias));
	}

	private static TokenID convertAddressBytesToTokenID(final byte[] addressBytes) {
		final var address = Address.wrap(
				Bytes.wrap(addressBytes).slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
		return EntityIdUtils.tokenIdFromEvmAddress(address.toArray());
	}

	private List<SyntheticTxnFactory.NftExchange> bindNftExchangesFrom(
			final TokenID tokenType,
			final Tuple[] abiExchanges,
			final UnaryOperator<byte[]> aliasResolver
	) {
		final List<SyntheticTxnFactory.NftExchange> nftExchanges = new ArrayList<>();
		for (final var exchange : abiExchanges) {
			final var sender = convertLeftPaddedAddressToAccountId((byte[]) exchange.get(0), aliasResolver);
			final var receiver = convertLeftPaddedAddressToAccountId((byte[]) exchange.get(1), aliasResolver);
			final var serialNo = (long) exchange.get(2);
			nftExchanges.add(new SyntheticTxnFactory.NftExchange(serialNo, tokenType, sender, receiver));
		}
		return nftExchanges;
	}

	private List<SyntheticTxnFactory.FungibleTokenTransfer> bindFungibleTransfersFrom(
			final TokenID tokenType,
			final Tuple[] abiTransfers,
			final UnaryOperator<byte[]> aliasResolver
	) {
		final List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
		for (final var transfer : abiTransfers) {
			final AccountID accountID = convertLeftPaddedAddressToAccountId((byte[]) transfer.get(0), aliasResolver);
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