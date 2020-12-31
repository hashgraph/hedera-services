package com.hedera.services.txns.contract;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HederaFs;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractCreateTransitionLogic implements TransitionLogic {
	private static final byte[] MISSING_BYTECODE = new byte[0];

	@FunctionalInterface
	public interface LegacyCreator {
		TransactionRecord perform(
				TransactionBody txn,
				Instant consensusTime,
				byte[] bytecode,
				SequenceNumber seqNum);
	}

	private final HederaFs hfs;
	private final LegacyCreator delegate;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final Supplier<SequenceNumber> seqNo;

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	public ContractCreateTransitionLogic(
			HederaFs hfs,
			LegacyCreator delegate,
			Supplier<SequenceNumber> seqNo,
			OptionValidator validator,
			TransactionContext txnCtx
	) {
		this.hfs = hfs;
		this.seqNo = seqNo;
		this.txnCtx = txnCtx;
		this.delegate = delegate;
		this.validator = validator;
	}

	@Override
	public void doStateTransition() {
		try {
			var contractCreateTxn = txnCtx.accessor().getTxn();
			var op = contractCreateTxn.getContractCreateInstance();

			if (!validator.isValidEntityMemo(op.getMemo())) {
				txnCtx.setStatus(MEMO_TOO_LONG);
				return;
			}
			var inputs = prepBytecode(op);
			if (inputs.getValue() != OK) {
				txnCtx.setStatus(inputs.getValue());
				return;
			}

			var legacyRecord = delegate.perform(contractCreateTxn, txnCtx.consensusTime(), inputs.getKey(), seqNo.get());

			var outcome = legacyRecord.getReceipt().getStatus();
			txnCtx.setStatus(outcome);
			txnCtx.setCreateResult(legacyRecord.getContractCreateResult());
			if (outcome == SUCCESS) {
				txnCtx.setCreated(legacyRecord.getReceipt().getContractID());
			}
		} catch (Exception e) {
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	private Map.Entry<byte[], ResponseCodeEnum> prepBytecode(ContractCreateTransactionBody op) {
		var bytecodeSrc = op.getFileID();
		if (!hfs.exists(bytecodeSrc)) {
			return new AbstractMap.SimpleImmutableEntry<>(MISSING_BYTECODE, INVALID_FILE_ID);
		}
		byte[] bytecode = hfs.cat(bytecodeSrc);
		if (bytecode.length == 0) {
			return new AbstractMap.SimpleImmutableEntry<>(MISSING_BYTECODE, CONTRACT_FILE_EMPTY);
		}
		return new AbstractMap.SimpleImmutableEntry<>(bytecode, OK);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractCreateInstance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody contractCreateTxn) {
		var op = contractCreateTxn.getContractCreateInstance();
		return validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod()) ? OK : AUTORENEW_DURATION_NOT_IN_RANGE;
	}
}
