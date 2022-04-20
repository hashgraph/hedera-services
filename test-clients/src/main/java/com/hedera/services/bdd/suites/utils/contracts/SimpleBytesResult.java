package com.hedera.services.bdd.suites.utils.contracts;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;

public class SimpleBytesResult implements ContractCallResult{
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
