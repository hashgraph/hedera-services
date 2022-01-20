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

import com.hedera.services.ServicesState;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.ExpansionHelper;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.StateChildrenSigMetadataLookup;
import com.hedera.services.sigs.metadata.TokenMetaUtils;
import com.hedera.services.sigs.metadata.TokenSigningMetadata;
import com.hedera.services.state.StateAccessor;
import com.hedera.services.state.annotations.WorkingState;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.migration.StateVersions;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldTransaction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Function;

/**
 * Used by {@link com.hedera.services.ServicesState#expandSignatures(SwirldTransaction)} to expand the
 * cryptographic signatures <i>linked</i> to a given transaction. Linked signatures are derived from
 * two pieces of information:
 * <ol>
 *     <li>The list of Hedera keys required to sign the transaction for it to be valid; and,</li>
 *     <li>The public key prefixes and private key signatures in the transaction's {@code SignatureMap}.</li>
 * </ol>
 *
 * We prefer to lookup the Hedera keys from the latest signed state, since if the entities with those keys
 * are unchanged between {@code expandSignatures} and {@code handleTransaction}, we can skip the otherwise
 * necessary step of re-expanding signatures in {@link Rationalization#performFor(TxnAccessor)}.
 *
 * This class is <b>NOT</b> thread-safe.
 */
@Singleton
public class SigReqsManager {
	/* The token-to-signing-metadata transformation used to construct instances of SigRequirements */
	public static final Function<MerkleToken, TokenSigningMetadata> TOKEN_META_TRANSFORM =
			TokenMetaUtils::signingMetaFrom;

	private final Platform platform;
	private final FileNumbers fileNumbers;
	private final AliasManager aliasManager;
	private final StateAccessor workingState;
	private final ExpansionHelper expansionHelper;
	private final SignatureWaivers signatureWaivers;
	private final GlobalDynamicProperties dynamicProperties;
	/* Convenience wrapper for the latest state children received from Platform#getLastCompleteSwirldState() */
	private final MutableStateChildren signedChildren = new MutableStateChildren();

	private SigReqsFactory sigReqsFactory = SigRequirements::new;
	private StateChildrenLookupsFactory lookupsFactory = StateChildrenSigMetadataLookup::new;

	/* Used to expand signatures when `sigs.expandFromLastSignedState=true` and a signed state is available */
	private SigRequirements signedSigReqs;
	/* Used to expand signatures when `sigs.expandFromLastSignedState=false` or no signed state is available */
	private SigRequirements workingSigReqs;

	@Inject
	public SigReqsManager(
			final Platform platform,
			final FileNumbers fileNumbers,
			final AliasManager aliasManager,
			final ExpansionHelper expansionHelper,
			final SignatureWaivers signatureWaivers,
			final @WorkingState StateAccessor workingState,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.platform = platform;
		this.fileNumbers = fileNumbers;
		this.aliasManager = aliasManager;
		this.workingState = workingState;
		this.expansionHelper = expansionHelper;
		this.signatureWaivers = signatureWaivers;
		this.dynamicProperties = dynamicProperties;
	}

	/**
	 * Uses the "best available" {@link SigRequirements} implementation to expand the platform
	 * signatures linked to the given transaction; prefers the implementation backed by the latest
	 * signed state as returned from {@link Platform#getLastCompleteSwirldState()}.
	 *
	 * @param accessor
	 * 		a transaction that needs linked signatures expanded
	 */
	public void expandSigsInto(final PlatformTxnAccessor accessor) {
		if (dynamicProperties.expandSigsFromLastSignedState() && tryExpandFromSignedState(accessor)) {
			return;
		}
		expandFromWorkingState(accessor);
	}

	/**
	 * Uses the working state to expand the platform signatures linked to the given transaction.
	 *
	 * @param accessor
	 * 		the transaction to expand signatures for
	 */
	private void expandFromWorkingState(final PlatformTxnAccessor accessor) {
		ensureWorkingStateSigReqsIsConstructed();
		expansionHelper.expandIn(accessor, workingSigReqs, accessor.getPkToSigsFn());
	}

	/**
	 * Gets the latest signed state from the platform and makes a best-effort attempt to use
	 * it to expand the platform signatures linked to the given transaction.
	 *
	 * @param accessor
	 * 		the transaction to expand signatures for
	 * @return whether the expansion attempt succeeded
	 */
	private boolean tryExpandFromSignedState(final PlatformTxnAccessor accessor) {
		try (final AutoCloseableWrapper<ServicesState> wrapper = platform.getLastCompleteSwirldState()) {
			final var signedState = wrapper.get();
			if (signedState == null) {
				return false;
			}
			/* Since we can't get the enclosing platform SignedState, we don't know exactly
			 * when this state was signed. So we just use, as a guaranteed lower bound, the
			 * consensus time of its last-handled transaction. */
			final var earliestSigningTime = signedState.getTimeOfLastHandledTxn();
			if (earliestSigningTime == null) {
				/* No transactions have been handled; abort now to avoid downstream NPE. */
				return false;
			}
			if (signedState.getStateVersion() != StateVersions.CURRENT_VERSION) {
				/* We just upgraded and don't yet have a signed state from the current version. */
				return false;
			}

			expandFromSignedState(signedState, earliestSigningTime, accessor);
			return true;
		}
	}

	/**
	 * Tries to expand the platform signatures linked to a transaction from a provided signed state that
	 * cannot have been signed before the given consensus time. Returns whether the expansion attempt was
	 * successful. (If this fails, we will next try to expand signatures from the working state.)
	 *
	 * @param signedState
	 * 		the signed state to use for the expansion attempt
	 * @param earliestSigningTime
	 * 		the earliest consensus time at which the state could have been signed
	 * @param accessor
	 * 		the transaction to expand signatures for
	 */
	private void expandFromSignedState(
			final ServicesState signedState,
			final Instant earliestSigningTime,
			final PlatformTxnAccessor accessor
	) {
		/* Update our children (e.g., MerkleMaps and VirtualMaps) from the current signed state.
		 * Because event intake is single-threaded, there's no risk of another thread getting
		 * inconsistent results while we are doing this. Also, note that MutableStateChildren
		 * uses weak references, so we won't keep this signed state from GC eligibility.
		 *
		 * We use the updateFromMaybeUninitializedState() variant here, because during a
		 * reconnect the latest signed state may have never received an init() call. In that
		 * case, any "rebuilt" children of the ServicesState will be null. (This isn't a
		 * problem for any existing SigRequirements code, however.) */
		signedChildren.updateFromMaybeUninitializedState(signedState, earliestSigningTime);
		ensureSignedStateSigReqsIsConstructed();
		expansionHelper.expandIn(accessor, signedSigReqs, accessor.getPkToSigsFn());
	}

	private void ensureWorkingStateSigReqsIsConstructed() {
		if (workingSigReqs == null) {
			final var lookup = lookupsFactory.from(
					fileNumbers, aliasManager, workingState.children(), TOKEN_META_TRANSFORM);
			workingSigReqs = sigReqsFactory.from(lookup, signatureWaivers);
		}
	}

	private void ensureSignedStateSigReqsIsConstructed() {
		if (signedSigReqs == null) {
			final var lookup = lookupsFactory.from(
					fileNumbers, aliasManager, signedChildren, TOKEN_META_TRANSFORM);
			signedSigReqs = sigReqsFactory.from(lookup, signatureWaivers);
		}
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
