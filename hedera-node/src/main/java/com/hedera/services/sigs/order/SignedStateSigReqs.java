package com.hedera.services.sigs.order;

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
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.StateChildrenSigMetadataLookup;
import com.hedera.services.sigs.metadata.TokenMetaUtils;
import com.hedera.services.sigs.metadata.TokenSigningMetadata;
import com.hedera.services.state.StateAccessor;
import com.hedera.services.state.annotations.LatestSignedState;
import com.hedera.services.state.annotations.WorkingState;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.TxnAccessor;
import com.swirlds.common.SwirldState;
import com.swirlds.common.SwirldTransaction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Function;

/**
 * Used by {@link com.hedera.services.ServicesState#expandSignatures(SwirldTransaction)} to get the
 * "best available" implementation of {@link SigRequirements} when expanding signatures.
 *
 * We prefer to expand from the latest signed state since whenever the entities and aliases linked to a
 * transaction are unchanged between {@code expandSignatures} and {@code handleTransaction}, we can short-circuit
 * out of {@link Rationalization#performFor(TxnAccessor)} during {@code handleTransaction}.)
 */
@Singleton
public class SignedStateSigReqs {
	public static final Function<MerkleToken, TokenSigningMetadata> TOKEN_META_TRANSFORM =
			TokenMetaUtils::signingMetaFrom;

	private final FileNumbers fileNumbers;
	private final AliasManager aliasManager;
	private final StateAccessor workingState;
	private final StateAccessor latestSignedState;
	private final SignatureWaivers signatureWaivers;

	private SigReqsFactory sigReqsFactory = SigRequirements::new;
	private StateChildrenLookupsFactory lookupsFactory = StateChildrenSigMetadataLookup::new;

	private StateChildren signedChildren = new MutableStateChildren();
	private SigRequirements signedSigReqs;
	/* Before we have a first signed state, we must use the working state's children to expand signatures. */
	private SigRequirements workingSigReqs;

	@Inject
	public SignedStateSigReqs(
			final FileNumbers fileNumbers,
			final AliasManager aliasManager,
			final SignatureWaivers signatureWaivers,
			final @WorkingState StateAccessor workingState,
			final @LatestSignedState StateAccessor latestSignedState
	) {
		this.fileNumbers = fileNumbers;
		this.aliasManager = aliasManager;
		this.workingState = workingState;
		this.signatureWaivers = signatureWaivers;
		this.latestSignedState = latestSignedState;
	}

	/**
	 * Returns the "best available" implementation of {@link SigRequirements} for use in expanding
	 * signatures; prefers the implementation backed by the latest signed state received in
	 * {@link com.hedera.services.ServicesMain#newSignedState(SwirldState, Instant, long)}.
	 *
	 * @return the best available signing requirements
	 */
	public SigRequirements getBestAvailable() {
		final var latestSignedChildren = latestSignedState.children();
		if (latestSignedChildren.isSigned()) {
			if (latestSignedChildren.isSignedAfter(signedChildren)) {
				signedChildren = latestSignedChildren;
				final var lookup = lookupsFactory.from(
						fileNumbers, aliasManager, signedChildren, TOKEN_META_TRANSFORM);
				signedSigReqs = sigReqsFactory.from(lookup, signatureWaivers);

				/* Once we have a signed state, we won't go back to the working state */
				workingSigReqs = null;
			}
			return signedSigReqs;
		} else {
			return provideWorkingSigReqs();
		}
	}

	private SigRequirements provideWorkingSigReqs() {
		if (workingSigReqs == null) {
			final var lookup = lookupsFactory.from(
					fileNumbers, aliasManager, workingState.children(), TOKEN_META_TRANSFORM);
			workingSigReqs = sigReqsFactory.from(lookup, signatureWaivers);
		}
		return workingSigReqs;
	}

	@FunctionalInterface
	interface SigReqsFactory {
		SigRequirements from(
				SigMetadataLookup sigMetaLookup,
				SignatureWaivers signatureWaivers);
	}

	@FunctionalInterface
	interface StateChildrenLookupsFactory {
		SigMetadataLookup from(
				FileNumbers fileNumbers,
				AliasManager aliasManager,
				StateChildren stateChildren,
				Function<MerkleToken, TokenSigningMetadata> tokenMetaTransform);
	}

	/* --- Only used by unit tests --- */
	void setSigReqsFactory(final SigReqsFactory sigReqsFactory) {
		this.sigReqsFactory = sigReqsFactory;
	}

	void setLookupsFactory(final StateChildrenLookupsFactory lookupsFactory) {
		this.lookupsFactory = lookupsFactory;
	}
}
