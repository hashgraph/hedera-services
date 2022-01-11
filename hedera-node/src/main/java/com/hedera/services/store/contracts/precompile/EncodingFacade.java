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
		//Default constructor
	}

	public Bytes getMintSuccessfulResultFromReceipt(final long totalSupply, final long[] serialNumbers) {
		return functionResultBuilder().forFunction(FunctionType.MINT).withStatus(SUCCESS.getNumber()).
				withTotalSupply(totalSupply).
				withSerialNumbers(serialNumbers != null ? serialNumbers : new long[0]).build();
	}

	public Bytes getBurnSuccessfulResultFromReceipt(final long totalSupply) {
		return functionResultBuilder().forFunction(FunctionType.BURN).withStatus(SUCCESS.getNumber()).
				withTotalSupply(totalSupply).build();
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
			} else if (functionType == FunctionType.BURN) {
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
			Tuple result = Tuple.EMPTY;
			if (functionType == FunctionType.MINT) {
				result = Tuple.of(
						status,
						BigInteger.valueOf(totalSupply),
						serialNumbers);
			} else if (functionType == FunctionType.BURN) {
				result = Tuple.of(
						status,
						BigInteger.valueOf(totalSupply));
			}
			return Bytes.wrap(tupleType.encode(result).array());
		}
	}
}
