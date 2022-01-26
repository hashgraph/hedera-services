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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class EncodingFacade {
	private static final long[] NO_MINTED_SERIAL_NUMBERS = new long[0];
	private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
	private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");

	@Inject
	public EncodingFacade() {
		/* For Dagger2 */
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
		MINT, BURN
	}

	private FunctionResultBuilder functionResultBuilder() {
		return new FunctionResultBuilder();
	}

	private static class FunctionResultBuilder {
		private FunctionType functionType;
		private TupleType tupleType;
		private int status;
		private long totalSupply;
		private long[] serialNumbers;

		private FunctionResultBuilder forFunction(final FunctionType functionType) {
			if (functionType == FunctionType.MINT) {
				tupleType = mintReturnType;
			} else {
				tupleType = burnReturnType;
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
			if (functionType == FunctionType.MINT) {
				result = Tuple.of(status, BigInteger.valueOf(totalSupply), serialNumbers);
			} else {
				result = Tuple.of(status, BigInteger.valueOf(totalSupply));
			}
			return Bytes.wrap(tupleType.encode(result).array());
		}
	}
}
