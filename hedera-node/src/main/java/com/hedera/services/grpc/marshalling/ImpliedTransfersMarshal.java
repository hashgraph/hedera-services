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
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.CustomFeesBalanceChange;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		final List<Pair<EntityId, List<CustomFee>>> entityCustomFees = new ArrayList<>();
		List<CustomFeesBalanceChange> customFeeBalanceChangesForRecord = new ArrayList<>();
		Map<Pair<EntityId, EntityId>, BalanceChange> indexesMap = new HashMap<>();
		for (var aa : op.getTransfers().getAccountAmountsList()) {
			BalanceChange change = hbarAdjust(aa);
			changes.add(change);
			indexesMap.put(Pair.of(change.getAccount(), EntityId.MISSING_ENTITY_ID), change);
		}
		var payerId = EntityId.fromGrpcAccountId(payer);
		for (var scopedTransfers : op.getTokenTransfersList()) {
			final var grpcTokenId = scopedTransfers.getToken();
			final var scopingToken = EntityId.fromGrpcTokenId(grpcTokenId);
			var amount = 0L;
			for (var aa : scopedTransfers.getTransfersList()) {
				BalanceChange tokenChange = tokenAdjust(scopingToken, grpcTokenId, aa);
				changes.add(tokenChange);
				indexesMap.put(Pair.of(tokenChange.getAccount(), tokenChange.getToken()), tokenChange);
				if (aa.getAmount() > 0) {
					amount += aa.getAmount();
				}
			}

			List<CustomFee> customFeesOfToken = customFeeSchedules.lookupScheduleFor(scopingToken);
			entityCustomFees.add(Pair.of(scopingToken, customFeesOfToken));
			List<BalanceChange> customFeeChanges = computeBalanceChangeForCustomFee(scopingToken, payerId, amount,
					customFeesOfToken, indexesMap, customFeeBalanceChangesForRecord);
			changes.addAll(customFeeChanges);
			//getListOfBalanceChangesForCustomFees(customFeeChanges, customFeeBalanceChangesForRecord);
		}
		return ImpliedTransfers.valid(maxHbarAdjusts, maxTokenAdjusts, changes, entityCustomFees,
				customFeeBalanceChangesForRecord);
	}

	/**
	 * Compute the balance changes for custom fees to be added to all balance changes in transfer list
	 *
	 * @param scopingToken
	 * 		token Id that is being transferred
	 * @param payerId
	 * 		payer Id for the transaction
	 * @param totalAmount
	 * 		total amount being transferred in transfer transaction
	 * @param customFeesOfToken
	 * 		list of custom fees for the token
	 * @param indexesMap
	 * @param customFeeBalanceChangesForRecord
	 * @return
	 */
	private List<BalanceChange> computeBalanceChangeForCustomFee(EntityId scopingToken, EntityId payerId,
			long totalAmount, List<CustomFee> customFeesOfToken,
			Map<Pair<EntityId, EntityId>, BalanceChange> indexesMap,
			List<CustomFeesBalanceChange> customFeeBalanceChangesForRecord) {
		List<BalanceChange> customFeeChanges = new ArrayList<>();
		for (CustomFee fees : customFeesOfToken) {
			if (fees.getFeeType() == CustomFee.FeeType.FIXED_FEE) {
				addFixedFeeBalanceChanges(fees, payerId, customFeeChanges, indexesMap, customFeeBalanceChangesForRecord);
			} else if (fees.getFeeType() == CustomFee.FeeType.FRACTIONAL_FEE) {
				addFractionalFeeBalanceChanges(fees, payerId, totalAmount, scopingToken,
						customFeeChanges, indexesMap, customFeeBalanceChangesForRecord);
			}
		}
		return customFeeChanges;
	}

	/**
	 * Calculate fractional fee balance changes for the custom fees
	 *
	 * @param fees
	 * 		custom fees
	 * @param payerId
	 * 		payer id for the transaction
	 * @param totalAmount
	 * 		total hbar/token amount that is transferred
	 * @param scopingToken
	 * 		tokenId that is being transferred
	 * @param customFeeChanges
	 * @param existingBalanceChange
	 * @param customFeeBalanceChangesForRecord
	 * @return
	 */
	private void addFractionalFeeBalanceChanges(CustomFee fees,
			EntityId payerId, long totalAmount, EntityId scopingToken,
			List<BalanceChange> customFeeChanges,
			Map<Pair<EntityId, EntityId>, BalanceChange> existingBalanceChange,
			List<CustomFeesBalanceChange> customFeeBalanceChangesForRecord) {
		long fee =
				(fees.getFractionalFeeSpec().getNumerator() * totalAmount / fees.getFractionalFeeSpec().getDenominator());
		long feesToCollect = Math.max(fee, fees.getFractionalFeeSpec().getMinimumUnitsToCollect());

		if (fees.getFractionalFeeSpec().getMaximumUnitsToCollect() > 0) {
			feesToCollect = Math.min(feesToCollect, fees.getFractionalFeeSpec().getMaximumUnitsToCollect());
		}
		boolean isPresent = adjustUnitsIfKeyPresent(Pair.of(fees.getFeeCollector(), scopingToken), existingBalanceChange, feesToCollect);
		if(!isPresent) {
			BalanceChange customFee = tokenAdjust(fees.getFeeCollector(), scopingToken, feesToCollect);
			customFeeChanges.add(customFee);
			existingBalanceChange.put(Pair.of(fees.getFeeCollector(), scopingToken), customFee);
		}
		isPresent = adjustUnitsIfKeyPresent(Pair.of(payerId, scopingToken), existingBalanceChange, -feesToCollect);
		if(!isPresent) {
			BalanceChange customFee = tokenAdjust(payerId, scopingToken, -feesToCollect);
			customFeeChanges.add(customFee);
			existingBalanceChange.put(Pair.of(payerId, scopingToken), customFee);
		}
		customFeeBalanceChangesForRecord.add(new CustomFeesBalanceChange(fees.getFeeCollector(),
				scopingToken,
				feesToCollect));
	}

	/**
	 * Calculate Fixed fee balance changes for the custom fees
	 *
	 * @param fees
	 * 		custom fees
	 * @param payerId
	 * 		payer id for the transaction
	 * @param customFeeChanges
	 * @param existingBalanceChange
	 * @param customFeeBalanceChangesForRecord
	 * @return
	 */
	private void addFixedFeeBalanceChanges(CustomFee fees, EntityId payerId,
			List<BalanceChange> customFeeChanges,
			Map<Pair<EntityId, EntityId>, BalanceChange> existingBalanceChange,
			List<CustomFeesBalanceChange> customFeeBalanceChangesForRecord) {
		if (fees.getFixedFeeSpec().getTokenDenomination() == null) {
			boolean isPresent = adjustUnitsIfKeyPresent(Pair.of(fees.getFeeCollector(), EntityId.MISSING_ENTITY_ID),
					existingBalanceChange, fees.getFixedFeeSpec().getUnitsToCollect());
			if (!isPresent) {
				BalanceChange customFee = hbarAdjust(fees.getFeeCollector(),
						fees.getFixedFeeSpec().getUnitsToCollect());
				customFeeChanges.add(customFee);
				existingBalanceChange.put(Pair.of(fees.getFeeCollector(), EntityId.MISSING_ENTITY_ID), customFee);
			}
			isPresent = adjustUnitsIfKeyPresent(Pair.of(payerId, EntityId.MISSING_ENTITY_ID),
					existingBalanceChange, -fees.getFixedFeeSpec().getUnitsToCollect());
			if (!isPresent) {
				BalanceChange customFee = hbarAdjust(payerId, -fees.getFixedFeeSpec().getUnitsToCollect());
				customFeeChanges.add(customFee);
				existingBalanceChange.put(Pair.of(fees.getFeeCollector(), EntityId.MISSING_ENTITY_ID), customFee);
			}
			customFeeBalanceChangesForRecord.add(new CustomFeesBalanceChange(fees.getFeeCollector(),
					null, fees.getFixedFeeSpec().getUnitsToCollect()));
		} else {
			boolean isPresent = adjustUnitsIfKeyPresent(Pair.of(fees.getFeeCollector(),
					fees.getFixedFeeSpec().getTokenDenomination()),
					existingBalanceChange, fees.getFixedFeeSpec().getUnitsToCollect());
			if(!isPresent) {
				BalanceChange customFee = tokenAdjust(fees.getFeeCollector(),
						fees.getFixedFeeSpec().getTokenDenomination(),
						fees.getFixedFeeSpec().getUnitsToCollect());
				customFeeChanges.add(customFee);
				existingBalanceChange.put(Pair.of(fees.getFeeCollector(),
						fees.getFixedFeeSpec().getTokenDenomination()), customFee);
			}
			isPresent = adjustUnitsIfKeyPresent(
					Pair.of(payerId, fees.getFixedFeeSpec().getTokenDenomination()),
					existingBalanceChange, -fees.getFixedFeeSpec().getUnitsToCollect());

			if(!isPresent) {
				BalanceChange customFee = tokenAdjust(payerId,
						fees.getFixedFeeSpec().getTokenDenomination(),
						-fees.getFixedFeeSpec().getUnitsToCollect());
				customFeeChanges.add(customFee);
				existingBalanceChange.put(Pair.of(payerId,
						fees.getFixedFeeSpec().getTokenDenomination()), customFee);
			}
			customFeeBalanceChangesForRecord.add(new CustomFeesBalanceChange(fees.getFeeCollector(),
					fees.getFixedFeeSpec().getTokenDenomination(),
					fees.getFixedFeeSpec().getUnitsToCollect()));
		}
	}

	private void addOnlyIfNotPresent(boolean isPresent, CustomFee fees,
			BalanceChange hbarAdjust, List<BalanceChange> customFeeChanges,
			Map<Pair<EntityId, EntityId>, BalanceChange> existingBalanceChange) {
		if (!isPresent) {
			BalanceChange customFee = hbarAdjust(fees.getFeeCollector(),
					fees.getFixedFeeSpec().getUnitsToCollect());
			customFeeChanges.add(customFee);
			existingBalanceChange.put(Pair.of(fees.getFeeCollector(), EntityId.MISSING_ENTITY_ID), customFee);
		}
	}

	private boolean adjustUnitsIfKeyPresent(Pair<EntityId, EntityId> key,
			Map<Pair<EntityId, EntityId>, BalanceChange> indexesMap, long fees) {
		if (indexesMap.containsKey(key)) {
			var balChange = indexesMap.get(key);
			balChange.adjustUnits(fees);
			return true;
		}
		return false;
	}

	/**
	 * Get list of {@link CustomFeesBalanceChange} to be set for
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} from list of all balance changes in transfer list
	 *
	 * @param customFeeChanges
	 * 		custom fees balance changes
	 * @param customFeeBalanceChangesForRecord
	 * @return
	 */
	private void getListOfBalanceChangesForCustomFees(List<BalanceChange> customFeeChanges,
			List<CustomFeesBalanceChange> customFeeBalanceChangesForRecord) {
		for (BalanceChange change : customFeeChanges) {
			customFeeBalanceChangesForRecord.add(new CustomFeesBalanceChange(
					EntityId.fromGrpcAccountId(change.accountId()),
					change.isForHbar() ? null : EntityId.fromGrpcTokenId(change.tokenId()),
					change.units()));
		}
	}

	private CustomFeesBalanceChange addCustomFeeBalanceChange(BalanceChange change,
			List<CustomFeesBalanceChange> customFeeBalanceChangesForRecord) {
			return new CustomFeesBalanceChange(
					change.getAccount(),
					change.isForHbar() ? null : change.getToken(),
					change.units());
	}
}
