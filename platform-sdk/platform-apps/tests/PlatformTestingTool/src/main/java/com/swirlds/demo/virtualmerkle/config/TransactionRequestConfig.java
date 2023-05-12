/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.virtualmerkle.config;

import com.swirlds.demo.platform.HotspotConfiguration;
import com.swirlds.demo.platform.PAYLOAD_TYPE;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class has the responsibility to hold the {@code type} of a transaction and how many times
 * the transaction must be submitted.
 */
public final class TransactionRequestConfig {

    private PAYLOAD_TYPE type;

    private long amount;

    private HotspotConfiguration hotspot;

    public TransactionRequestConfig() {}

    public TransactionRequestConfig(TransactionRequestConfig source) {
        this.type = source.type;
        this.amount = source.amount;
        this.hotspot = source.hotspot;
    }

    /**
     * @return The type of the transaction from this request.
     */
    public PAYLOAD_TYPE getType() {
        return type;
    }

    /**
     * Sets the new type of the transaction for this request.
     *
     * @param type
     * 		The type of the transaction from this request.
     */
    public void setType(final PAYLOAD_TYPE type) {
        this.type = type;
    }

    /**
     * @return The amount of times this transaction must be submitted.
     */
    public long getAmount() {
        return amount;
    }

    /**
     * Sets the new amount of times this transaction must be submitted.
     *
     * @param amount
     * 		The amount of times this transaction must be submitted.
     */
    public void setAmount(final long amount) {
        this.amount = amount;
    }

    /**
     * Decrements and return the number of times this transaction must be submitted.
     *
     * @return The new number of times this transaction must be submitted.
     */
    public long decrementAndGetAmount() {
        return --amount;
    }

    /**
     * Get the hotspot configuration for this group of transactions, or null if there is no configured hotspot.
     *
     * @return hotspot configuration
     */
    public HotspotConfiguration getHotspot() {
        return hotspot;
    }

    /**
     * Set the hotspot configuration for this group of transactions.
     *
     * @param hotspot
     * 			hotspot configuration
     */
    public void setHotspot(final HotspotConfiguration hotspot) {
        this.hotspot = hotspot;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final TransactionRequestConfig that = (TransactionRequestConfig) o;

        return new EqualsBuilder()
                .append(amount, that.amount)
                .append(type, that.type)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(type).append(amount).toHashCode();
    }

    @Override
    public String toString() {
        return "TransactionRequestConfig{" + "type=" + type + ", amount=" + amount + '}';
    }
}
