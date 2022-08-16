/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
 */
package com.hedera.services.sigs.order;

import static com.hedera.services.context.primitives.SignedStateViewFactory.isUsable;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.ServicesState;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.sigs.ExpansionHelper;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.StateChildrenSigMetadataLookup;
import com.hedera.services.sigs.metadata.TokenMetaUtils;
import com.hedera.services.sigs.metadata.TokenSigningMetadata;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.events.Event;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Used by {@link com.hedera.services.sigs.EventExpansion#expandAllSigs(Event, ServicesState)} to
 * expand the cryptographic signatures <i>linked</i> to a given transaction. Linked signatures are
 * derived from two pieces of information:
 *
 * <ol>
 *   <li>The list of Hedera keys required to sign the transaction for it to be valid; and,
 *   <li>The public key prefixes and private key signatures in the transaction's {@code
 *       SignatureMap}.
 * </ol>
 *
 * We prefer to look up the Hedera keys from an immutable state, since if the entities with those
 * keys are unchanged between {@code expandSignatures} and {@code handleTransaction}, we can skip
 * the otherwise necessary step of re-expanding signatures in {@link
 * Rationalization#performFor(SwirldsTxnAccessor)}.
 *
 * <p>This class is <b>NOT</b> thread-safe.
 */
@Singleton
public class SigReqsManager {
    private static final Logger log = LogManager.getLogger(SigReqsManager.class);

    // The token-to-signing-metadata transformation used to construct instances of SigRequirements
    public static final Function<MerkleToken, TokenSigningMetadata> TOKEN_META_TRANSFORM =
            TokenMetaUtils::signingMetaFrom;

    private final FileNumbers fileNumbers;
    private final ExpansionHelper expansionHelper;
    private final SignatureWaivers signatureWaivers;
    private final MutableStateChildren workingState;
    private final GlobalDynamicProperties dynamicProperties;
    // Convenience wrapper for children of a given immutable state
    private final MutableStateChildren immutableChildren = new MutableStateChildren();

    private SigReqsFactory sigReqsFactory = SigRequirements::new;
    private StateChildrenLookupsFactory lookupsFactory = StateChildrenSigMetadataLookup::new;

    // Used to expand signatures when sigs.expandFromImmutableState=true and an
    // initialized, immutable state of the current version is available
    private SigRequirements immutableSigReqs;
    // Used to expand signatures when one or more of the above conditions is not met
    private SigRequirements workingSigReqs;

    @Inject
    public SigReqsManager(
            final FileNumbers fileNumbers,
            final ExpansionHelper expansionHelper,
            final SignatureWaivers signatureWaivers,
            final MutableStateChildren workingState,
            final GlobalDynamicProperties dynamicProperties) {
        this.fileNumbers = fileNumbers;
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
     * @param sourceState an immutable state appropriate for signature expansion
     * @param accessor a transaction that needs linked signatures expanded
     */
    public void expandSigs(final ServicesState sourceState, final SwirldsTxnAccessor accessor) {
        if (dynamicProperties.expandSigsFromImmutableState()
                && tryExpandFromImmutable(sourceState, accessor)) {
            return;
        }
        expandFromWorkingState(accessor);
    }

    /**
     * Uses the working state to expand the platform signatures linked to the given transaction.
     *
     * @param accessor the transaction to expand signatures for
     */
    private void expandFromWorkingState(final SwirldsTxnAccessor accessor) {
        ensureWorkingStateSigReqsIsConstructed();
        expansionHelper.expandIn(accessor, workingSigReqs, accessor.getPkToSigsFn());
    }

    /**
     * Gets the latest signed state from the platform and makes a best-effort attempt to use it to
     * expand the platform signatures linked to the given transaction.
     *
     * @param accessor the transaction to expand signatures for
     * @return whether the expansion attempt succeeded
     */
    private boolean tryExpandFromImmutable(
            final ServicesState sourceState, final SwirldsTxnAccessor accessor) {
        if (!isUsable(sourceState)) {
            return false;
        }
        try {
            // Update our children (e.g., MerkleMaps and VirtualMaps) from given immutable state.
            // Because event intake is single-threaded, there's no risk of another thread getting
            // inconsistent results while we are doing this. Also, note that MutableStateChildren
            // uses weak references, so we won't keep this immutable state from GC eligibility.
            immutableChildren.updateFromImmutable(
                    sourceState, sourceState.getTimeOfLastHandledTxn());
            expandFromImmutableState(accessor);
            return true;
        } catch (Exception e) {
            log.warn("Unable to expand signatures from immutable state", e);
            return false;
        }
    }

    private void expandFromImmutableState(final SwirldsTxnAccessor accessor) {
        ensureImmutableStateSigReqsIsConstructed();
        expansionHelper.expandIn(accessor, immutableSigReqs, accessor.getPkToSigsFn());
    }

    private void ensureWorkingStateSigReqsIsConstructed() {
        if (workingSigReqs == null) {
            final var lookup = lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM);
            workingSigReqs = sigReqsFactory.from(lookup, signatureWaivers);
        }
    }

    private void ensureImmutableStateSigReqsIsConstructed() {
        if (immutableSigReqs == null) {
            final var lookup =
                    lookupsFactory.from(fileNumbers, immutableChildren, TOKEN_META_TRANSFORM);
            immutableSigReqs = sigReqsFactory.from(lookup, signatureWaivers);
        }
    }

    @FunctionalInterface
    interface SigReqsFactory {
        SigRequirements from(SigMetadataLookup sigMetaLookup, SignatureWaivers signatureWaivers);
    }

    @FunctionalInterface
    interface StateChildrenLookupsFactory {
        SigMetadataLookup from(
                FileNumbers fileNumbers,
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

    @VisibleForTesting
    MutableStateChildren getImmutableChildren() {
        return immutableChildren;
    }
}
