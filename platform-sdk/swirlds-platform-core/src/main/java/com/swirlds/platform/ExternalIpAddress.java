/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * External Ip Address found by {@link Network#getExternalIpAddress()}
 */
public class ExternalIpAddress {

    public static final ExternalIpAddress NO_IP = new ExternalIpAddress(IpAddressStatus.NO_IP_FOUND, "");
    public static final ExternalIpAddress UPNP_DISABLED =
            new ExternalIpAddress(IpAddressStatus.ROUTER_UPNP_DISABLED, "");
    private final IpAddressStatus status;
    private final String ipAddress;

    ExternalIpAddress(final IpAddressStatus status, final String ipAddress) {
        this.status = status;
        this.ipAddress = ipAddress;
    }

    ExternalIpAddress(final String ipAddress) {
        this(IpAddressStatus.IP_FOUND, ipAddress);
    }

    public IpAddressStatus getStatus() {
        return status;
    }

    /**
     * If External IP address is found, then the address will be returned
     * in ipv4 or ipv6 format. Otherwise, an empty string
     *
     * @return External ip address or blank
     */
    public String getIpAddress() {
        return this.ipAddress;
    }

    /**
     * If External IP address is found, it is returned in ipv4/ipv6 format.
     * Otherwise, a string describing the status
     *
     * @return String representation of {@link ExternalIpAddress}
     */
    @Override
    public String toString() {
        if (this.status == IpAddressStatus.IP_FOUND) {
            return this.getIpAddress();
        }

        return this.status.name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ExternalIpAddress)) {
            return false;
        }

        final ExternalIpAddress that = (ExternalIpAddress) o;
        return new EqualsBuilder()
                .append(status, that.status)
                .append(ipAddress, that.ipAddress)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(status).append(ipAddress).hashCode();
    }
}
