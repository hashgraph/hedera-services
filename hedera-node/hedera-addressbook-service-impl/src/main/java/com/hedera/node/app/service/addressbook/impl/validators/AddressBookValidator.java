/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.addressbook.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FQDN_SIZE_TOO_LARGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.GOSSIP_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_IPV4_ADDRESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_DESCRIPTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SERVICE_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.node.config.data.NodesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AddressBookValidator {
    /**
     * Default constructor for injection.
     */
    @Inject
    public AddressBookValidator() {
        // Dagger2
    }

    /**
     * Validates the node description.
     *
     * @param description The description to validate
     * @param nodesConfig The nodes configuration
     */
    public void validateDescription(@Nullable final String description, @NonNull final NodesConfig nodesConfig) {
        if (description == null || description.isEmpty()) {
            return;
        }
        final var raw = description.getBytes(StandardCharsets.UTF_8);
        final var maxUtf8Bytes = nodesConfig.nodeMaxDescriptionUtf8Bytes();
        validateFalse(raw.length > maxUtf8Bytes, INVALID_NODE_DESCRIPTION);
        validateFalse(containsZeroByte(raw), INVALID_NODE_DESCRIPTION);
    }

    private boolean containsZeroByte(@NonNull final byte[] bytes) {
        boolean ret = false;
        for (final byte b : bytes) {
            if (b == 0) {
                ret = true;
                break;
            }
        }
        return ret;
    }

    /**
     * Validates the gossip endpoint.
     *
     * @param endpointList The list of GossipEndpoint to validate
     * @param nodesConfig The nodes configuration
     */
    public void validateGossipEndpoint(
            @Nullable final List<ServiceEndpoint> endpointList, @NonNull final NodesConfig nodesConfig) {
        validateFalse(endpointList == null || endpointList.isEmpty(), INVALID_GOSSIP_ENDPOINT);
        validateFalse(endpointList.size() > nodesConfig.maxGossipEndpoint(), GOSSIP_ENDPOINTS_EXCEEDED_LIMIT);
        // for phase 2: The first in the list is used as the Internal IP address in config.txt,
        // the second in the list is used as the External IP address in config.txt
        validateFalse(endpointList.size() < 2, INVALID_GOSSIP_ENDPOINT);

        for (final var endpoint : endpointList) {
            validateFalse(
                    nodesConfig.gossipFqdnRestricted() && !endpoint.domainName().isEmpty(),
                    GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN);
            validateEndpoint(endpoint, nodesConfig);
        }
    }

    /**
     * Validates the service endpoint.
     *
     * @param endpointList The list of ServiceEndpoint to validate
     * @param nodesConfig The nodes configuration
     */
    public void validateServiceEndpoint(
            @Nullable final List<ServiceEndpoint> endpointList, @NonNull final NodesConfig nodesConfig) {
        validateFalse(endpointList == null || endpointList.isEmpty(), INVALID_SERVICE_ENDPOINT);
        validateFalse(endpointList.size() > nodesConfig.maxServiceEndpoint(), INVALID_SERVICE_ENDPOINT);
        for (final var endpoint : endpointList) {
            validateEndpoint(endpoint, nodesConfig);
        }
    }

    private void validateEndpoint(@NonNull final ServiceEndpoint endpoint, @NonNull final NodesConfig nodesConfig) {
        validateFalse(endpoint.port() == 0, INVALID_ENDPOINT);
        validateFalse(
                (endpoint.ipAddressV4().length() == 0 || endpoint.ipAddressV4().equals(Bytes.EMPTY))
                        && endpoint.domainName().trim().isEmpty(),
                INVALID_ENDPOINT);
        validateFalse(
                endpoint.ipAddressV4().length() != 0
                        && !endpoint.ipAddressV4().equals(Bytes.EMPTY)
                        && !endpoint.domainName().trim().isEmpty(),
                IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT);
        validateFalse(endpoint.domainName().trim().length() > nodesConfig.maxFqdnSize(), FQDN_SIZE_TOO_LARGE);
        validateFalse(
                endpoint.ipAddressV4().length() != 0
                        && !endpoint.ipAddressV4().equals(Bytes.EMPTY)
                        && !isIPv4(endpoint.ipAddressV4()),
                INVALID_IPV4_ADDRESS);
    }

    private boolean isIPv4(@NonNull final Bytes ip) {
        requireNonNull(ip);
        final var part = "(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])";
        final var regex = part + "\\." + part + "\\." + part + "\\." + part;
        return ip.asUtf8String().matches(regex);
    }
}
