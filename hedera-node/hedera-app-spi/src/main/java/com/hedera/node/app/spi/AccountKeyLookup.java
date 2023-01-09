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
package com.hedera.node.app.spi;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;

/**
 * An interface used for looking up Keys on the account. NOTE: This class can be modified to return
 * any other fields needed from account object if needed in the future.
 */
public interface AccountKeyLookup {

    /**
     * Fetches the account's key from given accountID. If the key could not be fetched as the given
     * accountId is invalid or doesn't exist provides information about the failure failureReason.
     * If there is no failure failureReason will be null.
     *
     * @param idOrAlias account id whose key should be fetched
     * @return key if successfully fetched or failureReason for failure
     */
    KeyOrLookupFailureReason getKey(final AccountID idOrAlias);

    /**
     * Fetches the account's key from given accountID and returns the keys if the account has
     * receiverSigRequired flag set to true.
     *
     * <p>If the receiverSigRequired flag is not true on the account, returns key as null and
     * failureReason as null. If the key could not be fetched as the given accountId is invalid or
     * doesn't exist, provides information about the failure failureReason. If there is no failure
     * failureReason will be null.
     *
     * @param idOrAlias account id whose key should be fetched
     * @return key if successfully fetched or failureReason for failure
     */
    KeyOrLookupFailureReason getKeyIfReceiverSigRequired(final AccountID idOrAlias);

    /**
     * Fetches the account's key from given contractID. If the key could not be fetched as the given
     * contractID is invalid or doesn't exist provides information about the failure failureReason.
     * If there is no failure failureReason will be null.
     *
     * @param idOrAlias account id whose key should be fetched
     * @return key if successfully fetched or failureReason for failure
     */
    KeyOrLookupFailureReason getKey(final ContractID idOrAlias);

    /**
     * Fetches the account's key from given contractID and returns the keys if the account has
     * receiverSigRequired flag set to true.
     *
     * <p>If the receiverSigRequired flag is not true on the account, returns key as null and
     * failureReason as null. If the key could not be fetched as the given contractID is invalid or
     * doesn't exist, provides information about the failure failureReason. If there is no failure
     * failureReason will be null.
     *
     * @param idOrAlias account id whose key should be fetched
     * @return key if successfully fetched or failureReason for failure
     */
    KeyOrLookupFailureReason getKeyIfReceiverSigRequired(final ContractID idOrAlias);
}
