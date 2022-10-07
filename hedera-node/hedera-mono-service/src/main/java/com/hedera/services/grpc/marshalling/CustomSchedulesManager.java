/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import com.hedera.services.store.models.Id;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import java.util.ArrayList;
import java.util.List;

public class CustomSchedulesManager {
    private final CustomFeeSchedules customFeeSchedules;
    private final List<CustomFeeMeta> allManagedMeta = new ArrayList<>();

    public CustomSchedulesManager(CustomFeeSchedules customFeeSchedules) {
        this.customFeeSchedules = customFeeSchedules;
    }

    public CustomFeeMeta managedSchedulesFor(Id token) {
        CustomFeeMeta extantMeta = null;
        if (!allManagedMeta.isEmpty()) {
            for (var meta : allManagedMeta) {
                if (token.equals(meta.tokenId())) {
                    extantMeta = meta;
                    break;
                }
            }
        }
        if (extantMeta == null) {
            extantMeta = customFeeSchedules.lookupMetaFor(token);
            allManagedMeta.add(extantMeta);
        }
        return extantMeta;
    }

    public List<CustomFeeMeta> metaUsed() {
        return allManagedMeta;
    }
}
