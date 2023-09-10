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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Encapsulates some strategies of decoding ABI calls, extracted here to ease unit testing.
 */
@Singleton
public class DecodingStrategies {
    @Inject
    public DecodingStrategies() {
        // Dagger2
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#CRYPTO_TRANSFER} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCryptoTransfer(@NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#CRYPTO_TRANSFER_V2} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCryptoTransferV2(@NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#TRANSFER_TOKENS} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferTokens(@NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#TRANSFER_TOKEN} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferToken(@NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#TRANSFER_NFTS} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferNfts(@NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#TRANSFER_NFT} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferNft(@NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#HRC_TRANSFER_FROM} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeHrcTransferFrom(@NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#HRC_TRANSFER_NFT_FROM} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeHrcTransferNftFrom(@NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }
}
