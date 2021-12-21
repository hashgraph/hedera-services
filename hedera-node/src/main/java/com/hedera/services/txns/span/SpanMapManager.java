package com.hedera.services.txns.span;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.utils.TxnAccessor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

/**
 * Responsible for managing the properties in a {@link TxnAccessor#getSpanMap()}.
 * This management happens in two steps:
 * <ol>
 * <li>In {@link SpanMapManager#expandSpan(TxnAccessor)}, the span map is
 * "expanded" to include the results of any work that can likely be reused
 * in {@code handleTransaction}.</li>
 * <li>In {@link SpanMapManager#rationalizeSpan(TxnAccessor)}, the span map
 * "rationalized" to be sure that any pre-computed work can still be reused
 * safely.</li>
 * </ol>
 *
 * The only entry currently in the span map is the {@link com.hedera.services.grpc.marshalling.ImpliedTransfers}
 * produced by the {@link ImpliedTransfersMarshal}; this improves performance for
 * CrypoTransfers specifically.
 *
 * Other operations will certainly be able to benefit from the same infrastructure
 * over time.
 */
@Singleton
public class SpanMapManager {
	private final AliasManager aliasManager;
	private final CustomFeeSchedules customFeeSchedules;
	private final GlobalDynamicProperties dynamicProperties;
	private final ImpliedTransfersMarshal impliedTransfersMarshal;
	private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

	@Inject
	public SpanMapManager(
			final ImpliedTransfersMarshal impliedTransfersMarshal,
			final GlobalDynamicProperties dynamicProperties,
			final CustomFeeSchedules customFeeSchedules,
			final AliasManager aliasManager
	) {
		this.impliedTransfersMarshal = impliedTransfersMarshal;
		this.dynamicProperties = dynamicProperties;
		this.customFeeSchedules = customFeeSchedules;
		this.aliasManager = aliasManager;
	}

	public void expandSpan(TxnAccessor accessor) {
		final var function = accessor.getFunction();
		if (function == CryptoTransfer) {
			expandImpliedTransfers(accessor);
		}
	}

	public void rationalizeSpan(TxnAccessor accessor) {
		if (accessor.getFunction() == CryptoTransfer) {
			rationalizeImpliedTransfers(accessor);
		}
	}

	private void expandImpliedTransfers(TxnAccessor accessor) {
		final var op = accessor.getTxn().getCryptoTransfer();
		final var impliedTransfers = impliedTransfersMarshal.unmarshalFromGrpc(op);
		reCalculateXferMeta(accessor, impliedTransfers);
		spanMapAccessor.setImpliedTransfers(accessor, impliedTransfers);
		accessor.setNumAutoCreations(impliedTransfers.getMeta().getNumAutoCreations());
	}

	private void rationalizeImpliedTransfers(TxnAccessor accessor) {
		final var impliedTransfers = spanMapAccessor.getImpliedTransfers(accessor);
		if (!impliedTransfers.getMeta().wasDerivedFrom(dynamicProperties, customFeeSchedules, aliasManager)) {
			expandImpliedTransfers(accessor);
		}
	}

	private void reCalculateXferMeta(TxnAccessor accessor, ImpliedTransfers impliedTransfers) {
		final var xferMeta = accessor.availXferUsageMeta();

		var customFeeTokenTransfers = 0;
		var customFeeHbarTransfers = 0;
		final Set<EntityId> involvedTokens = new HashSet<>();
		for (var assessedFee : impliedTransfers.getAssessedCustomFees()) {
			if (assessedFee.isForHbar()) {
				customFeeHbarTransfers++;
			} else {
				customFeeTokenTransfers++;
				involvedTokens.add(assessedFee.token());
			}
		}
		xferMeta.setCustomFeeHbarTransfers(customFeeHbarTransfers);
		xferMeta.setCustomFeeTokensInvolved(involvedTokens.size());
		xferMeta.setCustomFeeTokenTransfers(customFeeTokenTransfers);
	}
}
