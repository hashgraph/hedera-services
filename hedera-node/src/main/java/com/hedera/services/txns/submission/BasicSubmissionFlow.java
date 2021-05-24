package com.hedera.services.txns.submission;

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

import com.hedera.services.context.ServicesNodeType;
import com.hedera.services.txns.SubmissionFlow;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;

import static com.hedera.services.context.ServicesNodeType.ZERO_STAKE_NODE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Performs precheck on a top-level transaction and submits it to the Platform if precheck passes.
 */
public class BasicSubmissionFlow implements SubmissionFlow {
	private final ServicesNodeType nodeType;
	private final TransactionPrecheck precheck;
	private final PlatformSubmissionManager submissionManager;

	public BasicSubmissionFlow(
			ServicesNodeType nodeType,
			TransactionPrecheck precheck,
			PlatformSubmissionManager submissionManager
	) {
		this.precheck = precheck;
		this.nodeType = nodeType;
		this.submissionManager = submissionManager;
	}

	@Override
	public TransactionResponse submit(Transaction signedTxn) {
		if (nodeType == ZERO_STAKE_NODE) {
			return responseWith(INVALID_NODE_ACCOUNT);
		}

		final var precheckResult = precheck.performForTopLevel(signedTxn);
		final var precheckResultMeta = precheckResult.getLeft();
		final var precheckResultValidity = precheckResultMeta.getValidity();
		if (precheckResultValidity != OK) {
			return responseWith(precheckResultValidity, precheckResultMeta.getRequiredFee());
		} else if (precheckResult.getRight().isEmpty()) {
			return responseWith(FAIL_INVALID);
		}

		final var accessor = precheckResult.getRight().get();
		return responseWith(submissionManager.trySubmission(accessor));
	}

	private TransactionResponse responseWith(ResponseCodeEnum validity) {
		return responseWith(validity, 0);
	}

	private TransactionResponse responseWith(ResponseCodeEnum validity, long feeRequired) {
		return TransactionResponse.newBuilder()
				.setNodeTransactionPrecheckCode(validity)
				.setCost(feeRequired)
				.build();
	}
}
