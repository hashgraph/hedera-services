package com.hedera.services.txns.contract;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.store.AccountStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

import javax.inject.Singleton;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;

@Module
public final class ContractLogicModule {

	@Provides
	@Singleton
	public static ContractDeleteTransitionLogic.LegacyDeleter provideLegacyDeleter(
			final SmartContractRequestHandler contracts
	) {
		return contracts::deleteContract;
	}

	@Provides
	@IntoMap
	@FunctionKey(ContractCreate)
	public static List<TransitionLogic> provideContractCreateLogic(
			final ContractCreateTransitionLogic contractCreateTransitionLogic
	) {
		return List.of(contractCreateTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(ContractDelete)
	public static List<TransitionLogic> provideContractDeleteLogic(
			final ContractDeleteTransitionLogic contractDeleteTransitionLogic
	) {
		return List.of(contractDeleteTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(ContractCall)
	public static List<TransitionLogic> provideContractCallLogic(
			final ContractCallTransitionLogic contractCallTransitionLogic
	) {
		return List.of(contractCallTransitionLogic);
	}

	@Provides
	@IntoMap
	@FunctionKey(ContractUpdate)
	public static List<TransitionLogic> provideContractUpdateLogic(
			final TransactionContext txntCtx,
			final HederaLedger ledger,
			final OptionValidator validator,
			final AccountStore accountStore
	) {
		final var contractUpdateTransitionLogic = new ContractUpdateTransitionLogic(
				txntCtx, validator, accountStore);
		return List.of(contractUpdateTransitionLogic);
	}

	private ContractLogicModule() {
		throw new UnsupportedOperationException("Dagger2 module");
	}
}
