/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.config.legacy;

import java.net.InetAddress;

/**
 * This record defines the set of parameters that can be defined for the {@code address} property in the legacy
 * config.txt file.
 *
 * @param nickname
 * 		the name given to that addressbook member by the member creating this address
 * @param selfName
 * 		the name given to that addressbook member by themself
 * @param weight
 * 		the amount of weight (0 if they should have no influence on the consensus)
 * @param internalInetAddressName
 * 		IPv4 address on the inside of the NATing router
 * @param internalPort
 * 		port for the internal IPv4 address
 * @param externalInetAddressName
 * 		IPv4 address on the outside of the NATing router (same as internal if there is no NAT)
 * @param externalPort
 * 		port for the external IPv4 address
 * @param memo
 * 		additional information about the node
 * @deprecated will be removed once we have removed the "legacy" {@code config.txt} file.
 */
@Deprecated(forRemoval = true)
public record AddressConfig(
        String nickname,
        String selfName,
        long weight,
        InetAddress internalInetAddressName,
        int internalPort,
        InetAddress externalInetAddressName,
        int externalPort,
        String memo) {}
