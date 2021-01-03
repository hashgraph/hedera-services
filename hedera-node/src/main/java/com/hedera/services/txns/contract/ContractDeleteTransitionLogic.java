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

public class ContractDeleteTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(ContractDeleteTransitionLogic.class);

	private final LegacyDeleter delegate;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> contracts;

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	public ContractDeleteTransitionLogic(
			LegacyDeleter delegate,
			OptionValidator validator,
			TransactionContext txnCtx,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> contracts
	) {
		this.delegate = delegate;
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.contracts = contracts;
	}

	@FunctionalInterface
	public interface LegacyDeleter {
		TransactionRecord perform(TransactionBody txn, Instant consensusTime);
	}

	@Override
	public void doStateTransition() {
		try {
			var contractDeleteTxn = txnCtx.accessor().getTxn();

			var legacyRecord = delegate.perform(contractDeleteTxn, txnCtx.consensusTime());

			txnCtx.setStatus(legacyRecord.getReceipt().getStatus());
		} catch (Exception e) {
			log.warn("Avoidable exception!", e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractDeleteInstance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody contractDeleteTxn) {
		var op = contractDeleteTxn.getContractDeleteInstance();
		return validator.queryableContractStatus(op.getContractID(), contracts.get());
	}
}
