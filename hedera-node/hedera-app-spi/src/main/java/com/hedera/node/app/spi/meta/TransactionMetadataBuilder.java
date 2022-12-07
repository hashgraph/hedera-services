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
package com.hedera.node.app.spi.meta;

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;


/**
 * Metadata collected when transactions are handled as part of "pre-handle" needed for signature
 * verification. This class may have subclasses in the future.
 *
 * <p>NOTE: This class shouldn't exist here, and is something of a puzzle. We cannot add it to SPI,
 * because it includes a dependency on AccountStore. But we also cannot put it in the app module,
 * because doing so would cause service modules to have a circular dependency on the app module.
 * Maybe we need some kind of base module from which services can extend and put it there?
 */
public interface TransactionMetadataBuilder {
    /**
     * Sets status on {@link TransactionMetadata}. It will be {@link ResponseCodeEnum#OK} if there is no failure.
     * @param status status to be set on {@link TransactionMetadata}
     * @return builder object
     */
    TransactionMetadataBuilder status(final ResponseCodeEnum status);

    /**
     * Fetches the payer key and add to required keys in {@link TransactionMetadata}.
     * @param payer payer for the transaction
     * @return builder object
     */
    TransactionMetadataBuilder payerKeyFor(final AccountID payer);

    /**
     * Adds given key to required keys in {@link TransactionMetadata}.
     * @param key key to be added
     * @return builder object
     */
    TransactionMetadataBuilder addToReqKeys(final HederaKey key);

    /**
     * Adds the {@link TransactionBody} of the transaction on {@link TransactionMetadata}.
     * @param txn transaction body of the transaction
     * @return builder object
     */
    TransactionMetadataBuilder txnBody(final TransactionBody txn);

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the default failureReason given in the result.
     *
     * @param id given accountId
     */
    default TransactionMetadataBuilder addNonPayerKey(final AccountID id){
        return addNonPayerKey(id, null);
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the given failure reason on the metadata. If the
     * failureReason is null, sets the default failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatus given failure status
     */
    TransactionMetadataBuilder addNonPayerKey(final AccountID id, final ResponseCodeEnum failureStatus);

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account if receiverSigRequired is true on the account. If the lookup fails, sets the
     * given failure reason on the metadata. If the failureReason is null, sets the default
     * failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatus given failure status
     */
    TransactionMetadataBuilder addNonPayerKeyIfReceiverSigRequired(final AccountID id, final ResponseCodeEnum failureStatus);

    /**
     * Builds {@link TransactionMetadata} object from builder
     * @return TransactionMetadata object
     */
    TransactionMetadata build();
}
