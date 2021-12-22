package com.hedera.services.store.contracts.precompile;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class EncodingFacade {
	private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
	private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");

	@Inject
	public EncodingFacade() {
	}

	public static Bytes getMintSuccessfulResultFromReceipt(final long totalSupply, final long[] serialNumbers) {
		return functionResultBuilder().forFunction(FunctionType.MINT).withStatus(SUCCESS.getNumber()).
				withTotalSupply(totalSupply).
				withSerialNumbers(serialNumbers != null ? serialNumbers : new long[0]).build();
	}

	public static Bytes getBurnSuccessfulResultFromReceipt(final long totalSupply) {
		return functionResultBuilder().forFunction(FunctionType.BURN).withStatus(SUCCESS.getNumber()).
				withTotalSupply(totalSupply).build();
	}

	private enum FunctionType {
		MINT, BURN
	}

	private static FunctionResultBuilder functionResultBuilder() {
		return new FunctionResultBuilder();
	}

	private static class FunctionResultBuilder {
		private FunctionType functionType;
		private TupleType tupleType;
		private int status;
		private long totalSupply;
		private long[] serialNumbers;

		private FunctionResultBuilder forFunction(final FunctionType functionType) {
			switch (functionType) {
				case MINT:
					tupleType = mintReturnType;
					break;
				case BURN:
					tupleType = burnReturnType;
					break;
				default:
					tupleType = TupleType.EMPTY;
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

		private Bytes build() {
			Tuple result;
			switch (functionType) {
				case MINT:
					result = com.esaulpaugh.headlong.abi.Tuple.of(
							status,
							BigInteger.valueOf(totalSupply),
							serialNumbers);
					break;
				case BURN:
					result = com.esaulpaugh.headlong.abi.Tuple.of(
							status,
							BigInteger.valueOf(totalSupply));
					break;
				default:
					result = Tuple.EMPTY;
			}
			return Bytes.wrap(tupleType.encode(result).array());
		}
	}
}