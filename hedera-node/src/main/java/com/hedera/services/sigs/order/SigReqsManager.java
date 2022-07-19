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
import com.hedera.services.context.primitives.SignedStateViewFactory;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.NoValidSignedStateException;
import com.hedera.services.sigs.ExpansionHelper;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.StateChildrenSigMetadataLookup;
import com.hedera.services.sigs.metadata.TokenMetaUtils;
import com.hedera.services.sigs.metadata.TokenSigningMetadata;
import com.hedera.services.state.merkle.MerkleToken;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.transaction.SwirldTransaction;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;

import javax.inject.Inject;
import javax.inject.Singleton;
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
 * necessary step of re-expanding signatures in {@link Rationalization#performFor(SwirldsTxnAccessor)}.
 *
 * This class is <b>NOT</b> thread-safe.
 */
@Singleton
public class SigReqsManager {
	// The token-to-signing-metadata transformation used to construct instances of SigRequirements
	public static final Function<MerkleToken, TokenSigningMetadata> TOKEN_META_TRANSFORM =
			TokenMetaUtils::signingMetaFrom;

	private final FileNumbers fileNumbers;
	private final ExpansionHelper expansionHelper;
	private final SignatureWaivers signatureWaivers;
	private final MutableStateChildren workingState;
	private final GlobalDynamicProperties dynamicProperties;
	private final SignedStateViewFactory stateViewFactory;
	// Convenience wrapper for the latest state children received from Platform#getLastCompleteSwirldState()
	private final MutableStateChildren signedChildren = new MutableStateChildren();

	private SigReqsFactory sigReqsFactory = SigRequirements::new;
	private StateChildrenLookupsFactory lookupsFactory = StateChildrenSigMetadataLookup::new;

	// Used to expand signatures when sigs.expandFromLastSignedState=true and an
	// initialized, signed state of the current version is available
	private SigRequirements signedSigReqs;
	// Used to expand signatures when one or more of the above conditions is not met
	private SigRequirements workingSigReqs;

	@Inject
	public SigReqsManager(
			final FileNumbers fileNumbers,
			final ExpansionHelper expansionHelper,
			final SignatureWaivers signatureWaivers,
			final MutableStateChildren workingState,
			final GlobalDynamicProperties dynamicProperties,
			final SignedStateViewFactory stateViewFactory
	) {
		this.fileNumbers = fileNumbers;
		this.workingState = workingState;
		this.expansionHelper = expansionHelper;
		this.signatureWaivers = signatureWaivers;
		this.dynamicProperties = dynamicProperties;
		this.stateViewFactory = stateViewFactory;
	}

	/**
	 * Uses the "best available" {@link SigRequirements} implementation to expand the platform
	 * signatures linked to the given transaction; prefers the implementation backed by the latest
	 * signed state as returned from {@link Platform#getLastCompleteSwirldState()}.
	 *
	 * @param accessor
	 * 		a transaction that needs linked signatures expanded
	 */
	public void expandSigsInto(final SwirldsTxnAccessor accessor) {
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
	private void expandFromWorkingState(final SwirldsTxnAccessor accessor) {
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
	private boolean tryExpandFromSignedState(final SwirldsTxnAccessor accessor) {
		/* Update our children (e.g., MerkleMaps and VirtualMaps) from the current signed state.
		 * Because event intake is single-threaded, there's no risk of another thread getting
		 * inconsistent results while we are doing this. Also, note that MutableStateChildren
		 * uses weak references, so we won't keep this signed state from GC eligibility.
		 *
		 * We use the updateFromMaybeUninitializedState() variant here, because during a
		 * reconnect the latest signed state may have never received an init() call. In that
		 * case, any "rebuilt" children of the ServicesState will be null. (This isn't a
		 * problem for any existing SigRequirements code, however.) */
		try {
			stateViewFactory.tryToUpdateToLatestSignedChildren(signedChildren);
		} catch (NoValidSignedStateException ignore) {
			return false;
		}
		expandFromSignedState(accessor);
		return true;
	}

	/**
	 * Tries to expand the platform signatures linked to a transaction from latest completed signed state that
	 * cannot have been signed before the given consensus time. Returns whether the expansion attempt was
	 * successful. (If this fails, we will next try to expand signatures from the working state.)
	 *
	 * @param accessor
	 */
	private void expandFromSignedState(final SwirldsTxnAccessor accessor) {
		ensureSignedStateSigReqsIsConstructed();
		expansionHelper.expandIn(accessor, signedSigReqs, accessor.getPkToSigsFn());
	}

	private void ensureWorkingStateSigReqsIsConstructed() {
		if (workingSigReqs == null) {
			final var lookup = lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM);
			workingSigReqs = sigReqsFactory.from(lookup, signatureWaivers);
		}
	}

	private void ensureSignedStateSigReqsIsConstructed() {
		if (signedSigReqs == null) {
			final var lookup = lookupsFactory.from(fileNumbers, signedChildren, TOKEN_META_TRANSFORM);
			signedSigReqs = sigReqsFactory.from(lookup, signatureWaivers);
		}
	}

	@FunctionalInterface
	interface SigReqsFactory {
		SigRequirements from(SigMetadataLookup sigMetaLookup, SignatureWaivers signatureWaivers);
	}

	@FunctionalInterface
	interface StateChildrenLookupsFactory {
		SigMetadataLookup from(FileNumbers fileNumbers, StateChildren stateChildren,
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
