package com.hedera.services.store.contracts.precompile;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * Defines the lifecycle for a <i>single execution</i> of a precompiled contract compatible with
 * {@link HTSPrecompiledContract}. (So implementations will generally be <i>stateful</i>, and will
 * keep internal state between calls to different lifecycle methods.)
 */
interface Precompile {
	/**
	 * Most precompiles will use the HAPI {@link ResponseCodeEnum} ordinals as their response codes.
	 */
	Bytes SUCCESS_RESULT = resultFrom(SUCCESS);

	/**
	 * Returns a builder initialized with a "synthetic" transaction body that represents this
	 *
	 * @param input
	 * @param aliasResolver
	 * @return
	 */
	TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver);

	void run(MessageFrame frame);

	long getMinimumFeeInTinybars(Timestamp consensusTime);

	default Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
		return SUCCESS_RESULT;
	}

	default Bytes getFailureResultFor(final ResponseCodeEnum status) {
		return resultFrom(status);
	}

	default void addImplicitCostsIn(final TxnAccessor accessor) {
		// Most transaction types can compute their full Hedera fee from just an initial transaction body; but
		// for a token transfer, we may need to recompute to charge for the extra work implied by custom fees
	}

	default List<FcAssessedCustomFee> getCustomFees() {
		return Collections.emptyList();
	}

	default boolean shouldAddTraceabilityFieldsToRecord() {
		return true;
	}

	static Bytes resultFrom(final ResponseCodeEnum status) {
		return UInt256.valueOf(status.getNumber());
	}
}
