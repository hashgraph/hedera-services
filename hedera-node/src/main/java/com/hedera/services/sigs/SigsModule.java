package com.hedera.services.sigs;

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

import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.keys.HederaKeyActivation;
import com.hedera.services.keys.OnlyIfSigVerifiableValid;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.annotations.WorkingStateSigReqs;
import com.hedera.services.sigs.metadata.StateChildrenSigMetadataLookup;
import com.hedera.services.sigs.metadata.TokenMetaUtils;
import com.hedera.services.sigs.order.PolicyBasedSigWaivers;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SignatureWaivers;
import com.hedera.services.sigs.utils.PrecheckUtils;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.StateAccessor;
import com.hedera.services.state.annotations.WorkingState;
import com.hedera.services.state.logic.PayerSigValidity;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.TransactionSignature;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static com.hedera.services.state.logic.TerminalSigStatuses.TERMINAL_SIG_STATUSES;

@Module
public abstract class SigsModule {
	@Binds
	@Singleton
	public abstract SignatureWaivers provideSignatureWaivers(PolicyBasedSigWaivers policyBasedSigWaivers);

	@Provides
	@Singleton
	public static SyncVerifier provideSyncVerifier(Platform platform) {
		return platform.getCryptography()::verifySync;
	}

	@Provides
	@Singleton
	public static BiPredicate<JKey, TransactionSignature> provideValidityTest(SyncVerifier syncVerifier) {
		return new OnlyIfSigVerifiableValid(syncVerifier);
	}

	@Provides
	@Singleton
	@WorkingStateSigReqs
	public static SigRequirements provideWorkingStateSigReqs(
			final FileNumbers fileNumbers,
			final AliasManager aliasManager,
			final SignatureWaivers signatureWaivers,
			final @WorkingState StateAccessor workingState
	) {
		final var sigMetaLookup = new StateChildrenSigMetadataLookup(
				fileNumbers, aliasManager, workingState.children(), TokenMetaUtils::signingMetaFrom);
		return new SigRequirements(sigMetaLookup, signatureWaivers);
	}

	@Provides
	@Singleton
	public static Predicate<TransactionBody> provideQueryPaymentTest(final NodeInfo nodeInfo) {
		return PrecheckUtils.queryPaymentTestFor(nodeInfo);
	}

	@Provides
	@Singleton
	public static Predicate<ResponseCodeEnum> provideTerminalSigStatusTest() {
		return TERMINAL_SIG_STATUSES;
	}

	@Provides
	@Singleton
	public static PayerSigValidity providePayerSigValidity() {
		return HederaKeyActivation::payerSigIsActive;
	}

	@Provides
	@Singleton
	public static ExpansionHelper provideExpansionHelper() {
		return HederaToPlatformSigOps::expandIn;
	}
}
