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
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.CustomFeesBalanceChange;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.CustomFeeSchedules;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
	private final CustomFeeSchedules customFeeSchedules;
	private List<CustomFeesBalanceChange> customFeeBalanceChanges;

	public ImpliedTransfersMarshal(
			GlobalDynamicProperties dynamicProperties,
			PureTransferSemanticChecks transferSemanticChecks,
			CustomFeeSchedules customFeeSchedules
	) {
		this.dynamicProperties = dynamicProperties;
		this.transferSemanticChecks = transferSemanticChecks;
		this.customFeeSchedules = customFeeSchedules;
	}

	public ImpliedTransfers unmarshalFromGrpc(CryptoTransferTransactionBody op, AccountID payer) {
		final var maxTokenAdjusts = dynamicProperties.maxTokenTransferListSize();
		final var maxHbarAdjusts = dynamicProperties.maxTransferListSize();

		final var validity = transferSemanticChecks.fullPureValidation(
				maxHbarAdjusts, maxTokenAdjusts, op.getTransfers(), op.getTokenTransfersList());
		if (validity != OK) {
			return ImpliedTransfers.invalid(maxHbarAdjusts, maxTokenAdjusts, validity);
		}

		final List<BalanceChange> changes = new ArrayList<>();
		final List<Pair<EntityId, List<CustomFee>>> customFeesChanges = new ArrayList<>();
		List<CustomFeesBalanceChange> customFeeBalanceChangesForRecord = new ArrayList<>();
		for (var aa : op.getTransfers().getAccountAmountsList()) {
			changes.add(hbarAdjust(aa));
		}
		EntityId payerId = EntityId.fromGrpcAccountId(payer);
		for (var scopedTransfers : op.getTokenTransfersList()) {
			final var grpcTokenId = scopedTransfers.getToken();
			final var scopingToken = EntityId.fromGrpcTokenId(grpcTokenId);
			long amount = 0L;
			for (var aa : scopedTransfers.getTransfersList()) {
				changes.add(tokenAdjust(scopingToken, grpcTokenId, aa));
				if (aa.getAmount() > 0) {
					amount += aa.getAmount();
				}
			}

			List<CustomFee> customFeesOfToken = customFeeSchedules.lookupScheduleFor(scopingToken);
			customFeesChanges.add(new Pair<>(scopingToken, customFeesOfToken));
			List<BalanceChange> customFeeChanges = computeBalanceChangeForCustomFee(scopingToken, payerId, amount,
					customFeesOfToken);
			changes.addAll(customFeeChanges);
			customFeeBalanceChangesForRecord = getListOfBalanceChangesForCustomFees(customFeeChanges);
		}
		return ImpliedTransfers.valid(maxHbarAdjusts, maxTokenAdjusts, changes,
				customFeesChanges, customFeeBalanceChangesForRecord);
	}

	private List<CustomFeesBalanceChange> getListOfBalanceChangesForCustomFees(List<BalanceChange> customFeeChanges) {
		return customFeeChanges.stream().map(e -> new CustomFeesBalanceChange(
				EntityId.fromGrpcAccountId(e.accountId()),
				EntityId.fromGrpcTokenId(e.tokenId()),
				e.units())).collect(Collectors.toList());
	}

	private List<BalanceChange> computeBalanceChangeForCustomFee(EntityId scopingToken, EntityId payerId,
			long totalAmount, List<CustomFee> customFeesOfToken) {
		List<BalanceChange> customFeeChanges = new ArrayList<>();
		for (CustomFee fees : customFeesOfToken) {
			if (fees.getFeeType() == CustomFee.FeeType.FIXED_FEE) {
				if (fees.getFixedFeeSpec().getTokenDenomination() == null) {
					customFeeChanges.add(hbarAdjust(fees.getFeeCollector(),
							fees.getFixedFeeSpec().getUnitsToCollect()));
					customFeeChanges.add(hbarAdjust(payerId, -fees.getFixedFeeSpec().getUnitsToCollect()));
				} else {
					customFeeChanges.add(tokenAdjust(fees.getFeeCollector(),
							fees.getFixedFeeSpec().getTokenDenomination(),
							fees.getFixedFeeSpec().getUnitsToCollect()));
					customFeeChanges.add(tokenAdjust(payerId,
							fees.getFixedFeeSpec().getTokenDenomination(),
							-fees.getFixedFeeSpec().getUnitsToCollect()));
				}
			} else if (fees.getFeeType() == CustomFee.FeeType.FRACTIONAL_FEE) {
				long fee =
						(fees.getFractionalFeeSpec().getNumerator() / fees.getFractionalFeeSpec().getDenominator()) * totalAmount;
				customFeeChanges.add(tokenAdjust(fees.getFeeCollector(),
						scopingToken,
						fee));
				customFeeChanges.add(tokenAdjust(payerId,
						scopingToken,
						-fee));
			}
		}
		return customFeeChanges;
	}
}
