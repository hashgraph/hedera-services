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
import com.hedera.services.txns.contract.helpers.UpdateCustomizerFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNum.fromContractId;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractUpdateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(ContractUpdateTransitionLogic.class);

	private final HederaLedger ledger;
	private final OptionValidator validator;
	private final SigImpactHistorian sigImpactHistorian;
	private final TransactionContext txnCtx;
	private final UpdateCustomizerFactory customizerFactory;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	public ContractUpdateTransitionLogic(
			final HederaLedger ledger,
			final OptionValidator validator,
			final SigImpactHistorian sigImpactHistorian,
			final TransactionContext txnCtx,
			final UpdateCustomizerFactory customizerFactory,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts
	) {
		this.ledger = ledger;
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.contracts = contracts;
		this.sigImpactHistorian = sigImpactHistorian;
		this.customizerFactory = customizerFactory;
	}

	@Override
	public void doStateTransition() {
		try {
			var contractUpdateTxn = txnCtx.accessor().getTxn();
			var op = contractUpdateTxn.getContractUpdateInstance();
			var id = op.getContractID();
			var target = contracts.get().get(fromContractId(id));

			var result = customizerFactory.customizerFor(target, validator, op);
			var customizer = result.getLeft();
			if (customizer.isPresent()) {
				ledger.customize(asAccount(id), customizer.get());
				sigImpactHistorian.markEntityChanged(id.getContractNum());
				txnCtx.setStatus(SUCCESS);
			} else {
				txnCtx.setStatus(result.getRight());
			}
		} catch (Exception e) {
			log.warn("Avoidable exception!", e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractUpdateInstance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody contractUpdateTxn) {
		var op = contractUpdateTxn.getContractUpdateInstance();

		var status = validator.queryableContractStatus(op.getContractID(), contracts.get());
		if (status != OK) {
			return status;
		}

		if (op.hasAutoRenewPeriod()) {
			if (op.getAutoRenewPeriod().getSeconds() < 1) {
				return INVALID_RENEWAL_PERIOD;
			}
			if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
				return AUTORENEW_DURATION_NOT_IN_RANGE;
			}
		}

		var newMemoIfAny = op.hasMemoWrapper() ? op.getMemoWrapper().getValue() : op.getMemo();
		if ((status = validator.memoCheck(newMemoIfAny)) != OK) {
			return status;
		}

		return OK;
	}
}
