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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class EncodingFacade {
	private static final long[] NO_MINTED_SERIAL_NUMBERS = new long[0];
	private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
	private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");
	private static final TupleType totalSupplyType = TupleType.parse("(uint256)");
	private static final TupleType balanceOfType = TupleType.parse("(uint256)");
	private static final TupleType decimalsType = TupleType.parse("(uint8)");
	private static final TupleType ownerOfType = TupleType.parse("(address)");
	private static final TupleType nameType = TupleType.parse("(string)");
	private static final TupleType symbolType = TupleType.parse("(string)");
	private static final TupleType tokenUriType = TupleType.parse("(string)");
	private static final TupleType ercTransferType = TupleType.parse("(bool)");

	@Inject
	public EncodingFacade() {
		/* For Dagger2 */
	}

	public Bytes encodeTokenUri(final String tokenUri) {
		return functionResultBuilder()
				.forFunction(FunctionType.TOKEN_URI)
				.withTokenUri(tokenUri)
				.build();
	}

	public Bytes encodeSymbol(final String symbol) {
		return functionResultBuilder()
				.forFunction(FunctionType.SYMBOL)
				.withSymbol(symbol)
				.build();
	}

	public Bytes encodeName(final String name) {
		return functionResultBuilder()
				.forFunction(FunctionType.NAME)
				.withName(name)
				.build();
	}

	public Bytes encodeOwner(final Address address) {
		return functionResultBuilder()
				.forFunction(FunctionType.OWNER)
				.withOwner(address)
				.build();
	}

	public Bytes encodeBalance(final long balance) {
		return functionResultBuilder()
				.forFunction(FunctionType.BALANCE)
				.withBalance(balance)
				.build();
	}

	public Bytes encodeDecimals(final int decimals) {
		return functionResultBuilder()
				.forFunction(FunctionType.DECIMALS)
				.withDecimals(decimals)
				.build();
	}

	public Bytes encodeTotalSupply(final long totalSupply) {
		return functionResultBuilder()
				.forFunction(FunctionType.TOTAL_SUPPLY)
				.withTotalSupply(totalSupply)
				.build();
	}

	public Bytes encodeMintSuccess(final long totalSupply, final long[] serialNumbers) {
		return functionResultBuilder()
				.forFunction(FunctionType.MINT)
				.withStatus(SUCCESS.getNumber())
				.withTotalSupply(totalSupply)
				.withSerialNumbers(serialNumbers != null ? serialNumbers : NO_MINTED_SERIAL_NUMBERS)
				.build();
	}

	public Bytes encodeMintFailure(final ResponseCodeEnum status) {
		return functionResultBuilder()
				.forFunction(FunctionType.MINT)
				.withStatus(status.getNumber())
				.withTotalSupply(0L)
				.withSerialNumbers(NO_MINTED_SERIAL_NUMBERS)
				.build();
	}

	public Bytes encodeBurnSuccess(final long totalSupply) {
		return functionResultBuilder()
				.forFunction(FunctionType.BURN)
				.withStatus(SUCCESS.getNumber())
				.withTotalSupply(totalSupply)
				.build();
	}

	public Bytes encodeBurnFailure(final ResponseCodeEnum status) {
		return functionResultBuilder()
				.forFunction(FunctionType.BURN)
				.withStatus(status.getNumber())
				.withTotalSupply(0L)
				.build();
	}

	public Bytes ercFungibleTransfer(final boolean ercFungibleTransferStatus) {
		return functionResultBuilder()
				.forFunction(FunctionType.ERC_TRANSFER)
				.withErcFungibleTransferStatus(ercFungibleTransferStatus)
				.withTotalSupply(0L)
				.build();
	}

	private enum FunctionType {
		MINT, BURN, TOTAL_SUPPLY, DECIMALS, BALANCE, OWNER, TOKEN_URI, NAME, SYMBOL, ERC_TRANSFER
	}

	private FunctionResultBuilder functionResultBuilder() {
		return new FunctionResultBuilder();
	}

	private static class FunctionResultBuilder {
		private FunctionType functionType;
		private TupleType tupleType;
		private int status;
		private boolean ercFungibleTransferStatus;
		private long totalSupply;
		private long balance;
		private long[] serialNumbers;
		private int decimals;
		private Address owner;
		private String name;
		private String symbol;
		private String metadata;

		private FunctionResultBuilder forFunction(final FunctionType functionType) {
			switch(functionType) {
				case MINT -> tupleType = mintReturnType;
				case BURN -> tupleType = burnReturnType;
				case TOTAL_SUPPLY -> tupleType = totalSupplyType;
				case DECIMALS -> tupleType = decimalsType;
				case BALANCE -> tupleType = balanceOfType;
				case OWNER -> tupleType = ownerOfType;
				case NAME -> tupleType = nameType;
				case SYMBOL -> tupleType = symbolType;
				case TOKEN_URI -> tupleType = tokenUriType;
				case ERC_TRANSFER -> tupleType = ercTransferType;
			}

			this.functionType = functionType;
			return this;
		}

		private FunctionResultBuilder withStatus(final int status) {
			this.status = status;
			return this;
		}

		private FunctionResultBuilder withTotalSupply(final long totalSupply) {
			this.totalSupply = totalSupply;
			return this;
		}

		private FunctionResultBuilder withSerialNumbers(final long[] serialNumbers) {
			this.serialNumbers = serialNumbers;
			return this;
		}

		private FunctionResultBuilder withDecimals(final int decimals) {
			this.decimals = decimals;
			return this;
		}

		private FunctionResultBuilder withBalance(final long balance) {
			this.balance = balance;
			return this;
		}

		private FunctionResultBuilder withOwner(final Address address) {
			this.owner = address;
			return this;
		}

		private FunctionResultBuilder withName(final String name) {
			this.name = name;
			return this;
		}

		private FunctionResultBuilder withSymbol(final String symbol) {
			this.symbol = symbol;
			return this;
		}

		private FunctionResultBuilder withTokenUri(final String tokenUri) {
			this.metadata = tokenUri;
			return this;
		}

		private FunctionResultBuilder withErcFungibleTransferStatus(final boolean ercFungibleTransferStatus) {
			this.ercFungibleTransferStatus = ercFungibleTransferStatus;
			return this;
		}

		private Bytes build() {
			Tuple result;

			switch(functionType) {
				case MINT -> result = Tuple.of(status, BigInteger.valueOf(totalSupply), serialNumbers);
				case BURN -> result = Tuple.of(status, BigInteger.valueOf(totalSupply));
				case TOTAL_SUPPLY -> result = Tuple.of(BigInteger.valueOf(totalSupply));
				case DECIMALS -> result = Tuple.of(decimals);
				case BALANCE -> result = Tuple.of(BigInteger.valueOf(balance));
				case OWNER -> result = Tuple.of(convertBesuAddressToHeadlongAddress(owner));
				case NAME -> result = Tuple.of(name);
				case SYMBOL -> result = Tuple.of(symbol);
				case TOKEN_URI -> result = Tuple.of(metadata);
				case ERC_TRANSFER -> result = Tuple.of(ercFungibleTransferStatus);
				default -> result = Tuple.of(status);
			}

			return Bytes.wrap(tupleType.encode(result).array());
		}
	}

	public static Log generateLog(final Address logger,  boolean indexed, final Object... params) {
		final var paramsConverted = convertParamsForLog(params);
		final var tuple = Tuple.of(paramsConverted.toArray());
		final var tupleType = generateTupleType(params);
		if(indexed) {
			return new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), generateLogTopics(params));
		} else {
			return new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), new ArrayList<>());
		}
	}

	private static List<Object> convertParamsForLog(final Object... params) {
		final List<Object> paramsConverted = new ArrayList<>();
		for (final var param : params) {
			if (param instanceof Address) {
				paramsConverted.add(convertBesuAddressToHeadlongAddress((Address) param));
			} else if (param instanceof Long) {
				paramsConverted.add(BigInteger.valueOf((Long) param));
			} else {
				paramsConverted.add(param);
			}
		}

		return paramsConverted;
	}

	private static TupleType generateTupleType(final Object... params) {
		final StringBuilder tupleTypes = new StringBuilder("(");
		for (final var param : params) {
			if (param instanceof Address) {
				tupleTypes.append("address,");
			} else if (param instanceof BigInteger || param instanceof Long) {
				tupleTypes.append("uint256,");
			} else if (param instanceof Boolean) {
				tupleTypes.append("boolean,");
			}
		}
		//Delete last comma
		tupleTypes.deleteCharAt(tupleTypes.length()-1);
		tupleTypes.append(")");

		return TupleType.parse(tupleTypes.toString());
	}

	private static List<LogTopic> generateLogTopics(final Object... params) {
		final List<LogTopic> logTopics = new ArrayList<>();
		for (final var param : params) {
			if (param instanceof Address) {
				byte[] array = ((Address) param).toArray();
				logTopics.add(LogTopic.wrap(Bytes.wrap(expandByteArrayTo32Length(array))));
			} else if (param instanceof BigInteger) {
				byte[] array = ((BigInteger) param).toByteArray();
				logTopics.add(LogTopic.wrap(Bytes.wrap(expandByteArrayTo32Length(array))));
			} else if (param instanceof Long) {
				byte[] array = BigInteger.valueOf((Long) param).toByteArray();
				logTopics.add(LogTopic.wrap(Bytes.wrap(expandByteArrayTo32Length(array))));
			} else if (param instanceof Boolean) {
				boolean value = (Boolean) param;
				byte[] array = new byte[]{(byte) (value?1:0)};
				logTopics.add(LogTopic.wrap(Bytes.wrap(expandByteArrayTo32Length(array))));
			}
		}
		return logTopics;
	}

	private static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
		byte[] expandedArray = new byte[32];

		System.arraycopy(bytesToExpand, 0, expandedArray, expandedArray.length-bytesToExpand.length, bytesToExpand.length);
		return expandedArray;
	}

	private static com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(final Address addressToBeConverted) {
		final com.esaulpaugh.headlong.abi.Address convertedAddress =
				com.esaulpaugh.headlong.abi.Address.wrap(com.esaulpaugh.headlong.abi.Address.toChecksumAddress(((Address) addressToBeConverted).toBigInteger()));
		return convertedAddress;
	}
}