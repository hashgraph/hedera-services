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

package com.hedera.node.app.hapi.utils.fee;

public record FeeObject(long nodeFee, long networkFee, long serviceFee) {

    // TODO: These getters need to be removed, but this will results in a lot of changes.
    //  So, we will remove them in a separate PR.

    @Deprecated(forRemoval = true)
    public long getNodeFee() {
        return nodeFee;
    }

    @Deprecated(forRemoval = true)
    public long getNetworkFee() {
        return networkFee;
    }

    @Deprecated(forRemoval = true)
    public long getServiceFee() {
        return serviceFee;
    }

    public long totalFee() {
        return nodeFee + networkFee + serviceFee;
    }
}
