package com.hedera.services.txns.crypto;

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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoTransfer transaction,
 * and the conditions under which such logic is syntactically correct. (It is
 * possible that the <i>semantics</i> of the transaction will still be wrong;
 * for example, if one of the accounts involved no longer has the necessary
 * funds available after consensus.)
 *
 * @author Michael Tinker
 */
public class CryptoTransferTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(CryptoTransferTransitionLogic.class);

	private final HederaLedger ledger;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;
	private final ImpliedTransfersMarshal impliedTransfersMarshal;
	private final PureTransferSemanticChecks transferSemanticChecks;
	private final ExpandHandleSpanMapAccessor spanMapAccessor;

	public CryptoTransferTransitionLogic(
			HederaLedger ledger,
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties,
			ImpliedTransfersMarshal impliedTransfersMarshal,
			PureTransferSemanticChecks transferSemanticChecks,
			ExpandHandleSpanMapAccessor spanMapAccessor
	) {
		this.txnCtx = txnCtx;
		this.ledger = ledger;
		this.spanMapAccessor = spanMapAccessor;
		this.dynamicProperties = dynamicProperties;
		this.transferSemanticChecks = transferSemanticChecks;
		this.impliedTransfersMarshal = impliedTransfersMarshal;
	}

	@Override
	public void doStateTransition() {
		try {
			final var accessor = txnCtx.accessor();
			final var impliedTransfers = finalImpliedTransfersFor(accessor);

			var outcome = impliedTransfers.getMeta().code();
			if (outcome == OK) {
				final var changes = impliedTransfers.getAllBalanceChanges();
				outcome = ledger.doZeroSum(changes);
			}

			txnCtx.setStatus((outcome == OK) ? SUCCESS : outcome);
			txnCtx.setAssessedCustomFees(impliedTransfers.getAssessedCustomFees());
		} catch (Exception e) {
			log.warn("Avoidable exception in CryptoTransfer state transition", e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	private ImpliedTransfers finalImpliedTransfersFor(TxnAccessor accessor) {
		var impliedTransfers = spanMapAccessor.getImpliedTransfers(accessor);
		if (impliedTransfers == null) {
			final var op = accessor.getTxn().getCryptoTransfer();
			impliedTransfers = impliedTransfersMarshal.unmarshalFromGrpc(op, accessor.getPayer());
		}
		return impliedTransfers;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoTransfer;
	}

	@Override
	public ResponseCodeEnum validateSemantics(TxnAccessor accessor) {
		final var impliedTransfers = spanMapAccessor.getImpliedTransfers(accessor);
		if (impliedTransfers != null) {
			/* Accessor is for a consensus transaction with a expand-handle span
			* we've been managing in the normal way. */
			return impliedTransfers.getMeta().code();
		} else {
			/* Accessor is for either (1) a transaction in precheck or (2) a scheduled
			transaction that reached consensus without a managed expand-handle span. */
			final var op = accessor.getTxn().getCryptoTransfer();
			final var maxHbarAdjusts = dynamicProperties.maxTransferListSize();
			final var maxTokenAdjusts = dynamicProperties.maxTokenTransferListSize();
			return transferSemanticChecks.fullPureValidation(
					maxHbarAdjusts, maxTokenAdjusts, op.getTransfers(), op.getTokenTransfersList());
		}
	}
}
