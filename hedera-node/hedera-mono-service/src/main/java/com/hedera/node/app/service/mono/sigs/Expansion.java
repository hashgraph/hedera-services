/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.sigs;

import static com.hedera.node.app.service.mono.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.forPayerAndOthers;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.forPayerOnly;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.noneAvailable;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JHollowKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.sigs.order.SigRequirements;
import com.hedera.node.app.service.mono.sigs.order.SigningOrderResult;
import com.hedera.node.app.service.mono.sigs.order.SigningOrderResultFactory;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.utils.PendingCompletion;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

class Expansion {
    private final SigRequirements sigReqs;
    private final PubKeyToSigBytes pkToSigFn;
    private final CryptoSigsCreation cryptoSigsCreation;
    private final SwirldsTxnAccessor txnAccessor;
    private final TxnScopedPlatformSigFactory sigFactory;

    private final LinkedRefs linkedRefs = new LinkedRefs();
    private final List<TransactionSignature> expandedSigs = new ArrayList<>();
    private final AliasManager aliasManager;

    private JKey payerKey;
    private List<JKey> otherPartyKeys;

    private enum Role {
        PAYER,
        OTHER_PARTIES
    }

    public Expansion(
            final SwirldsTxnAccessor txnAccessor,
            final SigRequirements sigReqs,
            final PubKeyToSigBytes pkToSigFn,
            final CryptoSigsCreation cryptoSigsCreation,
            final TxnScopedPlatformSigFactory sigFactory,
            final AliasManager aliasManager) {
        this.cryptoSigsCreation = cryptoSigsCreation;
        this.txnAccessor = txnAccessor;
        this.sigFactory = sigFactory;
        this.pkToSigFn = pkToSigFn;
        this.sigReqs = sigReqs;
        this.aliasManager = aliasManager;
    }

    public void execute() {
        final var payerStatus = expand(Role.PAYER, pkToSigFn, sigReqs::keysForPayer);
        if (payerStatus != OK) {
            txnAccessor.setSigMeta(noneAvailable());
            txnAccessor.setExpandedSigStatus(payerStatus);
            txnAccessor.setLinkedRefs(linkedRefs);
            return;
        }

        final var otherStatus = expand(Role.OTHER_PARTIES, pkToSigFn, sigReqs::keysForOtherParties);
        if (otherStatus != OK) {
            finalizeForExpansionUpTo(Role.PAYER, otherStatus);
            return;
        }

        if (pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()) {
            pkToSigFn.forEachUnusedSigWithFullPrefix(
                    (type, pubKey, sig) -> expandedSigs.add(sigFactory.signAppropriately(type, pubKey, sig)));
        }

        maybePerformHollowScreening();
        finalizeForExpansionUpTo(Role.OTHER_PARTIES, OK);
    }

    /**
     * Hollow accounts may be finalized by *any* signature in the sig map, so
     * if any ECDSA sigs are present in {@link Expansion#expandedSigs}, execute a {@link HollowScreening}, scoped
     * to those {@link Expansion#expandedSigs}.
     *
     * <p> If {@link HollowScreening#pendingCompletionsFrom} returns a non-empty list of {@link PendingCompletion}s,
     * cache the completions in the txn accessor via {@link SwirldsTxnAccessor#setPendingCompletions}.
     *
     * <p> Also try to replace any {@link JHollowKey}s present in the
     *      {@link Expansion#payerKey} and {@link Expansion#otherPartyKeys} with its corresponding
     *      {@link JECDSASecp256k1Key} using the {@link HollowScreening} instance, if such replacement is possible.
     *
     * <p>Note that this method tries to replace {@link JHollowKey}s present in the {@link Expansion#otherPartyKeys}
     * even if no pending completions are present. That's because a CryptoCreate with an evm address alias, derived from
     * a key, different than the key being set for the account, also adds a {@link JHollowKey} to the {@link Expansion#otherPartyKeys},
     * <strong>but that {@link JHollowKey} may not be connected to a finalization.</strong>
     *
     */
    private void maybePerformHollowScreening() {
        if (HollowScreening.atLeastOneWildcardKeyIn(payerKey, otherPartyKeys) && pkToSigFn.hasAtLeastOneEcdsaSig()) {
            final var hollowScreenResult =
                    HollowScreening.performForVersion2(expandedSigs, payerKey, otherPartyKeys, aliasManager);
            if (hollowScreenResult.pendingCompletions() != null) {
                txnAccessor.setPendingCompletions(hollowScreenResult.pendingCompletions());
            }
            if (hollowScreenResult.deHollowedPayerKey() != null) {
                payerKey = hollowScreenResult.deHollowedPayerKey();
            }
            if (hollowScreenResult.deHollowedOtherKeys() != null) {
                otherPartyKeys = hollowScreenResult.deHollowedOtherKeys();
            }
        }
    }

    private void finalizeForExpansionUpTo(final Role lastExpandSuccess, final ResponseCodeEnum finalStatus) {
        txnAccessor.addAllCryptoSigs(expandedSigs);
        if (lastExpandSuccess == Role.PAYER) {
            txnAccessor.setSigMeta(forPayerOnly(payerKey, expandedSigs, txnAccessor));
        } else {
            txnAccessor.setSigMeta(forPayerAndOthers(payerKey, otherPartyKeys, expandedSigs, txnAccessor));
        }
        txnAccessor.setExpandedSigStatus(finalStatus);
        txnAccessor.setLinkedRefs(linkedRefs);
    }

    private ResponseCodeEnum expand(
            final Role role, final PubKeyToSigBytes pkToSigFn, final SigReqsFunction sigReqsFn) {
        final var orderResult =
                sigReqsFn.apply(txnAccessor.getTxn(), CODE_ORDER_RESULT_FACTORY, linkedRefs, txnAccessor.getPayer());
        if (orderResult.hasErrorReport()) {
            return orderResult.getErrorReport();
        }
        if (role == Role.PAYER) {
            payerKey = orderResult.getPayerKey();
        } else {
            otherPartyKeys = orderResult.getOrderedKeys();
        }

        final var creationResult = cryptoSigsCreation.createFrom(orderResult.getOrderedKeys(), pkToSigFn, sigFactory);
        if (!creationResult.hasFailed()) {
            expandedSigs.addAll(creationResult.getPlatformSigs());
        }
        return creationResult.asCode();
    }

    @FunctionalInterface
    interface SigReqsFunction {
        SigningOrderResult<ResponseCodeEnum> apply(
                TransactionBody txn,
                SigningOrderResultFactory<ResponseCodeEnum> factory,
                @Nullable LinkedRefs linkedRefs,
                @Nullable AccountID payer);
    }

    @FunctionalInterface
    interface CryptoSigsCreation {
        PlatformSigsCreationResult createFrom(
                List<JKey> hederaKeys, PubKeyToSigBytes sigBytesFn, TxnScopedPlatformSigFactory factory);
    }
}
