package com.hedera.services.bdd.suites.utils.contracts;

/*-
 * ‌
 * Hedera Services Test Clients
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

import java.math.BigInteger;

public class SimpleBytesResult implements ContractCallResult {
	BigInteger exists;

	private SimpleBytesResult(long result) {
		this.exists = BigInteger.valueOf(result);
	}

	public static SimpleBytesResult bigIntResult(long result) {
		return new SimpleBytesResult(result);
	}

	@Override
	public Bytes getBytes() {
		final var intType = TupleType.parse("(int)");
		final var result = Tuple.of(exists);
		return Bytes.wrap(intType.encode(result).array());
	}
}
