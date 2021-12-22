package com.hedera.services.store.contracts.precompile;

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
	}

	public static Bytes getMintSuccessfulResult(final long newTotalSupply, final long[] serialNumbers) {
		final var resultTuple = com.esaulpaugh.headlong.abi.Tuple.of(
				SUCCESS.getNumber(),
				BigInteger.valueOf(newTotalSupply),
				serialNumbers != null ? serialNumbers : new long[]{});
		return Bytes.wrap(mintReturnType.encode(resultTuple).array());
	}

	public static Bytes getBurnSuccessfulResult(final long newTotalSupply) {
		final var resultTuple = com.esaulpaugh.headlong.abi.Tuple.of(
				SUCCESS.getNumber(),
				BigInteger.valueOf(newTotalSupply));
		return Bytes.wrap(burnReturnType.encode(resultTuple).array());
	}
}