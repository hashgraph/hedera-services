package com.hedera.services.grpc.marshalling;

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
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.ledger.BalanceChange.hbarAdjust;
import static com.hedera.services.ledger.BalanceChange.tokenAdjust;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Contains the logic to translate from a gRPC CryptoTransfer operation
 * to a validated list of balance changes, both ℏ and token unit.
 *
 * Once custom fees are implemented for HIP-18, this translation will
 * become somewhat more complicated, since it will need to analyze the
 * token transfers for any custom fee payments that need to be made.
 *
 * (C.f. https://github.com/hashgraph/hedera-services/issues/1587)
 */
public class ImpliedTransfersMarshal {
	private final GlobalDynamicProperties dynamicProperties;
	private final PureTransferSemanticChecks transferSemanticChecks;

	public ImpliedTransfersMarshal(
			GlobalDynamicProperties dynamicProperties,
			PureTransferSemanticChecks transferSemanticChecks
	) {
		this.dynamicProperties = dynamicProperties;
		this.transferSemanticChecks = transferSemanticChecks;
	}

	public ImpliedTransfers unmarshalFromGrpc(CryptoTransferTransactionBody op) {
		final var maxTokenAdjusts = dynamicProperties.maxTokenTransferListSize();
		final var maxHbarAdjusts = dynamicProperties.maxTransferListSize();

		final var validity = transferSemanticChecks.fullPureValidation(
				maxHbarAdjusts, maxTokenAdjusts, op.getTransfers(), op.getTokenTransfersList());
		if (validity != OK) {
			return ImpliedTransfers.invalid(maxHbarAdjusts, maxTokenAdjusts, validity);
		}

		final List<BalanceChange> changes = new ArrayList<>();
		for (var aa : op.getTransfers().getAccountAmountsList()) {
			final var grpcAccountId = aa.getAccountID();
			final var hbarChange = hbarAdjust(Id.fromGrpcAccount(grpcAccountId), aa.getAmount());
			hbarChange.setExplicitAccountId(grpcAccountId);
			changes.add(hbarChange);
		}
		for (var scopedTransfers : op.getTokenTransfersList()) {
			final var grpcTokenId = scopedTransfers.getToken();
			final var scopingToken = Id.fromGrpcToken(grpcTokenId);
			for (var aa : scopedTransfers.getTransfersList()) {
				final var grpcAccountId = aa.getAccountID();
				final var tokenChange = tokenAdjust(scopingToken, Id.fromGrpcAccount(grpcAccountId), aa.getAmount());
				tokenChange.setExplicitTokenId(grpcTokenId);
				tokenChange.setExplicitAccountId(grpcAccountId);
				changes.add(tokenChange);
			}
		}

		return ImpliedTransfers.valid(maxHbarAdjusts, maxTokenAdjusts, changes);
	}
}
