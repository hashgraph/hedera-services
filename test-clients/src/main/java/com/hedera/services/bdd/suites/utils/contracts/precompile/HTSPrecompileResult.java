package com.hedera.services.bdd.suites.utils.contracts.precompile;

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
import java.math.BigInteger;
import com.hedera.services.bdd.suites.utils.contracts.ContractCallResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class HTSPrecompileResult implements ContractCallResult {
	private HTSPrecompileResult() {}

	private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
	private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");

	public static HTSPrecompileResult htsPrecompileResult() {
		return new HTSPrecompileResult();
	}

	public enum FunctionType {
		MINT, BURN
	}

	private FunctionType functionType;
	private TupleType tupleType;
	private ResponseCodeEnum status;
	private long totalSupply;
	private long[] serialNumbers;

	public HTSPrecompileResult forFunction(final FunctionType functionType) {
		if (functionType == FunctionType.MINT) {
			tupleType = mintReturnType;
		} else if (functionType == FunctionType.BURN) {
			tupleType = burnReturnType;
		}
		this.functionType = functionType;
		return this;
	}

	public HTSPrecompileResult withStatus(final ResponseCodeEnum status) {
		this.status = status;
		return this;
	}

	public HTSPrecompileResult withTotalSupply(final long totalSupply) {
		this.totalSupply = totalSupply;
		return this;
	}

	public HTSPrecompileResult withSerialNumbers(final long ... serialNumbers) {
		this.serialNumbers = serialNumbers;
		return this;
	}

	@Override
	public Bytes getBytes() {
		Tuple result;
		if (functionType == FunctionType.MINT) {
			result = Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply), serialNumbers);
		} else if (functionType == FunctionType.BURN) {
			result = Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply));
		} else {
			return UInt256.valueOf(status.getNumber());
		}
		return Bytes.wrap(tupleType.encode(result).array());
	}
}
