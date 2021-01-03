package com.hedera.services.txns.contract;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
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

		if (!op.hasAutoRenewPeriod() || op.getAutoRenewPeriod().getSeconds() < 1) {
			return INVALID_RENEWAL_PERIOD;
		}
		if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			return AUTORENEW_DURATION_NOT_IN_RANGE;
		}
		if (op.getGas() < 0) {
			return CONTRACT_NEGATIVE_GAS;
		}
		if (op.getInitialBalance() < 0) {
			return CONTRACT_NEGATIVE_VALUE;
		}
		if (!validator.isValidEntityMemo(op.getMemo())) {
			return MEMO_TOO_LONG;
		}

		return OK;
	}
}
