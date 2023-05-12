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

import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;

public class EntityId {
    private static final long DEFAULT_SHARD = 0;
    private static final long DEFAULT_REALM = 0;

    public static final EntityId TREASURY = new EntityId("0.0.2");

    public EntityId() {}

    public EntityId(String literal) {
        long[] parts = asDotDelimitedLongArray(literal);
        shard = parts[0];
        realm = parts[1];
        num = parts[2];
    }

    public String asLiteral() {
        return String.format("%d.%d.%d", shard, realm, num);
    }

    public AccountID asAccount() {
        return HapiPropertySource.asAccount(asLiteral());
    }

    public ScheduleID asSchedule() {
        return HapiPropertySource.asSchedule(asLiteral());
    }

    public TokenID asToken() {
        return HapiPropertySource.asToken(asLiteral());
    }

    public TopicID asTopic() {
        return HapiPropertySource.asTopic(asLiteral());
    }

    public FileID asFile() {
        return HapiPropertySource.asFile(asLiteral());
    }

    public ContractID asContract() {
        return HapiPropertySource.asContract(asLiteral());
    }

    private long shard = DEFAULT_SHARD;
    private long realm = DEFAULT_REALM;
    private long num;

    public long getShard() {
        return shard;
    }

    public void setShard(long shard) {
        this.shard = shard;
    }

    public long getRealm() {
        return realm;
    }

    public void setRealm(long realm) {
        this.realm = realm;
    }

    public long getNum() {
        return num;
    }

    public void setNum(long num) {
        this.num = num;
    }
}
