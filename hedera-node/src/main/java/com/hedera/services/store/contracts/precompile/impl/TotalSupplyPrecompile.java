package com.hedera.services.store.contracts.precompile.impl;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Provider;

public class TotalSupplyPrecompile extends ERCReadOnlyAbstractPrecompile {
	public TotalSupplyPrecompile(
			final TokenID tokenId,
			final SyntheticTxnFactory syntheticTxnFactory,
			final WorldLedgers ledgers,
			final EncodingFacade encoder,
			final DecodingFacade decoder,
			final Provider<FeeCalculator> feeCalculator,
			final UsagePricesProvider resourceCosts,
			final StateView currentView,
			final PrecompilePricingUtils pricingUtils
	) {
		super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, feeCalculator, resourceCosts, currentView, pricingUtils);
	}

	@Override
	public long getGasRequirement(long blockTimestamp) {
		final var now = Timestamp.newBuilder().setSeconds(blockTimestamp).build();
		return pricingUtils.computeViewFunctionGas(now, getMinimumFeeInTinybars(now), feeCalculator, resourceCosts, currentView);
	}

	@Override
	public Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
		final var totalSupply = ledgers.totalSupplyOf(tokenId);
		return encoder.encodeTotalSupply(totalSupply);
	}
}
