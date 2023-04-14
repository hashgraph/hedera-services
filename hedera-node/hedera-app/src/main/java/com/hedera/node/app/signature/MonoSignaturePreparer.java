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

package com.hedera.node.app.signature;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.exception.InvalidAccountIDException;
import com.hedera.node.app.service.mono.legacy.exception.KeyPrefixMismatchException;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.sigs.Expansion;
import com.hedera.node.app.service.mono.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.verification.PrecheckVerifier;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.state.HederaState;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of {@link SignaturePreparer} that delegates to the respective mono-service's functionality
 */
@Singleton
public class MonoSignaturePreparer implements SignaturePreparer {
    private final PrecheckVerifier precheckVerifier;

    private final Expansion.CryptoSigsCreation cryptoSigsCreation;
    private final Function<SignatureMap, PubKeyToSigBytes> keyToSigFactory;
    private final Function<TxnAccessor, TxnScopedPlatformSigFactory> scopedFactoryProvider;

    @Inject
    public MonoSignaturePreparer(
            final @NonNull PrecheckVerifier precheckVerifier,
            final @NonNull Expansion.CryptoSigsCreation cryptoSigsCreation,
            final @NonNull Function<SignatureMap, PubKeyToSigBytes> keyToSigFactory,
            final @NonNull Function<TxnAccessor, TxnScopedPlatformSigFactory> scopedFactoryProvider) {
        this.precheckVerifier = Objects.requireNonNull(precheckVerifier);
        this.cryptoSigsCreation = Objects.requireNonNull(cryptoSigsCreation);
        this.keyToSigFactory = Objects.requireNonNull(keyToSigFactory);
        this.scopedFactoryProvider = Objects.requireNonNull(scopedFactoryProvider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SigExpansionResult expandedSigsFor(
            final @NonNull Transaction transaction,
            final @NonNull HederaKey payerKey,
            final @NonNull List<HederaKey> otherPartyKeys) {
        final var accessor = SignedTxnAccessor.uncheckedFrom(transaction);
        final var keyToSig = keyToSigFactory.apply(PbjConverter.toPbj(accessor.getSigMap()));
        final var scopedFactory = scopedFactoryProvider.apply(accessor);

        final List<TransactionSignature> netCryptoSigs = new ArrayList<>();
        final var payerResult = cryptoSigsCreation.createFrom(List.of((JKey) payerKey), keyToSig, scopedFactory);
        if (payerResult.hasFailed()) {
            return new SigExpansionResult(netCryptoSigs, PbjConverter.toPbj(payerResult.asCode()));
        }
        netCryptoSigs.addAll(payerResult.getPlatformSigs());
        final var otherPartiesResult = cryptoSigsCreation.createFrom((List) otherPartyKeys, keyToSig, scopedFactory);
        if (otherPartiesResult.hasFailed()) {
            return new SigExpansionResult(netCryptoSigs, PbjConverter.toPbj(otherPartiesResult.asCode()));
        }
        netCryptoSigs.addAll(otherPartiesResult.getPlatformSigs());
        return new SigExpansionResult(netCryptoSigs, OK);
    }

    @Override
    public ResponseCodeEnum syncGetPayerSigStatus(final @NonNull Transaction transaction) {
        try {
            final var accessor = SignedTxnAccessor.uncheckedFrom(transaction);
            return precheckVerifier.hasNecessarySignatures(accessor) ? OK : INVALID_SIGNATURE;
        } catch (final KeyPrefixMismatchException ignore) {
            return KEY_PREFIX_MISMATCH;
        } catch (final InvalidAccountIDException ignore) {
            return INVALID_ACCOUNT_ID;
        } catch (final Exception ignore) {
            return INVALID_SIGNATURE;
        }
    }

    @NonNull
    @Override
    public TransactionSignature prepareSignature(
            final @NonNull HederaState state,
            final @NonNull byte[] txBodyBytes,
            final @NonNull SignatureMap signatureMap,
            final @NonNull AccountID accountID) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NonNull
    @Override
    public Map<HederaKey, TransactionSignature> prepareSignatures(
            @NonNull HederaState state,
            @NonNull byte[] txBodyBytes,
            @NonNull SignatureMap signatureMap,
            @NonNull Set<HederaKey> keys) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
