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
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.ALLOWANCE;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.APPROVE;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.BALANCE;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.BURN;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.DECIMALS;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.ERC_TRANSFER;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.GET_APPROVED;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.IS_APPROVED_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.MINT;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.NAME;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.OWNER;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.SYMBOL;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.TOKEN_URI;
import static com.hedera.services.store.contracts.precompile.EncodingFacade.FunctionType.TOTAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class EncodingFacade {
	private static final long[] NO_MINTED_SERIAL_NUMBERS = new long[0];
	private static final String STRING_RETURN_TYPE = "(string)";
	public static final String UINT256_RETURN_TYPE = "(uint256)";
	public static final String BOOL_RETURN_TYPE = "(bool)";
	private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
	private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");
	private static final TupleType totalSupplyType = TupleType.parse(UINT256_RETURN_TYPE);
	private static final TupleType balanceOfType = TupleType.parse(UINT256_RETURN_TYPE);
	private static final TupleType allowanceOfType = TupleType.parse(UINT256_RETURN_TYPE);
	private static final TupleType approveOfType = TupleType.parse(BOOL_RETURN_TYPE);
	private static final TupleType decimalsType = TupleType.parse("(uint8)");
	private static final TupleType ownerOfType = TupleType.parse("(address)");
	private static final TupleType getApprovedType = TupleType.parse("(address)");
	private static final TupleType nameType = TupleType.parse(STRING_RETURN_TYPE);
	private static final TupleType symbolType = TupleType.parse(STRING_RETURN_TYPE);
	private static final TupleType tokenUriType = TupleType.parse(STRING_RETURN_TYPE);
	private static final TupleType ercTransferType = TupleType.parse(BOOL_RETURN_TYPE);
	private static final TupleType isApprovedForAllType = TupleType.parse(BOOL_RETURN_TYPE);

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

	public Bytes encodeGetApproved(final Address approved) {
		return functionResultBuilder()
				.forFunction(FunctionType.GET_APPROVED)
				.withApproved(approved)
				.build();
	}

	public Bytes encodeBalance(final long balance) {
		return functionResultBuilder()
				.forFunction(FunctionType.BALANCE)
				.withBalance(balance)
				.build();
	}

	public Bytes encodeAllowance(final long allowance) {
		return functionResultBuilder()
				.forFunction(FunctionType.ALLOWANCE)
				.withAllowance(allowance)
				.build();
	}

	public Bytes encodeApprove(final boolean approve) {
		return functionResultBuilder()
				.forFunction(FunctionType.APPROVE)
				.withApprove(approve)
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
				.forFunction(MINT)
				.withStatus(SUCCESS.getNumber())
				.withTotalSupply(totalSupply)
				.withSerialNumbers(serialNumbers != null ? serialNumbers : NO_MINTED_SERIAL_NUMBERS)
				.build();
	}

	public Bytes encodeMintFailure(final ResponseCodeEnum status) {
		return functionResultBuilder()
				.forFunction(MINT)
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

	public Bytes encodeEcFungibleTransfer(final boolean ercFungibleTransferStatus) {
		return functionResultBuilder()
				.forFunction(FunctionType.ERC_TRANSFER)
				.withErcFungibleTransferStatus(ercFungibleTransferStatus)
				.build();
	}

	public Bytes encodeIsApprovedForAll(final boolean isApprovedForAllStatus) {
		return functionResultBuilder()
				.forFunction(FunctionType.IS_APPROVED_FOR_ALL)
				.withIsApprovedForAllStatus(isApprovedForAllStatus)
				.build();
	}

	protected enum FunctionType {
		MINT, BURN, TOTAL_SUPPLY, DECIMALS, BALANCE, OWNER, TOKEN_URI, NAME, SYMBOL, ERC_TRANSFER, ALLOWANCE, APPROVE, GET_APPROVED, IS_APPROVED_FOR_ALL
	}

	private FunctionResultBuilder functionResultBuilder() {
		return new FunctionResultBuilder();
	}

	private static class FunctionResultBuilder {
		private FunctionType functionType;
		private TupleType tupleType;
		private int status;
		private boolean ercFungibleTransferStatus;
		private boolean isApprovedForAllStatus;
		private long totalSupply;
		private long balance;
		private long allowance;
		private boolean approve;
		private long[] serialNumbers;
		private int decimals;
		private Address owner;
		private Address approved;
		private String name;
		private String symbol;
		private String metadata;

		private FunctionResultBuilder forFunction(final FunctionType functionType) {
			if (functionType == FunctionType.MINT) {
				tupleType = mintReturnType;
			} else if (functionType == FunctionType.BURN) {
				tupleType = burnReturnType;
			} else if (functionType == FunctionType.TOTAL_SUPPLY) {
				tupleType = totalSupplyType;
			} else if (functionType == FunctionType.DECIMALS) {
				tupleType = decimalsType;
			} else if (functionType == FunctionType.BALANCE) {
				tupleType = balanceOfType;
			} else if (functionType == FunctionType.ALLOWANCE) {
				tupleType = allowanceOfType;
			} else if (functionType == FunctionType.APPROVE) {
				tupleType = approveOfType;
			} else if (functionType == FunctionType.OWNER) {
				tupleType = ownerOfType;
			} else if (functionType == FunctionType.GET_APPROVED) {
				tupleType = getApprovedType;
			} else if (functionType == FunctionType.NAME) {
				tupleType = nameType;
			} else if (functionType == FunctionType.SYMBOL) {
				tupleType = symbolType;
			} else if (functionType == FunctionType.TOKEN_URI) {
				tupleType = tokenUriType;
			} else if (functionType == FunctionType.ERC_TRANSFER) {
				tupleType = ercTransferType;
			} else if (functionType == IS_APPROVED_FOR_ALL) {
				tupleType = isApprovedForAllType;
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

		private FunctionResultBuilder withAllowance(final long allowance) {
			this.allowance = allowance;
			return this;
		}

		private FunctionResultBuilder withApprove(final boolean approve) {
			this.approve = approve;
			return this;
		}

		private FunctionResultBuilder withOwner(final Address address) {
			this.owner = address;
			return this;
		}

		private FunctionResultBuilder withApproved(final Address approved) {
			this.approved = approved;
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

		private FunctionResultBuilder withIsApprovedForAllStatus(final boolean isApprovedForAllStatus) {
			this.isApprovedForAllStatus = isApprovedForAllStatus;
			return this;
		}

		private Bytes build() {
			Tuple result = Tuple.of(status);

			if (MINT.equals(functionType)) {
				result = Tuple.of(status, BigInteger.valueOf(totalSupply), serialNumbers);
			} else if (BURN.equals(functionType)) {
				result = Tuple.of(status, BigInteger.valueOf(totalSupply));
			} else if (TOTAL_SUPPLY.equals(functionType)) {
				result = Tuple.of(BigInteger.valueOf(totalSupply));
			} else if (DECIMALS.equals(functionType)) {
				result = Tuple.of(decimals);
			} else if (BALANCE.equals(functionType)) {
				result = Tuple.of(BigInteger.valueOf(balance));
			} else if (ALLOWANCE.equals(functionType)) {
				result = Tuple.of(BigInteger.valueOf(allowance));
			} else if (APPROVE.equals(functionType)) {
				result = Tuple.of(approve);
			} else if (OWNER.equals(functionType)) {
				result = Tuple.of(convertBesuAddressToHeadlongAddress(owner));
			}  else if (GET_APPROVED.equals(functionType)) {
				result = Tuple.of(convertBesuAddressToHeadlongAddress(approved));
			} else if (NAME.equals(functionType)) {
				result = Tuple.of(name);
			} else if (SYMBOL.equals(functionType)) {
				result = Tuple.of(symbol);
			} else if (TOKEN_URI.equals(functionType)) {
				result = Tuple.of(metadata);
			} else if (ERC_TRANSFER.equals(functionType)) {
				result = Tuple.of(ercFungibleTransferStatus);
			} else if (IS_APPROVED_FOR_ALL.equals(functionType)) {
				result = Tuple.of(isApprovedForAllStatus);
			}

			return Bytes.wrap(tupleType.encode(result).array());
		}
	}

	public static class LogBuilder {
		private Address logger;
		private final List<Object> data = new ArrayList<>();
		private final List<LogTopic> topics = new ArrayList<>();
		final StringBuilder tupleTypes = new StringBuilder("(");

		public static LogBuilder logBuilder() {
			return new LogBuilder();
		}

		public LogBuilder forLogger(final Address logger) {
			this.logger = logger;
			return this;
		}

		public LogBuilder forEventSignature(final Bytes eventSignature) {
			topics.add(generateLogTopic(eventSignature));
			return this;
		}

		public LogBuilder forDataItem(final Object dataItem) {
			data.add(convertDataItem(dataItem));
			addTupleType(dataItem, tupleTypes);
			return this;
		}

		public LogBuilder forIndexedArgument(final Object param) {
			topics.add(generateLogTopic(param));
			return this;
		}

		public Log build() {
			if (tupleTypes.length() > 1) {
				tupleTypes.deleteCharAt(tupleTypes.length() - 1);
				tupleTypes.append(")");
				final var tuple = Tuple.of(data.toArray());
				final var tupleType = TupleType.parse(tupleTypes.toString());
				return new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), topics);
			} else {
				return new Log(logger, Bytes.EMPTY, topics);
			}
		}

		private Object convertDataItem(final Object param) {
			if (param instanceof Address address) {
				return convertBesuAddressToHeadlongAddress(address);
			} else if (param instanceof Long numeric) {
				return BigInteger.valueOf(numeric);
			} else {
				return param;
			}
		}

		private static LogTopic generateLogTopic(final Object param) {
			byte[] array = new byte[]{};
			if (param instanceof Address address) {
				array = address.toArray();
			} else if (param instanceof BigInteger numeric) {
				array = numeric.toByteArray();
			} else if (param instanceof Long numeric) {
				array = BigInteger.valueOf(numeric).toByteArray();
			} else if (param instanceof Boolean bool) {
				array = new byte[]{(byte) (Boolean.TRUE.equals(bool) ? 1 : 0)};
			} else if (param instanceof Bytes bytes) {
				array = bytes.toArray();
			}

			return LogTopic.wrap(Bytes.wrap(expandByteArrayTo32Length(array)));
		}

		private static void addTupleType(final Object param, final StringBuilder stringBuilder) {
			if (param instanceof Address) {
				stringBuilder.append("address,");
			} else if (param instanceof BigInteger || param instanceof Long) {
				stringBuilder.append("uint256,");
			} else if (param instanceof Boolean) {
				stringBuilder.append("bool,");
			}
		}

		private static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
			byte[] expandedArray = new byte[32];

			System.arraycopy(bytesToExpand, 0, expandedArray, expandedArray.length - bytesToExpand.length, bytesToExpand.length);
			return expandedArray;
		}
	}

	private static com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(final Address addressToBeConverted) {
		return com.esaulpaugh.headlong.abi.Address.wrap(com.esaulpaugh.headlong.abi.Address.toChecksumAddress(addressToBeConverted.toBigInteger()));
	}
}