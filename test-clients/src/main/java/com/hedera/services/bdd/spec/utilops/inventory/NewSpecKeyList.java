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
package com.hedera.services.bdd.spec.utilops.inventory;

import static java.util.stream.Collectors.toList;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewSpecKeyList extends UtilOp {
    static final Logger log = LogManager.getLogger(NewSpecKeyList.class);

    private final String name;
    private final List<String> keys;

    public NewSpecKeyList(String name, List<String> keys) {
        this.name = name;
        this.keys = keys;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        List<Key> childKeys = keys.stream().map(spec.registry()::getKey).collect(toList());
        Key newList =
                Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(childKeys)).build();
        spec.registry().saveKey(name, newList);

        SigControl[] childControls =
                childKeys.stream().map(spec.keys()::controlFor).toArray(SigControl[]::new);
        spec.keys().setControl(newList, SigControl.listSigs(childControls));

        return false;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("name", name);
    }
}
