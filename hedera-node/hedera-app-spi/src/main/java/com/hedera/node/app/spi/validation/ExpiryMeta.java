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

package com.hedera.node.app.spi.validation;

/**
 * Encapsulates expiry/auto-renewal metadata for an entity.
 *
 * <p>Is <b>not</b> required to be a valid combination of values; so it can,
 * for example, be used to represent an invalid entity creation attempt.
 *
 * @param expiry the consensus second at which the entity expires
 * @param autoRenewPeriod the number of seconds between auto-renewals
 * @param autoRenewNum the number of the account to be charged for auto-renewals
 */
public record ExpiryMeta(long expiry, long autoRenewPeriod, long autoRenewNum) {
    /**
     * A sentinel value indicating some part of the metadata is not available..
     */
    public static long NA = Long.MIN_VALUE;

    /**
     * Returns true if the expiry is explicitly set.
     *
     * @return whether this metadata has explicit expiry
     */
    public boolean hasExplicitExpiry() {
        return expiry != NA;
    }

    /**
     * Returns true if the auto-renew period is explicitly set.
     *
     * @return whether this metadata has explicit auto-renew period
     */
    public boolean hasAutoRenewPeriod() {
        return autoRenewPeriod != NA;
    }

    /**
     * Returns true if the auto-renew account is explicitly set.
     *
     * @return whether this metadata has explicit auto-renew account
     */
    public boolean hasAutoRenewNum() {
        return autoRenewNum != NA;
    }

    /**
     * Returns true if the auto-renew period and account are both explicitly set.
     *
     * @return whether this metadata has explicit auto-renew period and account
     */
    public boolean hasFullAutoRenewSpec() {
        return hasAutoRenewNum() && hasAutoRenewPeriod();
    }
}
