package com.hedera.services.fees.calculation.utils;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.consensus.ConsensusOpsUsage;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 *
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

@Singleton
public class AccessorBasedUsages {
	private static final EnumSet<HederaFunctionality> supportedOps = EnumSet.of(FileAppend, CryptoTransfer,
			CryptoCreate, ConsensusSubmitMessage, TokenFeeScheduleUpdate, TokenCreate);

	private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

	private final FileOpsUsage fileOpsUsage;
	private final TokenOpsUsage tokenOpsUsage;
	private final CryptoOpsUsage cryptoOpsUsage;
	private final ConsensusOpsUsage consensusOpsUsage;

	private final OpUsageCtxHelper opUsageCtxHelper;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public AccessorBasedUsages(FileOpsUsage fileOpsUsage, TokenOpsUsage tokenOpsUsage, CryptoOpsUsage cryptoOpsUsage,
			OpUsageCtxHelper opUsageCtxHelper, ConsensusOpsUsage consensusOpsUsage,
			GlobalDynamicProperties dynamicProperties) {
		this.fileOpsUsage = fileOpsUsage;
		this.tokenOpsUsage = tokenOpsUsage;
		this.cryptoOpsUsage = cryptoOpsUsage;
		this.opUsageCtxHelper = opUsageCtxHelper;
		this.consensusOpsUsage = consensusOpsUsage;
		this.dynamicProperties = dynamicProperties;
	}

	public void assess(SigUsage sigUsage, TxnAccessor accessor, UsageAccumulator into) {
		final var function = accessor.getFunction();
		if (!supportedOps.contains(function)) {
			throw new IllegalArgumentException("Usage estimation for " + function + " not yet migrated");
		}

		final var baseMeta = accessor.baseUsageMeta();
		if (function == CryptoTransfer) {
			estimateCryptoTransfer(sigUsage, accessor, baseMeta, into);
		} else if (function == ConsensusSubmitMessage) {
			estimateSubmitMessage(sigUsage, accessor, baseMeta, into);
		} else if (function == TokenFeeScheduleUpdate) {
			estimateFeeScheduleUpdate(sigUsage, accessor, baseMeta, into);
		} else if (function == FileAppend) {
			estimateFileAppend(sigUsage, accessor, baseMeta, into);
		} else if (function == TokenCreate) {
			estimateTokenCreate(sigUsage, accessor, baseMeta, into);
		} else if (function == CryptoCreate) {
			estimateCryptoCreate(sigUsage, accessor, baseMeta, into);
		}
	}

	public boolean supports(HederaFunctionality function) {
		return supportedOps.contains(function);
	}

	private void estimateFeeScheduleUpdate(SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta,
			UsageAccumulator into) {
		final var op = accessor.getTxn().getTokenFeeScheduleUpdate();
		final var opMeta = spanMapAccessor.getFeeScheduleUpdateMeta(accessor);
		final var usageCtx = opUsageCtxHelper.ctxForFeeScheduleUpdate(op);
		tokenOpsUsage.feeScheduleUpdateUsage(sigUsage, baseMeta, opMeta, usageCtx, into);
	}

	private void estimateFileAppend(SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta,
			UsageAccumulator into) {
		final var opMeta = opUsageCtxHelper.metaForFileAppend(accessor.getTxn());
		fileOpsUsage.fileAppendUsage(sigUsage, opMeta, baseMeta, into);
	}

	private void estimateCryptoTransfer(SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta,
			UsageAccumulator into) {
		final var xferMeta = accessor.availXferUsageMeta();
		xferMeta.setTokenMultiplier(dynamicProperties.feesTokenTransferUsageMultiplier());
		cryptoOpsUsage.cryptoTransferUsage(sigUsage, xferMeta, baseMeta, into);
	}

	private void estimateCryptoCreate(SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta,
			UsageAccumulator into) {
		final var cryptoCreateMeta = accessor.getSpanMapAccessor().getCryptoCreateMeta(accessor);
		cryptoOpsUsage.cryptoCreateUsage(sigUsage, baseMeta, cryptoCreateMeta, into);
	}

	private void estimateSubmitMessage(SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta,
			UsageAccumulator into) {
		final var submitMeta = accessor.availSubmitUsageMeta();
		consensusOpsUsage.submitMessageUsage(sigUsage, submitMeta, baseMeta, into);
	}

	private void estimateTokenCreate(SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta,
			UsageAccumulator into) {
		final var tokenCreateMeta = accessor.getSpanMapAccessor().getTokenCreateMeta(accessor);
		tokenOpsUsage.tokenCreateUsage(sigUsage, baseMeta, tokenCreateMeta, into);
	}
}
