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
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.impl.SignatureVerificationFutureImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.codec.DecoderException;

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

    @NonNull
    private SwirldsTxnAccessor adaptForMono(final ConsensusTransaction platformTxn, final PreHandleResult phr) {
        try {
            final var accessor = PlatformTxnAccessor.from(platformTxn.getContents());

            // To work with the mono-service, we have to turn all the sigs BACK into TransactionSignatures
            final var cryptoSignatures = new ArrayList<TransactionSignature>();
            // Add the TransactionSignatures for every cryptographic key we verified
            final var verificationResults = phr.verificationResults();
            if (verificationResults != null) {
                cryptoSignatures.addAll(extract(verificationResults.values()));
            }

            accessor.addAllCryptoSigs(cryptoSignatures);
            final var preHandleResponseCode = phr.responseCode();
            final var payerKey = phr.payerKey();
            if (payerKey != null) {
                final var jkey = mapToJKey(payerKey);
                if (preHandleResponseCode != OK) {
                    accessor.setSigMeta(forPayerOnly(jkey, cryptoSignatures, accessor));
                } else {
                    final List<JKey> otherPayerKeys = verificationResults == null
                            ? Collections.emptyList()
                            : verificationResults.keySet().stream()
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
        }
    }

    @NonNull
    private List<TransactionSignature> extract(@Nullable final Collection<SignatureVerificationFuture> map) {
        if (map == null) return Collections.emptyList();
        return map.stream()
                .map(SignatureVerificationFutureImpl.class::cast)
                .map(SignatureVerificationFutureImpl::txSig)
                .toList();
    }

    @NonNull
    private JKey mapToJKey(@NonNull final Key key) {
        try {
            return JKey.mapKey(key);
        } catch (DecoderException e) {
            throw new IllegalArgumentException("Unable to map key", e);
        }
    }
}
