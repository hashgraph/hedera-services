/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.forPayerAndOthers;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.forPayerOnly;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.noneAvailable;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.state.logic.StandardProcessLogic;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hedera.node.app.signature.hapi.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.codec.DecoderException;

@Singleton
public class AdaptedMonoProcessLogic implements ProcessLogic {
    private static final long MILLIS_TO_WAIT_FOR_SIGNATURE_VERIFICATION = 60_000L;
    private final StandardProcessLogic monoProcessLogic;

    @Inject
    public AdaptedMonoProcessLogic(@NonNull final StandardProcessLogic monoProcessLogic) {
        this.monoProcessLogic = monoProcessLogic;
    }

    @Override
    public void incorporateConsensusTxn(final ConsensusTransaction platformTxn, final long submittingMember) {
        if (platformTxn.getMetadata() instanceof PreHandleResult metadata) {
            final var accessor = adaptForMono(platformTxn, metadata);
            platformTxn.setMetadata(accessor);
        }
        monoProcessLogic.incorporateConsensusTxn(platformTxn, submittingMember);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private SwirldsTxnAccessor adaptForMono(final ConsensusTransaction platformTxn, final PreHandleResult metadata) {
        try {
            final var accessor = PlatformTxnAccessor.from(platformTxn.getContents());

            // To work with the mono-service, we have to turn all the sigs BACK into TransactionSignatures
            final var cryptoSignatures = new ArrayList<TransactionSignature>();
            // Add the TransactionSignature for the payer
            final var payerVerificationFuture = metadata.payerVerification();
            final var payerVerification =
                    payerVerificationFuture == null ? null : payerVerificationFuture.get(1, TimeUnit.MINUTES);
            final var payerTxSigs =
                    payerVerification == null ? null : ((SignatureVerificationImpl) payerVerification).txSigs();
            cryptoSignatures.addAll(payerTxSigs);

            // Add the TransactionSignatures for the non-payers
            cryptoSignatures.addAll(extract(metadata.nonPayerVerifications()));
            // Add the TransactionSignatures for the non-payer hollow accounts
            cryptoSignatures.addAll(extract(metadata.nonPayerHollowVerifications()));

            accessor.addAllCryptoSigs(cryptoSignatures);
            final var preHandleResponseCode = metadata.responseCode();
            final var payerKey = payerVerification == null ? null : payerVerification.key();
            if (payerKey != null) {
                final var jkey = mapToJKey(payerKey);
                if (preHandleResponseCode != OK) {
                    accessor.setSigMeta(forPayerOnly(jkey, cryptoSignatures, accessor));
                } else {
                    final List<JKey> otherPayerKeys = metadata.nonPayerVerifications() == null
                            ? Collections.emptyList()
                            : metadata.nonPayerVerifications().keySet().stream()
                                    .map(this::mapToJKey)
                                    .toList();
                    accessor.setSigMeta(forPayerAndOthers(jkey, otherPayerKeys, cryptoSignatures, accessor));
                }
            } else {
                accessor.setSigMeta(noneAvailable());
            }
            // Prevent the mono-service workflow from rationalizing sigs
            accessor.setExpandedSigStatus(fromPbj(preHandleResponseCode));
            accessor.setLinkedRefs(new LinkedRefs());
            return accessor;
        } catch (final InvalidProtocolBufferException e) {
            throw new IllegalStateException("An unparseable transaction was submitted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Unknown failure", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while handling sigs", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out waiting for 3 minutes. This is fatal", e);
        }
    }

    private List<TransactionSignature> extract(@Nullable final Map<?, Future<SignatureVerification>> map) {
        if (map == null) return Collections.emptyList();
        return map.values().stream()
                .map(future -> {
                    try {
                        return future.get(MILLIS_TO_WAIT_FOR_SIGNATURE_VERIFICATION, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(
                                "Interrupted while waiting for a signature verification to complete!", e);
                    } catch (ExecutionException | TimeoutException e) {
                        throw new RuntimeException("Signature verification failed!", e);
                    }
                })
                .map(SignatureVerificationImpl.class::cast)
                .map(SignatureVerificationImpl::txSigs)
                .flatMap(List::stream)
                .toList();
    }

    private JKey mapToJKey(@NonNull final Key key) {
        try {
            return JKey.mapKey(key);
        } catch (DecoderException e) {
            throw new IllegalArgumentException("Unable to map key", e);
        }
    }
}
