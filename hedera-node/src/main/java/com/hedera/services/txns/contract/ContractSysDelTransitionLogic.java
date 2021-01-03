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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ContractSysDelTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(ContractSysDelTransitionLogic.class);

	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final LegacySystemDeleter delegate;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> contracts;

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	public ContractSysDelTransitionLogic(
			OptionValidator validator,
			TransactionContext txnCtx,
			LegacySystemDeleter delegate,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> contracts
	) {
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.delegate = delegate;
		this.contracts = contracts;
	}

	@FunctionalInterface
	public interface LegacySystemDeleter {
		TransactionRecord perform(TransactionBody txn, Instant consensusTime);
	}

	@Override
	public void doStateTransition() {
		try {
			var contractSysDelTxn = txnCtx.accessor().getTxn();

			var legacyRecord = delegate.perform(contractSysDelTxn, txnCtx.consensusTime());

			txnCtx.setStatus(legacyRecord.getReceipt().getStatus());
		} catch (Exception e) {
			log.warn("Avoidable exception!", e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return txn -> txn.hasSystemDelete() && txn.getSystemDelete().hasContractID();
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody contractSysDelTxn) {
		var op = contractSysDelTxn.getSystemDelete();
		var status = validator.queryableContractStatus(op.getContractID(), contracts.get());
		return (status != INVALID_CONTRACT_ID) ? OK : INVALID_CONTRACT_ID;
	}
}
