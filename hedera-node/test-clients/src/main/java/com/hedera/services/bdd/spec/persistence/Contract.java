/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.persistence.Entity.UNUSED_KEY;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.asAdminKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.adminKeyFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import java.util.Optional;

public class Contract {
    private static final String UNSPECIFIED_BYTECODE_FILE = null;
    private static final String MISSING_MEMO = null;

    private String memo = MISSING_MEMO;
    private String bytecodeFile = UNSPECIFIED_BYTECODE_FILE;
    private SpecKey adminKey = UNUSED_KEY;

    public void registerWhatIsKnown(HapiSpec spec, String name, Optional<EntityId> entityId) {
        if (bytecodeFile == UNSPECIFIED_BYTECODE_FILE) {
            throw new IllegalStateException(String.format("Contract '%s' has no given bytecode file!", name));
        }
        if (adminKey != UNUSED_KEY) {
            adminKey.registerWith(spec, asAdminKeyFor(name));
        }
        entityId.ifPresent(id -> spec.registry().saveContractId(name, id.asContract()));
    }

    public HapiQueryOp<?> existenceCheck(String name) {
        return getContractInfo(name);
    }

    HapiSpecOperation createOp(String name) {
        var op = contractCreate(name).bytecode(bytecodeFile).advertisingCreation();

        if (adminKey != UNUSED_KEY) {
            op.adminKey(adminKeyFor(name));
        }
        if (memo != MISSING_MEMO) {
            op.entityMemo(memo);
        }

        return op;
    }

    public String getBytecodeFile() {
        return bytecodeFile;
    }

    public void setBytecodeFile(String bytecodeFile) {
        this.bytecodeFile = bytecodeFile;
    }

    public SpecKey getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(SpecKey adminKey) {
        this.adminKey = adminKey;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
