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

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;
import java.util.List;

public class FunctionParameters {
	public static FunctionParameters functionParameters() {
		return new FunctionParameters();
	}

	private static final int ABI_ID_MINT_TOKEN = 0x278e0b88;
	private static final TupleType mintTokenType = TupleType.parse("(address,uint64,bytes[])");

	private int functionHash;
	private Bytes functionParams;

	private FunctionParameters() {
	}

	public Bytes forMintToken(final byte[] address, final long amount, final List<String> metadata) {
		this.functionHash = ABI_ID_MINT_TOKEN;
		final var result = Tuple.of(
				convertBesuAddressToHeadlongAddress(address),
				BigInteger.valueOf(amount),
				metadata.stream().map(String::getBytes).toArray(byte[][]::new)
		);
		this.functionParams = Bytes.wrap(mintTokenType.encode(result).array());
		return getBytes();
	}

	private Bytes getBytes() {
		return Bytes.concatenate(Bytes.ofUnsignedInt(functionHash), functionParams);
	}

	private Address convertBesuAddressToHeadlongAddress(final byte[] address) {
		return Address.wrap(Address.toChecksumAddress(new BigInteger(address)));
	}
}
