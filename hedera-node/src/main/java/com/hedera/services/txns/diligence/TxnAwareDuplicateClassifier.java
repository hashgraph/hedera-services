package com.hedera.services.txns.diligence;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionID;

import java.util.EnumSet;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;

/**
 * Provides a {@link ScopedDuplicateClassifier} that operates in the context
 * of the current transaction.
 *
 * @author Michael Tinker
 */
public class TxnAwareDuplicateClassifier implements ScopedDuplicateClassifier {
	private final TransactionContext txnCtx;
	private final NodeDuplicateClassifier nodeDuplicateClassifier;

	private static final EnumSet<ResponseCodeEnum> INVISIBLE_FROM_CLASSIFIER_WINDOW = EnumSet.of(
			INVALID_NODE_ACCOUNT,
			INVALID_PAYER_SIGNATURE
	);

	public TxnAwareDuplicateClassifier(
			TransactionContext txnCtx,
			NodeDuplicateClassifier nodeDuplicateClassifier
	) {
		this.txnCtx = txnCtx;
		this.nodeDuplicateClassifier = nodeDuplicateClassifier;
	}

	public void shiftDetectionWindow() {
		nodeDuplicateClassifier.shiftWindow(txnCtx.consensusTime().getEpochSecond());
	}

	public DuplicateClassification duplicityOfActiveTxn() {
		return nodeDuplicateClassifier.classify(txnCtx.submittingNodeAccount(), txnCtx.accessor().getTxnId());
	}

	public void incorporateCommitment() {
		if (!INVISIBLE_FROM_CLASSIFIER_WINDOW.contains(txnCtx.status())) {
			nodeDuplicateClassifier.observe(nodeSubmitting(), txnId(), now());
		}
	}

	private TransactionID txnId() {
		return txnCtx.accessor().getTxnId();
	}

	private AccountID nodeSubmitting() {
		return txnCtx.submittingNodeAccount();
	}

	private long now() {
		return txnCtx.consensusTime().getEpochSecond();
	}
}
