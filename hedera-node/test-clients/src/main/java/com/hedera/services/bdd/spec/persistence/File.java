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

package com.hedera.services.bdd.spec.persistence;

import static com.hedera.services.bdd.spec.persistence.Entity.UNUSED_KEY_LIST;
import static com.hedera.services.bdd.spec.persistence.EntityManager.FILES_SUBDIR;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.under;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static java.util.stream.Collectors.toList;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import java.util.Optional;
import java.util.stream.IntStream;

public class File {
    private static final String UNSPECIFIED_CONTENTS_LOC = null;
    private static final String DEFAULT_CONTENTS = "What America did you have / When Charon quit poling his ferry";
    private static final String CONTENTS_SUBDIR = "code";
    private static final String MISSING_MEMO = null;

    private String memo = MISSING_MEMO;
    private String data = UNSPECIFIED_CONTENTS_LOC;
    private SpecKeyList wacl = UNUSED_KEY_LIST;

    public void registerWhatIsKnown(HapiSpec spec, String name, Optional<EntityId> entityId) {
        if (wacl != UNUSED_KEY_LIST) {
            for (int i = 0, n = wacl.getListOf().size(); i < n; i++) {
                wacl.getListOf().get(i).registerWith(spec, under(name + i));
            }
        }
        entityId.ifPresent(id -> spec.registry().saveFileId(name, id.asFile()));
    }

    public HapiQueryOp<?> existenceCheck(String name) {
        return getFileInfo(name);
    }

    HapiSpecOperation createOp(String name) {
        var op = fileCreate(name).advertisingCreation();

        if (data == UNSPECIFIED_CONTENTS_LOC) {
            op.contents(DEFAULT_CONTENTS);
        } else {
            op.path(spec -> spec.setup().persistentEntitiesDir()
                    + java.io.File.separator
                    + String.join(java.io.File.separator, new String[] {FILES_SUBDIR, CONTENTS_SUBDIR, data}));
        }

        if (memo != MISSING_MEMO) {
            op.entityMemo(memo);
        }

        if (wacl != UNUSED_KEY_LIST) {
            var constituents = IntStream.range(0, wacl.getListOf().size())
                    .mapToObj(i -> name + i)
                    .collect(toList());
            op.key(name);
            return blockingOrder(newKeyListNamed(name, constituents), op);
        }

        return op;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public SpecKeyList getWacl() {
        return wacl;
    }

    public void setWacl(SpecKeyList wacl) {
        this.wacl = wacl;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
