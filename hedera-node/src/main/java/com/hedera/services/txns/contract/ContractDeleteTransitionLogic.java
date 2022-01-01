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
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class ContractDeleteTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(ContractDeleteTransitionLogic.class);

	private final HederaLedger ledger;
	private final LegacyDeleter delegate;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final SigImpactHistorian sigImpactHistorian;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	@Inject
	public ContractDeleteTransitionLogic(
			final HederaLedger ledger,
			final LegacyDeleter delegate,
			final OptionValidator validator,
			final SigImpactHistorian sigImpactHistorian,
			final TransactionContext txnCtx,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts
	) {
		this.ledger = ledger;
		this.delegate = delegate;
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.contracts = contracts;
		this.sigImpactHistorian = sigImpactHistorian;
	}

	@FunctionalInterface
	public interface LegacyDeleter {
		TransactionRecord perform(TransactionBody txn, Instant consensusTime);
	}

	@Override
	public void doStateTransition() {
		try {
			final var contractDeleteTxn = txnCtx.accessor().getTxn();
			final var op = contractDeleteTxn.getContractDeleteInstance();

			if (op.hasTransferAccountID()) {
				final var receiver = op.getTransferAccountID();
				if (ledger.exists(receiver) && ledger.isDetached(receiver)) {
					txnCtx.setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
					return;
				}
			}

			final var legacyRecord = delegate.perform(contractDeleteTxn, txnCtx.consensusTime());
			final var status = legacyRecord.getReceipt().getStatus();
			if (status == SUCCESS) {
				sigImpactHistorian.markEntityChanged(op.getContractID().getContractNum());
			}
			txnCtx.setStatus(status);
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
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody contractDeleteTxn) {
		var op = contractDeleteTxn.getContractDeleteInstance();
		return validator.queryableContractStatus(op.getContractID(), contracts.get());
	}
}
