package com.hedera.services.sigs;

import com.hedera.services.sigs.factories.BodySigningSigFactory;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytesProvider;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.VerificationStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hedera.services.sigs.PlatformSigOps.createEd25519PlatformSigsFrom;
import static com.hedera.services.sigs.factories.PlatformSigFactory.allVaryingMaterialEquals;
import static com.hedera.services.sigs.utils.StatusUtils.successFor;
import static com.hedera.services.legacy.crypto.SignatureStatusCode.SUCCESS;

public class Rationalization {
    private static final Logger log = LogManager.getLogger(Rationalization.class);
    public final static SigStatusOrderResultFactory IN_HANDLE_SUMMARY_FACTORY =
            new SigStatusOrderResultFactory(true);

    private final SyncVerifier syncVerifier;
    private final List<Signature> txnSigs;
    private final PlatformTxnAccessor txnAccessor;
    private final HederaSigningOrder keyOrderer;
    private final PubKeyToSigBytesProvider sigsProvider;
    private final TxnScopedPlatformSigFactory sigFactory;

    public Rationalization(
            PlatformTxnAccessor txnAccessor,
            SyncVerifier syncVerifier,
            HederaSigningOrder keyOrderer,
            PubKeyToSigBytesProvider sigsProvider
    ) {
        this.txnAccessor = txnAccessor;
        this.syncVerifier = syncVerifier;
        this.keyOrderer = keyOrderer;
        this.sigsProvider = sigsProvider;

        txnSigs = txnAccessor.getPlatformTxn().getSignatures();
        sigFactory = new BodySigningSigFactory(txnAccessor.getTxnBytes());
    }

    public SignatureStatus execute() {
        log.debug("Rationalizing crypto sigs with Hedera sigs for txn {}...", txnAccessor.getSignedTxn4Log());
        List<Signature> realPayerSigs = new ArrayList<>(), realOtherPartySigs = new ArrayList<>();

        SignatureStatus payerStatus = expandIn(
                realPayerSigs, sigsProvider::payerSigBytesFor, keyOrderer::keysForPayer);
        if ( !SUCCESS.name().equals( payerStatus.getStatusCode().name() ) ) {
            log.debug("Failed rationalizing payer sigs, txn {}: {}", txnAccessor.getTxnId(), payerStatus);
            return payerStatus;
        }
        SignatureStatus otherPartiesStatus = expandIn(
                realOtherPartySigs, sigsProvider::otherPartiesSigBytesFor, keyOrderer::keysForOtherParties);
        if ( !SUCCESS.name().equals( otherPartiesStatus.getStatusCode().name() ) ) {
            log.debug("Failed rationalizing other sigs, txn {}: {}", txnAccessor.getTxnId(), otherPartiesStatus);
            return otherPartiesStatus;
        }

        List<Signature> rationalizedPayerSigs = rationalize(realPayerSigs, 0);
        List<Signature> rationalizedOtherPartySigs = rationalize(realOtherPartySigs, realPayerSigs.size());

        if (rationalizedPayerSigs == realPayerSigs || rationalizedOtherPartySigs == realOtherPartySigs) {
            txnAccessor.getPlatformTxn().clear();
            txnAccessor.getPlatformTxn().addAll(rationalizedPayerSigs.toArray(new Signature[0]));
            txnAccessor.getPlatformTxn().addAll(rationalizedOtherPartySigs.toArray(new Signature[0]));
            log.warn("Verified crypto sigs synchronously for txn {}", txnAccessor.getSignedTxn4Log());
            return syncSuccess();
        }

        return asyncSuccess();
    }

    private List<Signature> rationalize(List<Signature> realSigs, int startingAt) {
        try {
            List<Signature> candidateSigs = txnSigs.subList(startingAt, startingAt + realSigs.size());
            if (allVaryingMaterialEquals(candidateSigs, realSigs) && allStatusesAreKnown(candidateSigs)) {
                return candidateSigs;
            }
        } catch (IndexOutOfBoundsException ignore) {
            log.warn(ignore.getMessage());
        }
        syncVerifier.verifySync(realSigs);
        return realSigs;
    }

    private boolean allStatusesAreKnown(List<Signature> sigs) {
        return sigs.stream().map(Signature::getSignatureStatus).noneMatch(VerificationStatus.UNKNOWN::equals);
    }

    private SignatureStatus expandIn(
            List<Signature> target,
            Function<Transaction, PubKeyToSigBytes> sigsFn,
            BiFunction<TransactionBody, SigStatusOrderResultFactory, SigningOrderResult<SignatureStatus>> keysFn
    ) {
        SigningOrderResult<SignatureStatus> orderResult =
                keysFn.apply(txnAccessor.getTxn(), IN_HANDLE_SUMMARY_FACTORY);
        if (orderResult.hasErrorReport()) {
            return orderResult.getErrorReport();
        }
        PlatformSigsCreationResult creationResult = createEd25519PlatformSigsFrom(
                orderResult.getOrderedKeys(), sigsFn.apply(txnAccessor.getSignedTxn()), sigFactory);
        if (creationResult.hasFailed()) {
            return creationResult.asSignatureStatus(true, txnAccessor.getTxnId());
        }
        target.addAll(creationResult.getPlatformSigs());
        return successFor(true, txnAccessor);
    }

    private SignatureStatus syncSuccess() {
        return success(SignatureStatusCode.SUCCESS_VERIFY_SYNC);
    }
    private SignatureStatus asyncSuccess() {
        return success(SignatureStatusCode.SUCCESS_VERIFY_ASYNC);
    }
    private SignatureStatus success(SignatureStatusCode code) {
        return new SignatureStatus(
                code, ResponseCodeEnum.OK,
                true, txnAccessor.getTxn().getTransactionID(),
                null, null, null, null);
    }
}
