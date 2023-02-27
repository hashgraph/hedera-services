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

package com.swirlds.logging.payloads;

/**
 * Log payload used in {@code com.swirlds.platform.Browser} to indicate a condition where no local machine address
 * matched the loaded address book.
 */
public class NodeAddressMismatchPayload extends AbstractLogPayload {

    /**
     * The internal IP address for this machine.
     */
    private String internalIpAddress;

    /**
     * The external IP address for this machine.
     */
    private String externalIpAddress;

    /**
     * Constructs a new payload with the specified {@code internalIpAddress} and no {@code externalIpAddress} specified.
     *
     * @param internalIpAddress
     * 		the internal IP address.
     * @throws IllegalArgumentException
     * 		if the {@code internalIpAddress} argument is a null reference.
     */
    public NodeAddressMismatchPayload(final String internalIpAddress) {
        this(internalIpAddress, null);
    }

    /**
     * Constructs a new payload with the specified {@code internalIpAddress} and {@code externalIpAddress}.
     *
     * @param internalIpAddress
     * 		the internal IP address.
     * @param externalIpAddress
     * 		the external IP address.
     * @throws IllegalArgumentException
     * 		if the {@code internalIpAddress} argument is a null reference.
     */
    public NodeAddressMismatchPayload(final String internalIpAddress, final String externalIpAddress) {
        super("No AddressBook entry found for the available machine IP addresses");

        if (internalIpAddress == null) {
            throw new IllegalArgumentException("The supplied argument 'internalIpAddress' cannot be null!");
        }

        this.internalIpAddress = internalIpAddress;
        this.externalIpAddress = externalIpAddress;
    }

    /**
     * Gets the internal IP address for this machine.
     *
     * @return the internal IP address.
     */
    public String getInternalIpAddress() {
        return internalIpAddress;
    }

    /**
     * Sets the internal IP address for this machine.
     *
     * @param internalIpAddress
     * 		the internal IP address.
     * @throws IllegalArgumentException
     * 		if the {@code internalIpAddress} argument is a null reference.
     */
    public void setInternalIpAddress(final String internalIpAddress) {
        if (internalIpAddress == null) {
            throw new IllegalArgumentException("The supplied argument 'internalIpAddress' cannot be null!");
        }
        this.internalIpAddress = internalIpAddress;
    }

    /**
     * Gets the external IP address (if available) for this machine. This method may return a {@code null} reference.
     *
     * @return the external IP address.
     */
    public String getExternalIpAddress() {
        return externalIpAddress;
    }

    /**
     * Sets the external IP address (if available) for this machine.
     *
     * @param externalIpAddress
     * 		the external IP address for this machine. A {@code null} reference may be passed if the external IP
     * 		address is unavailable.
     */
    public void setExternalIpAddress(final String externalIpAddress) {
        this.externalIpAddress = externalIpAddress;
    }
}
