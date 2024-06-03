/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.info;

import static com.hedera.hapi.util.HapiUtils.parseAccount;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.spi.info.NodeInfo;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.system.address.Address;
import edu.umd.cs.findbugs.annotations.NonNull;

public record NodeInfoImpl(
        long nodeId,
        @NonNull AccountID accountId,
        long stake,
        @NonNull String externalHostName,
        int externalPort,
        @NonNull String hexEncodedPublicKey,
        @NonNull String memo)
        implements NodeInfo {
    @NonNull
    static NodeInfo fromAddress(@NonNull final Address address) {
        return new NodeInfoImpl(
                address.getNodeId().id(),
                parseAccount(address.getMemo()),
                address.getWeight(),
                requireNonNull(address.getHostnameExternal()),
                address.getPortExternal(),
                CommonUtils.hex(requireNonNull(address.getSigPublicKey()).getEncoded()),
                address.getMemo());
    }
}
