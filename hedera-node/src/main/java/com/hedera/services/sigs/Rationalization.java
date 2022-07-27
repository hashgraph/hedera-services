/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs;

import static com.hedera.services.sigs.PlatformSigOps.createCryptoSigsFrom;
import static com.hedera.services.sigs.factories.PlatformSigFactory.allVaryingMaterialEquals;
import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hedera.services.utils.RationalizedSigMeta.forPayerAndOthers;
import static com.hedera.services.utils.RationalizedSigMeta.forPayerOnly;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.annotations.WorkingStateSigReqs;
import com.hedera.services.sigs.factories.ReusableBodySigningFactory;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class Rationalization {
    private static final Logger log = LogManager.getLogger(Rationalization.class);

    private final SyncVerifier syncVerifier;
    private final SigRequirements sigReqs;
    private final SigImpactHistorian sigImpactHistorian;
    private final ReusableBodySigningFactory bodySigningFactory;

    private SwirldsTxnAccessor txnAccessor;
    private PubKeyToSigBytes pkToSigFn;

    private JKey reqPayerSig;
    private boolean verifiedSync;
    private List<JKey> reqOthersSigs;
    private ResponseCodeEnum finalStatus;
    private List<TransactionSignature> txnSigs;
    private SigningOrderResult<ResponseCodeEnum> lastOrderResult;
    private List<TransactionSignature> realPayerSigs = new ArrayList<>();
    private List<TransactionSignature> realOtherPartySigs = new ArrayList<>();

    @Inject
    public Rationalization(
            final SyncVerifier syncVerifier,
            final SigImpactHistorian sigImpactHistorian,
            final @WorkingStateSigReqs SigRequirements sigReqs,
            final ReusableBodySigningFactory bodySigningFactory) {
        this.sigReqs = sigReqs;
        this.syncVerifier = syncVerifier;
        this.sigImpactHistorian = sigImpactHistorian;
        this.bodySigningFactory = bodySigningFactory;
    }

    public void performFor(final SwirldsTxnAccessor txnAccessor) {
        final var linkedRefs = txnAccessor.getLinkedRefs();
        if (linkedRefs != null && linkedRefs.haveNoChangesAccordingTo(sigImpactHistorian)) {
            finalStatus = txnAccessor.getExpandedSigStatus();
            if (finalStatus == null) {
                log.warn(
                        "{} had non-null linked refs but null sig status",
                        txnAccessor.getSignedTxnWrapper());
            } else {
                verifiedSync = false;
                return;
            }
        }

        resetFor(txnAccessor);
        execute();
    }

    public ResponseCodeEnum finalStatus() {
        return finalStatus;
    }

    public boolean usedSyncVerification() {
        return verifiedSync;
    }

    void resetFor(final SwirldsTxnAccessor txnAccessor) {
        this.pkToSigFn = txnAccessor.getPkToSigsFn();
        this.txnAccessor = txnAccessor;

        pkToSigFn.resetAllSigsToUnused();
        bodySigningFactory.resetFor(txnAccessor);

        txnSigs = txnAccessor.getPlatformTxn().getSignatures();
        realPayerSigs.clear();
        realOtherPartySigs.clear();

        finalStatus = null;
        verifiedSync = false;

        reqPayerSig = null;
        reqOthersSigs = null;
        lastOrderResult = null;
    }

    private void execute() {
        ResponseCodeEnum otherFailure = null;

        final var payerStatus = expandIn(realPayerSigs, sigReqs::keysForPayer);
        if (payerStatus != OK) {
            txnAccessor.setSigMeta(RationalizedSigMeta.noneAvailable());
            finalStatus = payerStatus;
            return;
        }
        reqPayerSig = lastOrderResult.getPayerKey();

        final var otherPartiesStatus = expandIn(realOtherPartySigs, sigReqs::keysForOtherParties);
        if (otherPartiesStatus != OK) {
            otherFailure = otherPartiesStatus;
        } else {
            reqOthersSigs = lastOrderResult.getOrderedKeys();
            if (pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()) {
                pkToSigFn.forEachUnusedSigWithFullPrefix(
                        (type, pubKey, sig) ->
                                realOtherPartySigs.add(
                                        bodySigningFactory.signAppropriately(type, pubKey, sig)));
            }
        }

        final var rationalizedPayerSigs = rationalize(realPayerSigs, 0);
        final var rationalizedOtherPartySigs =
                rationalize(realOtherPartySigs, realPayerSigs.size());
        if (rationalizedPayerSigs == realPayerSigs
                || rationalizedOtherPartySigs == realOtherPartySigs) {
            txnSigs = new ArrayList<>();
            txnSigs.addAll(rationalizedPayerSigs);
            txnSigs.addAll(rationalizedOtherPartySigs);
            verifiedSync = true;
        }

        makeRationalizedMetaAccessible();

        finalStatus = (otherFailure != null) ? otherFailure : OK;
    }

    private void makeRationalizedMetaAccessible() {
        if (reqOthersSigs == null) {
            txnAccessor.setSigMeta(forPayerOnly(reqPayerSig, txnSigs, txnAccessor));
        } else {
            txnAccessor.setSigMeta(
                    forPayerAndOthers(reqPayerSig, reqOthersSigs, txnSigs, txnAccessor));
        }
    }

    private List<TransactionSignature> rationalize(
            List<TransactionSignature> realSigs, int startingAt) {
        final var maxSubListEnd = txnSigs.size();
        final var requestedSubListEnd = startingAt + realSigs.size();
        if (requestedSubListEnd <= maxSubListEnd) {
            var candidateSigs = txnSigs.subList(startingAt, startingAt + realSigs.size());
            /* If all the key material is unchanged from expandSignatures(), we are done */
            if (allVaryingMaterialEquals(candidateSigs, realSigs)) {
                return candidateSigs;
            }
        }
        /* Otherwise we must synchronously verify these signatures for the rationalized keys */
        syncVerifier.verifySync(realSigs);
        return realSigs;
    }

    private ResponseCodeEnum expandIn(
            List<TransactionSignature> target, Expansion.SigReqsFunction keysFn) {
        lastOrderResult =
                keysFn.apply(
                        txnAccessor.getTxn(),
                        CODE_ORDER_RESULT_FACTORY,
                        null,
                        txnAccessor.getPayer());
        if (lastOrderResult.hasErrorReport()) {
            return lastOrderResult.getErrorReport();
        }
        final var creation =
                createCryptoSigsFrom(
                        lastOrderResult.getOrderedKeys(), pkToSigFn, bodySigningFactory);
        if (creation.hasFailed()) {
            return creation.asCode();
        }
        target.addAll(creation.getPlatformSigs());
        return OK;
    }

    /* --- Only used by unit tests --- */
    TxnAccessor getTxnAccessor() {
        return txnAccessor;
    }

    SyncVerifier getSyncVerifier() {
        return syncVerifier;
    }

    PubKeyToSigBytes getPkToSigFn() {
        return pkToSigFn;
    }

    SigRequirements getSigReqs() {
        return sigReqs;
    }

    List<TransactionSignature> getRealPayerSigs() {
        return realPayerSigs;
    }

    List<TransactionSignature> getRealOtherPartySigs() {
        return realOtherPartySigs;
    }

    List<TransactionSignature> getTxnSigs() {
        return txnSigs;
    }

    void setReqOthersSigs(List<JKey> reqOthersSigs) {
        this.reqOthersSigs = reqOthersSigs;
    }

    List<JKey> getReqOthersSigs() {
        return reqOthersSigs;
    }

    void setLastOrderResult(SigningOrderResult<ResponseCodeEnum> lastOrderResult) {
        this.lastOrderResult = lastOrderResult;
    }

    SigningOrderResult<ResponseCodeEnum> getLastOrderResult() {
        return lastOrderResult;
    }

    void setReqPayerSig(JKey reqPayerSig) {
        this.reqPayerSig = reqPayerSig;
    }

    JKey getReqPayerSig() {
        return reqPayerSig;
    }

    void setFinalStatus(ResponseCodeEnum finalStatus) {
        this.finalStatus = finalStatus;
    }

    void setVerifiedSync(boolean verifiedSync) {
        this.verifiedSync = verifiedSync;
    }

    public Rationalization(
            final SyncVerifier syncVerifier,
            final SigRequirements sigReqs,
            final ReusableBodySigningFactory bodySigningFactory) {
        this(syncVerifier, null, sigReqs, bodySigningFactory);
    }
}
