package com.hedera.node.app.spi;

import com.hederahashgraph.api.proto.java.AccountID;

/**
 * An interface used for looking up Keys on the account
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
}
