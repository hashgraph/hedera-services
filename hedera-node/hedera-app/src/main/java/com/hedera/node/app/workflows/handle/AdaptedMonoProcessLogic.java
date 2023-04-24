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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
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
import org.apache.commons.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Singleton;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.forPayerAndOthers;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.forPayerOnly;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.noneAvailable;

@Singleton
public class AdaptedMonoProcessLogic implements ProcessLogic {
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
            final var results = metadata.signatureResults().get(3, TimeUnit.MINUTES);
            final var cryptoSignatures = new ArrayList<TransactionSignature>();
            cryptoSignatures.addAll(((SignatureVerificationImpl)results.getPayerSignatureVerification()).txSigs());
            cryptoSignatures.addAll(getTransactionSignatures(results.getNonPayerSignatureVerifications()));
            cryptoSignatures.addAll(getTransactionSignatures(results.getHollowAccountSignatureVerifications()));

            accessor.addAllCryptoSigs(cryptoSignatures);
            final var preHandleStatus = metadata.status();
            final var payerKey = mapToJKey(results.getPayerSignatureVerification().key());
            if (payerKey != null) {
                if (preHandleStatus != ResponseCodeEnum.OK) {
                    accessor.setSigMeta(forPayerOnly(payerKey, cryptoSignatures, accessor));
                } else {
                    accessor.setSigMeta(forPayerAndOthers(
                            payerKey,
                            results.getNonPayerSignatureVerifications()
                                    .stream()
                                    .map(SignatureVerification::key)
                                    .filter(Objects::nonNull)
                                    .map(this::mapToJKey)
                                    .toList(),
                            cryptoSignatures,
                            accessor));
                }
            } else {
                accessor.setSigMeta(noneAvailable());
            }
            // Prevent the mono-service workflow from rationalizing sigs
            accessor.setExpandedSigStatus(fromPbj(preHandleStatus));
            accessor.setLinkedRefs(new LinkedRefs());
            return accessor;
        } catch (final InvalidProtocolBufferException e) {
            throw new IllegalStateException("An unparseable transaction was submitted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Unknown failure", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while handling sigs", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out waiting for 3 minutes. This is fatal", e);
        }
    }

    private List<TransactionSignature> getTransactionSignatures(List<SignatureVerification> verification) {
        return verification.stream()
                .map(v -> ((SignatureVerificationImpl)v).txSigs())
                .flatMap(List::stream)
                .toList();
    }

    private JKey mapToJKey(@NonNull final Key key) {
        try {
            return JKey.mapKey(key);
        } catch (DecoderException e) {
            throw new RuntimeException("Unable to map key", e);
        }
    }
}
