package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.web3j.abi.DefaultFunctionReturnDecoder;
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

public final class PrecompileArgumentsDecoder {

	private static final FunctionReturnDecoder functionReturnDecoder = new DefaultFunctionReturnDecoder();
	private static int INPUT_ENTRY_BYTES_LENGTH = 32;
	private static int ADDRESS_BYTES_LENGTH = 20;
	private static int ADDRESS_SKIP_BYTES_LENGTH = 12;
	private static int FUNCTION_SELECTOR_BYTES_LENGTH = 4;

	private PrecompileArgumentsDecoder() {
	}

	public static Map<Integer, Object> decodeArgumentsForTransferTokens(final Bytes input,
													final AccountStore accountStore, final TypedTokenStore tokenStore) {

		// PARSING TOKEN ADDRESS
		final var tokenAddress = Address.wrap(input.slice(ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
		final var tokenId = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());
		final var token = tokenStore.loadToken(Id.fromGrpcToken(tokenId));

		// PARSING ACCOUNT ADDRESS ARRAY
		final var addressArrayStartPositionStartIndex =
				ADDRESS_SKIP_BYTES_LENGTH + FUNCTION_SELECTOR_BYTES_LENGTH + ADDRESS_BYTES_LENGTH;
		final var addressArrayStartPositionBytes = input.slice(addressArrayStartPositionStartIndex, INPUT_ENTRY_BYTES_LENGTH);
		final var addressArrayStartPosition = addressArrayStartPositionBytes.toBigInteger().intValue() + FUNCTION_SELECTOR_BYTES_LENGTH;
		final var addressArraySize = input.slice(addressArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();
		final var addressArraySizeInt = addressArraySize.intValue();
		final var addressArrayByteLength = addressArraySizeInt * INPUT_ENTRY_BYTES_LENGTH;
		final var addressArrayByteLengthWithHeader = addressArrayByteLength + INPUT_ENTRY_BYTES_LENGTH;

		final List<TypeReference<Type>> outputParameters = new ArrayList<>(1);
		outputParameters.add(
				(TypeReference)
						new TypeReference.StaticArrayTypeReference<StaticArray<Bytes32>>(addressArraySizeInt) {});

		final var addressArray = FunctionReturnDecoder.decode(
				input.slice(addressArrayStartPosition + INPUT_ENTRY_BYTES_LENGTH,
						addressArrayByteLength).toString(),
				outputParameters);

		final List<Account> accounts = new ArrayList<>();
		for (final var address : ((StaticArray) addressArray.get(0)).getValue()) {
			final var addressBytes32 = ((Bytes32) address);
			final var addressBytes = Bytes.wrap(addressBytes32.getValue());
			final var addressSliced = Address.wrap(addressBytes.slice(ADDRESS_SKIP_BYTES_LENGTH, ADDRESS_BYTES_LENGTH));
			final var accountId = EntityIdUtils.accountParsedFromSolidityAddress(addressSliced.toArray());
			final var account = accountStore.loadAccount(Id.fromGrpcAccount(accountId));
			accounts.add(account);
		}

		// PARSING AMOUNT ARRAY
//		final var amountArrayStartPositionStartIndex =
//				input.slice(72, INPUT_ENTRY_BYTES_LENGTH).toBigInteger().intValue();
		final var amountArrayStartPosition = addressArrayStartPositionBytes.toBigInteger().intValue() + FUNCTION_SELECTOR_BYTES_LENGTH  + addressArrayByteLengthWithHeader;
		final var amountArraySize = input.slice(amountArrayStartPosition, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();
		final var amountArraySizeInt = amountArraySize.intValue();
		final var amountArrayByteLength = amountArraySizeInt * INPUT_ENTRY_BYTES_LENGTH;

		final List<TypeReference<Type>> amountOutputParameters = new ArrayList<>(1);
		amountOutputParameters.add(
				(TypeReference)
						new TypeReference.StaticArrayTypeReference<StaticArray<org.web3j.abi.datatypes.generated.Int64>>(amountArraySizeInt) {});

		final var amountArray = FunctionReturnDecoder.decode(
				input.slice(amountArrayStartPosition + INPUT_ENTRY_BYTES_LENGTH,
						amountArrayByteLength).toString(),
				amountOutputParameters);
		final List<BigInteger> amounts = new ArrayList<>();
		for(final var amount: ((StaticArray) amountArray.get(0)).getValue()) {
			amounts.add(((Int64) amount).getValue());
		}

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, token);
		decodedArguments.put(2, accounts);
		decodedArguments.put(3, amounts);
		return decodedArguments;
	}

	public static Map<Integer, Object> decodeArgumentsForTransferToken(final Bytes input,
																		final AccountStore accountStore, final TypedTokenStore tokenStore) {
		final Bytes tokenAddress = Address.wrap(input.slice(16, ADDRESS_BYTES_LENGTH));
		final Bytes fromAddress = Address.wrap(input.slice(48, ADDRESS_BYTES_LENGTH));
		final Bytes toAddress = Address.wrap(input.slice(80, ADDRESS_BYTES_LENGTH));
		final BigInteger amount = input.slice(100, INPUT_ENTRY_BYTES_LENGTH).toBigInteger();

		final var from = EntityIdUtils.accountParsedFromSolidityAddress(fromAddress.toArray());
		final var token = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress.toArray());
		final var to = EntityIdUtils.accountParsedFromSolidityAddress(toAddress.toArray());

		final Map<Integer, Object> decodedArguments = new HashMap<>();
		decodedArguments.put(1, token);
		decodedArguments.put(2, from);
		decodedArguments.put(3, to);
		decodedArguments.put(4, amount);
		return decodedArguments;
	}
}