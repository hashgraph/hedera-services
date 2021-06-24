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
import com.hedera.services.state.submerkle.AssessedCustomFee;
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.ledger.BalanceChange.hbarAdjust;
import static com.hedera.services.ledger.BalanceChange.tokenAdjust;
import static com.hedera.services.store.models.Id.MISSING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Contains the logic to translate from a gRPC CryptoTransfer operation
 * to a validated list of balance changes, both ℏ and token unit.
 */
public class ImpliedTransfersMarshal {
	private final GlobalDynamicProperties dynamicProperties;
	private final PureTransferSemanticChecks transferSemanticChecks;
	private final CustomFeeSchedules customFeeSchedules;

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
		final List<Pair<Id, List<CustomFee>>> entityCustomFees = new ArrayList<>();
		final List<AssessedCustomFee> assessedCustomFeesForRecord = new ArrayList<>();
		final Map<Pair<Id, Id>, BalanceChange> existingBalanceChanges = new HashMap<>();

		for (var aa : op.getTransfers().getAccountAmountsList()) {
			BalanceChange change = hbarAdjust(aa);
			changes.add(change);
			existingBalanceChanges.put(Pair.of(change.getAccount(), MISSING_ID), change);
		}

		final var payerId = Id.fromGrpcAccount(payer);
		for (var scopedTransfers : op.getTokenTransfersList()) {
			final var grpcTokenId = scopedTransfers.getToken();
			final var scopingToken = Id.fromGrpcToken(grpcTokenId);
			var amount = 0L;
			for (var aa : scopedTransfers.getTransfersList()) {
				final var tokenChange = tokenAdjust(scopingToken, grpcTokenId, aa);
				changes.add(tokenChange);
				existingBalanceChanges.put(Pair.of(tokenChange.getAccount(), tokenChange.getToken()), tokenChange);
				if (aa.getAmount() > 0) {
					amount += aa.getAmount();
					if (amount < 0) {
						return ImpliedTransfers.invalid(
								maxHbarAdjusts, maxTokenAdjusts, CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);
					}
				}
			}

			final var feeSchedule = customFeeSchedules.lookupScheduleFor(scopingToken.asEntityId());
			entityCustomFees.add(Pair.of(scopingToken, feeSchedule));
			try {
				final var customFeeChanges = computeBalanceChangeForCustomFee(
						scopingToken,
						payerId,
						amount,
						feeSchedule,
						existingBalanceChanges,
						assessedCustomFeesForRecord);
				changes.addAll(customFeeChanges);
			} catch (ArithmeticException overflow) {
				return ImpliedTransfers.invalid(maxHbarAdjusts, maxTokenAdjusts, CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);
			}
		}
		return ImpliedTransfers.valid(
				maxHbarAdjusts, maxTokenAdjusts, changes, entityCustomFees, assessedCustomFeesForRecord);
	}

	/**
	 * Compute the balance changes for custom fees to be added to all balance changes in transfer list
	 */
	private List<BalanceChange> computeBalanceChangeForCustomFee(
			Id scopingToken,
			Id payerId,
			long totalAmount, List<CustomFee> customFeesOfToken,
			Map<Pair<Id, Id>, BalanceChange> existingBalanceChanges,
			List<AssessedCustomFee> assessedCustomFeesForRecord
	) {
		List<BalanceChange> customFeeChanges = new ArrayList<>();
		for (CustomFee fees : customFeesOfToken) {
			if (fees.getFeeType() == CustomFee.FeeType.FIXED_FEE) {
				addFixedFeeBalanceChanges(
						fees,
						payerId,
						customFeeChanges,
						existingBalanceChanges,
						assessedCustomFeesForRecord);
			} else if (fees.getFeeType() == CustomFee.FeeType.FRACTIONAL_FEE) {
				addFractionalFeeBalanceChanges(
						fees,
						payerId,
						totalAmount,
						scopingToken,
						customFeeChanges,
						existingBalanceChanges,
						assessedCustomFeesForRecord);
			}
		}
		return customFeeChanges;
	}

	/**
	 * Calculate fractional fee balance changes for the custom fees
	 */
	private void addFractionalFeeBalanceChanges(
			CustomFee fees,
			Id payerId,
			long totalAmount,
			Id scopingToken,
			List<BalanceChange> customFeeChanges,
			Map<Pair<Id, Id>, BalanceChange> existingBalanceChanges,
			List<AssessedCustomFee> assessedCustomFeesForRecord
	) {
		final var spec = fees.getFractionalFeeSpec();
		final var nominalFee = safeFractionMultiply(spec.getNumerator(), spec.getDenominator(), totalAmount);
		long effectiveFee = Math.max(nominalFee, spec.getMinimumAmount());
		if (spec.getMaximumUnitsToCollect() > 0) {
			effectiveFee = Math.min(effectiveFee, spec.getMaximumUnitsToCollect());
		}

		modifyBalanceChange(
				Pair.of(fees.getFeeCollectorAsId(), scopingToken),
				existingBalanceChanges,
				customFeeChanges,
				effectiveFee,
				tokenAdjust(fees.getFeeCollectorAsId(), scopingToken, effectiveFee),
				false);

		modifyBalanceChange(
				Pair.of(payerId, scopingToken),
				existingBalanceChanges,
				customFeeChanges,
				-effectiveFee,
				tokenAdjust(payerId, scopingToken, -effectiveFee),
				true);

		assessedCustomFeesForRecord.add(
				new AssessedCustomFee(fees.getFeeCollectorAccountId(), scopingToken.asEntityId(), effectiveFee));
	}

	long safeFractionMultiply(long n, long d, long v) {
		if (v != 0 && n > Long.MAX_VALUE / v) {
			return BigInteger.valueOf(v).multiply(BigInteger.valueOf(n)).divide(BigInteger.valueOf(d)).longValueExact();
		} else {
			return n * v / d;
		}
	}

	/**
	 * Calculate Fixed fee balance changes for the custom fees
	 */
	private void addFixedFeeBalanceChanges(
			CustomFee fees,
			Id payerId,
			List<BalanceChange> customFeeChanges,
			Map<Pair<Id, Id>, BalanceChange> existingBalanceChanges,
			List<AssessedCustomFee> assessedCustomFeesForRecord
	) {
		final var spec = fees.getFixedFeeSpec();
		final var unitsToCollect = spec.getUnitsToCollect();
		if (spec.getTokenDenomination() == null) {
			modifyBalanceChange(
					Pair.of(fees.getFeeCollectorAsId(), MISSING_ID),
					existingBalanceChanges,
					customFeeChanges,
					unitsToCollect,
					hbarAdjust(fees.getFeeCollectorAsId(), unitsToCollect),
					false);
			modifyBalanceChange(
					Pair.of(payerId, MISSING_ID),
					existingBalanceChanges,
					customFeeChanges,
					-unitsToCollect,
					hbarAdjust(payerId, -unitsToCollect),
					true);
			assessedCustomFeesForRecord.add(
					new AssessedCustomFee(fees.getFeeCollectorAccountId(), null, unitsToCollect));
		} else {
			modifyBalanceChange(
					Pair.of(fees.getFeeCollectorAsId(), spec.getTokenDenomination().asId()),
					existingBalanceChanges,
					customFeeChanges,
					unitsToCollect,
					tokenAdjust(fees.getFeeCollectorAsId(), spec.getTokenDenomination().asId(), unitsToCollect),
					false);
			modifyBalanceChange(
					Pair.of(payerId, spec.getTokenDenomination().asId()),
					existingBalanceChanges,
					customFeeChanges,
					-unitsToCollect,
					tokenAdjust(payerId, spec.getTokenDenomination().asId(), -unitsToCollect),
					true);
			assessedCustomFeesForRecord.add(
					new AssessedCustomFee(fees.getFeeCollectorAccountId(), spec.getTokenDenomination(), unitsToCollect));
		}
	}

	/**
	 * Modify the units if the key is already present in the existing balance changes map.
	 * If not add a new balance change to the map.
	 */
	private void modifyBalanceChange(
			Pair<Id, Id> pair,
			Map<Pair<Id, Id>, BalanceChange> existingBalanceChanges,
			List<BalanceChange> customFeeChanges,
			long fees,
			BalanceChange customFee,
			boolean isPayer
	) {
		final var isPresent = adjustUnitsIfKeyPresent(pair, existingBalanceChanges, fees, isPayer);
		addBalanceChangeIfNotPresent(isPresent, customFeeChanges, existingBalanceChanges, pair, customFee, isPayer);
	}


	/**
	 * Add balance change object to the existing balance changes map only if the key is not present
	 */
	private void addBalanceChangeIfNotPresent(
			boolean isPresent,
			List<BalanceChange> customFeeChanges,
			Map<Pair<Id, Id>, BalanceChange> existingBalanceChanges,
			Pair<Id, Id> pair,
			BalanceChange customFee,
			boolean isPayer
	) {
		if (!isPresent) {
			if (isPayer) {
				customFee.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE);
			}
			customFeeChanges.add(customFee);
			existingBalanceChanges.put(pair, customFee);
		}
	}

	/**
	 * If the key is already present in existing balance chance changes map , modify the units of balance change
	 * by adding the new fees
	 */
	private boolean adjustUnitsIfKeyPresent(
			Pair<Id, Id> key,
			Map<Pair<Id, Id>, BalanceChange> existingBalanceChanges,
			long fees,
			boolean isPayer
	) {
		if (existingBalanceChanges.containsKey(key)) {
			final var balChange = existingBalanceChanges.get(key);
			balChange.adjustUnits(fees);
			if (isPayer) {
				balChange.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE);
			}
			return true;
		}
		return false;
	}
}
