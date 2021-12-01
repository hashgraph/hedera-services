package com.hedera.services.store.contracts.precompile;

import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.StaticArray;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Int64;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PrecompileArgumentsDecoder {

	private static final int INPUT_ENTRY_BYTES_LENGTH = 32;
	private static final int ADDRESS_BYTES_LENGTH = 20;
	private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;
	private static final int MAX_STATIC_ARRAY_LENGTH = 32;

	private PrecompileArgumentsDecoder() {
	}

	public static Map<Integer, Object> decodeArgumentsForTransferTokens(final Bytes input) {

		// PARSING TOKEN ADDRESS
		final var tokenAddress = Address.wrap(input.slice(ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
		final var tokenID = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());

		// PARSING ACCOUNT ADDRESS ARRAY
		final var addressArrayStartPositionStartIndex =
				ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH + ADDRESS_BYTES_LENGTH;
		final var addressArrayStartPositionBytes = input.slice(addressArrayStartPositionStartIndex, INPUT_ENTRY_BYTES_LENGTH);
		final var addressArrayStartPosition =
				addressArrayStartPositionBytes.toBigInteger().intValue() + FUNCTION_SELECTOR_BYTES_LENGTH;
		final var addressArraySize = input.slice(addressArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();
		final var addressArraySizeInt = addressArraySize.intValue();
		final var addressArrayByteLength = addressArraySizeInt * INPUT_ENTRY_BYTES_LENGTH;
		final var addressArrayByteLengthWithHeader = addressArrayByteLength + INPUT_ENTRY_BYTES_LENGTH;

		final var addresses = decodeAddressArray(addressArraySizeInt,
				addressArrayStartPosition + INPUT_ENTRY_BYTES_LENGTH,
				input);
		final var accountIDs = addresses.stream().map(a -> convertAddressToAccountID(a)).collect(Collectors.toList());

		// PARSING AMOUNT ARRAY
		final var amountArrayStartPosition = addressArrayStartPositionBytes.toBigInteger().intValue() + FUNCTION_SELECTOR_BYTES_LENGTH + addressArrayByteLengthWithHeader;
		final var amountArraySize = input.slice(amountArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();
		final var amountArraySizeInt = amountArraySize.intValue();

		final var amounts = decodeAmountsArray(amountArraySizeInt, amountArrayStartPosition, input);

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, tokenID);
		decodedArguments.put(2, accountIDs);
		decodedArguments.put(3, amounts);
		return decodedArguments;
	}


	public static Map<Integer, Object> decodeArgumentsForTransferToken(final Bytes input) {
		final Bytes tokenAddress = Address.wrap(input.slice(16, ADDRESS_BYTES_LENGTH));
		final Bytes fromAddress = Address.wrap(input.slice(48, ADDRESS_BYTES_LENGTH));
		final Bytes toAddress = Address.wrap(input.slice(80, ADDRESS_BYTES_LENGTH));
		final BigInteger amount = input.slice(100, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();

		final var token = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());
		final var from = EntityIdUtils.accountParsedFromSolidityAddress(fromAddress.toArray());
		final var to = EntityIdUtils.accountParsedFromSolidityAddress(toAddress.toArray());

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, token);
		decodedArguments.put(2, from);
		decodedArguments.put(3, to);
		decodedArguments.put(4, amount);
		return decodedArguments;
	}

	public static Map<Integer, Object> decodeArgumentsForTransferNFTs(final Bytes input) {
		// PARSING TOKEN ADDRESS
		final var tokenAddress = Address.wrap(input.slice(ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
		final var tokenID = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());

		// PARSING ACCOUNT ADDRESS ARRAY - SENDERS
		final var senderArrayStartPositionStartIndex =
				ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH + ADDRESS_BYTES_LENGTH;
		final var senderArrayStartPositionBytes = input.slice(senderArrayStartPositionStartIndex,
				INPUT_ENTRY_BYTES_LENGTH);
		final var senderArrayStartPosition =
				senderArrayStartPositionBytes.toBigInteger().intValue() + FUNCTION_SELECTOR_BYTES_LENGTH;
		final var senderArraySizeInt = input.slice(senderArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger().intValue();
		final var senderArrayByteLength = senderArraySizeInt * INPUT_ENTRY_BYTES_LENGTH;
		final var senderArrayByteLengthWithHeader = senderArrayByteLength + INPUT_ENTRY_BYTES_LENGTH;

		final var senderAddresses = decodeAddressArray(senderArraySizeInt, senderArrayByteLengthWithHeader,
				input);
		final var sendersIDs = senderAddresses.stream().map(a -> convertAddressToAccountID(a)).collect(Collectors.toList());

		// PARSING ACCOUNT ADDRESS ARRAY - RECEIVERS
		final var receiverArrayStartPositionStartIndex =
				senderArrayStartPositionStartIndex + INPUT_ENTRY_BYTES_LENGTH;
		final var receiverArrayStartPositionBytes = input.slice(receiverArrayStartPositionStartIndex,
				INPUT_ENTRY_BYTES_LENGTH);
		final var receiverArrayStartPosition =
				senderArrayStartPosition + FUNCTION_SELECTOR_BYTES_LENGTH + senderArrayByteLengthWithHeader;
		final var receiverArraySizeInt = input.slice(receiverArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger().intValue();
		final var receiverArrayByteLength = receiverArraySizeInt * INPUT_ENTRY_BYTES_LENGTH;
		final var receiverArrayByteLengthWithHeader = receiverArrayByteLength + INPUT_ENTRY_BYTES_LENGTH;

		final var receiverAddresses = decodeAddressArray(receiverArraySizeInt, receiverArrayStartPosition,
				input);
		final var receiversIDs = receiverAddresses.stream().map(a -> convertAddressToAccountID(a)).collect(Collectors.toList());

		// PARSING SERIAL NUMBERS ARRAY
		final var sNumberArrayStartPosition =
				senderArrayStartPosition + FUNCTION_SELECTOR_BYTES_LENGTH + senderArrayByteLengthWithHeader + receiverArrayByteLengthWithHeader;
		final var sNumberArraySizeInt =
				input.slice(sNumberArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger().intValue();
		final var serialNumbers = decodeAmountsArray(sNumberArraySizeInt, sNumberArrayStartPosition,
				input);

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, tokenID);
		decodedArguments.put(2, sendersIDs);
		decodedArguments.put(3, receiversIDs);
		decodedArguments.put(4, serialNumbers);
		return decodedArguments;
	}

	public static Map<Integer, Object> decodeArgumentsForTransferNFT(final Bytes input) {
		final Bytes tokenAddress = Address.wrap(input.slice(16, ADDRESS_BYTES_LENGTH));
		final Bytes fromAddress = Address.wrap(input.slice(48, ADDRESS_BYTES_LENGTH));
		final Bytes toAddress = Address.wrap(input.slice(80, ADDRESS_BYTES_LENGTH));
		final BigInteger sNumber = input.slice(100, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();

		final var token = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());
		final var from = EntityIdUtils.accountParsedFromSolidityAddress(fromAddress.toArray());
		final var to = EntityIdUtils.accountParsedFromSolidityAddress(toAddress.toArray());

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, token);
		decodedArguments.put(2, from);
		decodedArguments.put(3, to);
		decodedArguments.put(4, sNumber);
		return decodedArguments;
	}

	public static Map<Integer, Object> decodeArgumentsForMintToken(final Bytes input) {
		final Bytes tokenAddress = Address.wrap(input.slice(16, ADDRESS_BYTES_LENGTH));
		final BigInteger amount = input.slice(36, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();

		final var token = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());

		// PARSING BYTES - METADATA
		final var bytesSize = input.slice(68,
				INPUT_ENTRY_BYTES_LENGTH).toBigInteger().intValue();
		final var bytesStartPosition = 132;
		final var bytes = input.slice(bytesStartPosition, bytesSize + INPUT_ENTRY_BYTES_LENGTH);
		final var bytesStringified = new String(bytes.toArray());

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, token);
		decodedArguments.put(2, amount);
		decodedArguments.put(3, bytesStringified);
		return decodedArguments;
	}

	public static Map<Integer, Object> decodeArgumentsForBurnToken(final Bytes input) {
		final var tokenAddress = Address.wrap(input.slice(16, ADDRESS_BYTES_LENGTH));
		final var amount = input.slice(48, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();

		final var token = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());

		// PARSING SERIAL NUMBERS ARRAY
		final var sNumberArrayStartPosition = 80;
		final var sNumberArraySizeInt =
				input.slice(sNumberArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger().intValue();
		final var serialNumbers = decodeAmountsArray(sNumberArraySizeInt, sNumberArrayStartPosition + INPUT_ENTRY_BYTES_LENGTH,
				input);

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, token);
		decodedArguments.put(2, amount);
		decodedArguments.put(3, serialNumbers);
		return decodedArguments;
	}

	public static Map<Integer, Object> decodeArgumentsForAssociateDissociateToken(final Bytes input) {
		final Bytes accountAddress = Address.wrap(input.slice(16, ADDRESS_BYTES_LENGTH));
		final Bytes tokenAddress = Address.wrap(input.slice(48, ADDRESS_BYTES_LENGTH));

		final var accountID = EntityIdUtils.accountParsedFromSolidityAddress(accountAddress.toArray());
		final var tokenID = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArrayUnsafe());

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, accountID);
		decodedArguments.put(2, tokenID);
		return decodedArguments;
	}

	public static Map<Integer, Object> decodeArgumentsForAssociateDissociateTokens(final Bytes input) {
		final Bytes accountAddress = Address.wrap(input.slice(16, ADDRESS_BYTES_LENGTH));
		final var accountID = EntityIdUtils.accountParsedFromSolidityAddress(accountAddress.toArray());

		// PARSING TOKEN ADDRESS ARRAY
		final var tokenArrayStartPositionStartIndex =
				ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH + ADDRESS_BYTES_LENGTH;
		final var tokenArrayStartPositionBytes = input.slice(tokenArrayStartPositionStartIndex,
				INPUT_ENTRY_BYTES_LENGTH);
		final var tokenArrayStartPosition =
				tokenArrayStartPositionBytes.toBigInteger().intValue() + FUNCTION_SELECTOR_BYTES_LENGTH;
		final var tokenArraySizeInt = input.slice(tokenArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger().intValue();

		final var tokenAddresses = decodeAddressArray(tokenArraySizeInt,
				tokenArrayStartPosition + INPUT_ENTRY_BYTES_LENGTH,
				input);
		final var tokenIDs =
				tokenAddresses.stream().map(a -> convertAddressToTokenID(a)).collect(Collectors.toList());

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, accountID);
		decodedArguments.put(2, tokenIDs);
		return decodedArguments;
	}

	private static List<BigInteger> decodeAmountsArray(int arraySizeWhole, int arrayStartPosition,
													   final Bytes input) {
		final List<BigInteger> amounts = new ArrayList<>();
		var arraySizeChunk = Math.min(arraySizeWhole, MAX_STATIC_ARRAY_LENGTH);
		do {
			amounts.addAll(decodeAmountsArrayLimitBySize(arraySizeChunk, arrayStartPosition, arraySizeChunk * INPUT_ENTRY_BYTES_LENGTH,
					input));
			final var amountsSize = amounts.size();
			final var amountsLeft = arraySizeWhole - amountsSize;
			if (amountsLeft != 0 && amountsLeft % MAX_STATIC_ARRAY_LENGTH == 0) {
				arraySizeChunk = MAX_STATIC_ARRAY_LENGTH;
			} else {
				arraySizeChunk = amountsLeft % MAX_STATIC_ARRAY_LENGTH;
			}
			arrayStartPosition += MAX_STATIC_ARRAY_LENGTH * INPUT_ENTRY_BYTES_LENGTH;
		} while (amounts.size() != arraySizeWhole);

		return amounts;
	}

	private static List<BigInteger> decodeAmountsArrayLimitBySize(int amountArraySizeInt, int amountArrayStartPosition,
																  int amountArrayByteLength, final Bytes input) {
		final List<TypeReference<Type>> amountOutputParameters = new ArrayList<>(1);
		amountOutputParameters.add(
				(TypeReference)
						new TypeReference.StaticArrayTypeReference<StaticArray<org.web3j.abi.datatypes.generated.Int64>>(amountArraySizeInt) {
						});

		final var amountsArray = FunctionReturnDecoder.decode(
				input.slice(amountArrayStartPosition + INPUT_ENTRY_BYTES_LENGTH,
						amountArrayByteLength).toString(),
				amountOutputParameters);

		return convertInt64ArrayToBigInteger(amountsArray);
	}

	private static List<Address> decodeAddressArray(int arraySizeWhole, int arrayStartPosition,
													final Bytes input) {
		final List<Address> addresses = new ArrayList<>();
		var arraySizeChunk = Math.min(arraySizeWhole, MAX_STATIC_ARRAY_LENGTH);
		do {
			addresses.addAll(decodeAddressArrayLimitBySize(arraySizeChunk,
					arrayStartPosition,
					arraySizeChunk * INPUT_ENTRY_BYTES_LENGTH, input));
			final var accountIDsSize = addresses.size();
			final var accountsLeft = arraySizeWhole - accountIDsSize;
			if (accountsLeft != 0 && accountsLeft % MAX_STATIC_ARRAY_LENGTH == 0) {
				arraySizeChunk = MAX_STATIC_ARRAY_LENGTH;
			} else {
				arraySizeChunk = accountsLeft % MAX_STATIC_ARRAY_LENGTH;
			}
			arrayStartPosition += MAX_STATIC_ARRAY_LENGTH * INPUT_ENTRY_BYTES_LENGTH;
		} while (addresses.size() != arraySizeWhole);

		return addresses;
	}

	private static List<Address> decodeAddressArrayLimitBySize(int arraySizeLimit,
															   int arrayStartPosition,
															   int addressArrayByteLength,
															   final Bytes input) {
		final List<TypeReference<Type>> outputParameters = new ArrayList<>(1);
		outputParameters.add(
				(TypeReference)
						new TypeReference.StaticArrayTypeReference<StaticArray<Bytes32>>(arraySizeLimit) {
						});

		final var addressArray = FunctionReturnDecoder.decode(
				input.slice(arrayStartPosition,
						addressArrayByteLength).toString(),
				outputParameters);

		final List<Address> addresses = new ArrayList<>();
		for (final var address : ((StaticArray) addressArray.get(0)).getValue()) {
			final var addressBytes32 = ((Bytes32) address);
			final var addressBytes = Bytes.wrap(addressBytes32.getValue());
			final var addressSliced = Address.wrap(addressBytes.slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
			addresses.add(addressSliced);
		}

		return addresses;
	}

	private static List<BigInteger> convertInt64ArrayToBigInteger(final List<Type> amounts) {
		final List<BigInteger> amountsConverted = new ArrayList<>();
		for (final var amount : ((StaticArray) amounts.get(0)).getValue()) {
			amountsConverted.add(((Int64) amount).getValue());
		}

		return amountsConverted;
	}

	private static AccountID convertAddressToAccountID(final Address address) {
		return EntityIdUtils.accountParsedFromSolidityAddress(address.toArray());
	}

	private static TokenID convertAddressToTokenID(final Address address) {
		return EntityIdUtils.tokenParsedFromSolidityAddress(address.toArray());
	}
}