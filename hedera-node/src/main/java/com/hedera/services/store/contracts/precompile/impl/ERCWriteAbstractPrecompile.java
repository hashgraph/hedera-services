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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

import javax.inject.Provider;

public abstract class ERCWriteAbstractPrecompile implements Precompile {
	protected final WorldLedgers ledgers;
	protected final DecodingFacade decoder;
	protected final SideEffectsTracker sideEffects;
	protected final SyntheticTxnFactory syntheticTxnFactory;
	protected final InfrastructureFactory infrastructureFactory;
	protected final PrecompilePricingUtils pricingUtils;
	protected final Provider<FeeCalculator> feeCalculator;
	protected final StateView currentView;

	protected ERCWriteAbstractPrecompile(
			final WorldLedgers ledgers,
			final DecodingFacade decoder,
			final SideEffectsTracker sideEffects,
			final SyntheticTxnFactory syntheticTxnFactory,
			final InfrastructureFactory infrastructureFactory,
			final PrecompilePricingUtils pricingUtils,
			final Provider<FeeCalculator> feeCalculator,
			final StateView currentView
	) {
		this.ledgers = ledgers;
		this.decoder = decoder;
		this.sideEffects = sideEffects;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.infrastructureFactory = infrastructureFactory;
		this.feeCalculator = feeCalculator;
		this.pricingUtils = pricingUtils;
		this.currentView = currentView;
	}
}
