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
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class EncodingFacade {
	private static final long[] NO_MINTED_SERIAL_NUMBERS = new long[0];
	private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
	private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");
	private static final TupleType totalSupplyOfType = TupleType.parse("(uint256)");
	private static final TupleType balanceOfType = TupleType.parse("(uint256)");
	private static final TupleType decimalsType = TupleType.parse("(uint8)");
	private static final TupleType transferType = TupleType.parse("(int32)");
	private static final TupleType ownerOfType = TupleType.parse("(bytes32)");
	private static final TupleType nameType = TupleType.parse("(string)");
	private static final TupleType symbolType = TupleType.parse("(string)");
	private static final TupleType tokenUriType = TupleType.parse("(string)");

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

	public Bytes encodeTransfer() {
		return functionResultBuilder()
				.forFunction(FunctionType.TRANSFER)
				.withStatus(SUCCESS.getNumber())
				.build();
	}

	public Bytes encodeBalance(final long balance) {
		return functionResultBuilder()
				.forFunction(FunctionType.BALANCE)
				.withBalance(balance)
				.build();
	}

	public Bytes encodeDecimals(final short decimals) {
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

	private enum FunctionType {
		MINT, BURN, TOTAL_SUPPLY, DECIMALS, BALANCE, TRANSFER, OWNER, TOKEN_URI, NAME, SYMBOL
	}

	private FunctionResultBuilder functionResultBuilder() {
		return new FunctionResultBuilder();
	}

	private static class FunctionResultBuilder {
		private FunctionType functionType;
		private TupleType tupleType;
		private int status;
		private long totalSupply;
		private long balance;
		private long[] serialNumbers;
		private short decimals;
		private Address owner;
		private String name;
		private String symbol;
		private String metadata;

		private FunctionResultBuilder forFunction(final FunctionType functionType) {
			if (functionType == FunctionType.MINT) {
				tupleType = mintReturnType;
			} else if (functionType == FunctionType.BURN) {
				tupleType = burnReturnType;
			} else if (functionType == FunctionType.TOTAL_SUPPLY) {
				tupleType = totalSupplyOfType;
			} else if (functionType == FunctionType.DECIMALS) {
				tupleType = decimalsType;
			} else if (functionType == FunctionType.BALANCE) {
				tupleType = balanceOfType;
			} else if (functionType == FunctionType.TRANSFER) {
				tupleType = transferType;
			} else if (functionType == FunctionType.OWNER) {
				tupleType = ownerOfType;
			} else if (functionType == FunctionType.NAME) {
				tupleType = nameType;
			} else if (functionType == FunctionType.SYMBOL) {
				tupleType = symbolType;
			} else if (functionType == FunctionType.TOKEN_URI) {
				tupleType = tokenUriType;
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

		private FunctionResultBuilder withDecimals(final short decimals) {
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

		private Bytes build() {
			Tuple result;
			if (functionType == FunctionType.MINT) {
				result = Tuple.of(status, BigInteger.valueOf(totalSupply), serialNumbers);
			} else if (functionType == FunctionType.TOTAL_SUPPLY) {
				result = Tuple.of(BigInteger.valueOf(totalSupply));
			} else if (functionType == FunctionType.DECIMALS) {
				result = Tuple.of(decimals);
			} else if (functionType == FunctionType.BALANCE) {
				result = Tuple.of(BigInteger.valueOf(balance));
			} else if (functionType == FunctionType.TRANSFER) {
				result = Tuple.of(BigInteger.valueOf(status));
			} else if (functionType == FunctionType.OWNER) {
				result = Tuple.of(owner);
			} else if (functionType == FunctionType.NAME) {
				result = Tuple.of(name);
			} else if (functionType == FunctionType.SYMBOL) {
				result = Tuple.of(symbol);
			} else if (functionType == FunctionType.TOKEN_URI) {
				result = Tuple.of(metadata);
			} else {
				result = Tuple.of(status, BigInteger.valueOf(totalSupply));
			}
			return Bytes.wrap(tupleType.encode(result).array());
		}
	}
}
