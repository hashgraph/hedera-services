package com.hedera.services.store.contracts.precompile;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PrecompileArgumentsDecoder {

	private static final Function CRYPTO_TRANSFER_FUNCTION = new Function("cryptoTransfer((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])",
			"((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])");
	private static final Function TRANSFER_TOKENS_FUNCTION = new Function("transferTokens(address,address[],int64[])",
			"(bytes32," +
					"bytes32[],int64[])");
	private static final Function TRANSFER_TOKEN_FUNCTION = new Function("transferToken(address,address,address,int64)",
			"(bytes32," +
					"bytes32,bytes32,int64)");
	private static final Function TRANSFER_NFTS_FUNCTION = new Function("transferNFTs(address,address[],address[],int64[])",
			"(bytes32," +
					"bytes32[],bytes32[],int64[])");
	private static final Function TRANSFER_NFT_FUNCTION = new Function("transferNFT(address,address,address,int64)",
			"(bytes32," +
					"bytes32,bytes32,int64)");
	private static final Function MINT_TOKEN_FUNCTION = new Function("mintToken(address,uint64,bytes)",
			"(bytes32," +
					"int64,string)");
	private static final Function BURN_TOKEN_FUNCTION = new Function("burnToken(address,uint64,int64[])",
			"(bytes32," +
					"int64,int64[])");
	private static final Function ASSOCIATE_TOKENS_FUNCTION = new Function("associateTokens(address,address[])",
			"(bytes32," +
					"bytes32[])");
	private static final Function ASSOCIATE_TOKEN_FUNCTION = new Function("associateToken(address,address)",
			"(bytes32," +
					"bytes32)");
	private static final Function DISSOCIATE_TOKENS_FUNCTION = new Function("dissociateTokens(address,address[])",
			"(bytes32," +
					"bytes32[])");
	private static final Function DISSOCIATE_TOKEN_FUNCTION = new Function("associateToken(address,address)",
			"(bytes32," +
					"bytes32)");
	private static final int ADDRESS_BYTES_LENGTH = 20;
	private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;

	private PrecompileArgumentsDecoder() {
	}

	public static Map<Integer, Object> decodeArgumentsForCryptoTransfer(final Bytes input) {
		final Tuple decodedTuples =
				CRYPTO_TRANSFER_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());
		final List<TokenTransferList> tokenTransferLists = new ArrayList<>();
		for(final var tuple: decodedTuples) {
			for(final var tupleNested: (Tuple[]) tuple) {
				final List<AccountAmount> accountAmounts = new ArrayList<>();
				final var transfers = (Tuple[]) tupleNested.get(1);
				for(final var transfer: transfers) {
					final var accountAmount = new AccountAmount((byte[]) transfer.get(0),
							(long) transfer.get(1));
					accountAmounts.add(accountAmount);
				}

				final List<NftTransfer> nftTransfers =new ArrayList<>();
				final var nftTransfersDecoded = (Tuple[]) tupleNested.get(2);
				for(final var nftTransferDecoded: nftTransfersDecoded) {
					final var nftTransfer = new NftTransfer((byte[]) nftTransferDecoded.get(0),
							(byte[]) nftTransferDecoded.get(1),
							(long) nftTransferDecoded.get(2));
					nftTransfers.add(nftTransfer);
				}

				final TokenTransferList tokenTransferList = new TokenTransferList((byte[]) tupleNested.get(0), accountAmounts, nftTransfers);
				tokenTransferLists.add(tokenTransferList);
			}
		}

		final Map<Integer, Object> result = new HashMap<>();
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForTransferTokens(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
				input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var accountIDs = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(1));
		final var amountsConverted = convertLongArrayToBigIntegerArray((long[]) decodedArguments.get(2));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, tokenID);
		result.put(2, accountIDs);
		result.put(3, amountsConverted);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForTransferToken(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
				input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var fromAccountId = convertAddressBytesToAccountID((byte[]) decodedArguments.get(1));
		final var toAccountId = convertAddressBytesToAccountID((byte[]) decodedArguments.get(2));
		final var amountConverted = BigInteger.valueOf((long) decodedArguments.get(3));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, tokenID);
		result.put(2, fromAccountId);
		result.put(3, toAccountId);
		result.put(4, amountConverted);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForTransferNFTs(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_NFTS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var senderIDs = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(1));
		final var receiverIDs = decodeAccountIDsFromBytesArray((byte[][]) decodedArguments.get(2));
		final var serialNumbers = convertLongArrayToBigIntegerArray((long[]) decodedArguments.get(3));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, tokenID);
		result.put(2, senderIDs);
		result.put(3, receiverIDs);
		result.put(4, serialNumbers);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForTransferNFT(final Bytes input) {
		final Tuple decodedArguments =
				TRANSFER_NFTS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var senderID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(1));
		final var recipientID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(2));
		final var serialNumber = BigInteger.valueOf((long) decodedArguments.get(3));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, tokenID);
		result.put(2, senderID);
		result.put(3, recipientID);
		result.put(4, serialNumber);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForMintToken(final Bytes input) {
		final Tuple decodedArguments =
				MINT_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var amount = BigInteger.valueOf((long) decodedArguments.get(1));
		final var metadata = String.valueOf(decodedArguments.get(2));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, tokenID);
		result.put(2, amount);
		result.put(3, metadata);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForBurnToken(final Bytes input) {
		final Tuple decodedArguments =
				BURN_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(0));
		final var amount = BigInteger.valueOf((long) decodedArguments.get(1));
		final var serialNumbers = convertLongArrayToBigIntegerArray((long[]) decodedArguments.get(2));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, tokenID);
		result.put(2, amount);
		result.put(3, serialNumbers);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForAssociateToken(final Bytes input) {
		final Tuple decodedArguments =
				ASSOCIATE_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, accountID);
		result.put(2, tokenID);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForDissociateToken(final Bytes input) {
		final Tuple decodedArguments =
				DISSOCIATE_TOKEN_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenID = convertAddressBytesToTokenID((byte[]) decodedArguments.get(1));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, accountID);
		result.put(2, tokenID);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForAssociateTokens(final Bytes input) {
		final Tuple decodedArguments =
				ASSOCIATE_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, accountID);
		result.put(2, tokenIDs);
		return result;
	}

	public static Map<Integer, Object> decodeArgumentsForDissociateTokens(final Bytes input) {
		final Tuple decodedArguments =
				DISSOCIATE_TOKENS_FUNCTION.getOutputs().decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH,
						input.size() - FUNCTION_SELECTOR_BYTES_LENGTH).toArray());

		final var accountID = convertAddressBytesToAccountID((byte[]) decodedArguments.get(0));
		final var tokenIDs = decodeTokenIDsFromBytesArray((byte[][]) decodedArguments.get(1));

		final Map<Integer, Object> result = new HashMap<>();
		result.put(1, accountID);
		result.put(2, tokenIDs);
		return result;
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

	public static class AccountAmount {
		private AccountID accountId;
		private BigInteger amount;

		public AccountAmount(final byte[] accountId, final long amount) {
			this.accountId = convertAddressBytesToAccountID(accountId);
			this.amount = BigInteger.valueOf(amount);
		}
	}

	public static class NftTransfer {
		private AccountID sender;
		private AccountID receiver;
		private BigInteger serialNumber;

		public NftTransfer(final byte[] sender, final byte[] receiver, final long serialNumber) {
			this.sender = convertAddressBytesToAccountID(sender);
			this.receiver = convertAddressBytesToAccountID(receiver);
			this.serialNumber = BigInteger.valueOf(serialNumber);
		}
	}

	public static class TokenTransferList {
		private TokenID token;
		private List<AccountAmount> transfers;
		private List<NftTransfer> nftTransfers;

		public TokenTransferList(final byte[] token, final List<AccountAmount> transfers,
								 final List<NftTransfer> nftTransfers) {
			this.token = convertAddressBytesToTokenID(token);
			this.transfers = transfers;
			this.nftTransfers = nftTransfers;
		}


	}
}