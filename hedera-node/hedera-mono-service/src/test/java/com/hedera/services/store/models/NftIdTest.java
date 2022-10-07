/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;

class NftIdTest {
    private final long shard = 1;
    private final long realm = 2;
    private final long num = 3;
    private final long serialNo = 4;
    private final long bShard = 2;
    private final long bRealm = 3;
    private final long bNum = 4;
    private final long bSerialNo = 5;

    @Test
    void ordersAsExpected() {
        final var a = new NftId(shard + 1, realm + 1, num, serialNo + 1);
        final var b = new NftId(shard, realm, num + 1, serialNo);
        assertEquals(-1, Integer.signum(a.compareTo(b)));
        final var c = new NftId(shard + 1, realm, num, serialNo + 1);
        assertEquals(+1, Integer.signum(a.compareTo(c)));
        final var d = new NftId(shard + 1, realm + 1, num, serialNo);
        assertEquals(+1, Integer.signum(a.compareTo(d)));
        final var e = new NftId(shard, realm + 1, num, serialNo + 1);
        assertEquals(+1, Integer.signum(a.compareTo(e)));
    }

    @Test
    void objectContractWorks() {
        // given:
        final var subject = new NftId(shard, realm, num, serialNo);
        final var bSubject = new NftId(bShard, realm, num, serialNo);
        final var cSubject = new NftId(shard, bRealm, num, serialNo);
        final var dSubject = new NftId(shard, realm, bNum, serialNo);
        final var eSubject = new NftId(shard, realm, num, bSerialNo);
        final var rSubject = new NftId(shard, realm, num, serialNo);
        final var sSubject = subject;

        // expect:
        assertEquals(subject, rSubject);
        assertEquals(subject.hashCode(), rSubject.hashCode());
        assertEquals(subject, sSubject);
        assertNotEquals(subject, bSubject);
        assertNotEquals(subject.hashCode(), bSubject.hashCode());
        assertNotEquals(subject, cSubject);
        assertNotEquals(subject.hashCode(), cSubject.hashCode());
        assertNotEquals(subject, dSubject);
        assertNotEquals(subject.hashCode(), dSubject.hashCode());
        assertNotEquals(subject, eSubject);
        assertNotEquals(subject.hashCode(), eSubject.hashCode());
    }

    @Test
    void toStringWorks() {
        // setup:
        final var desired = "NftId[shard=1, realm=2, num=3, serialNo=4]";

        // given:
        final var subject = new NftId(shard, realm, num, serialNo);

        // expect:
        assertEquals(desired, subject.toString());
    }

    @Test
    void asEnumPairWorks() {
        final var subject = new NftId(shard, realm, num, serialNo);
        final var expected = EntityNumPair.fromLongs(num, serialNo);

        assertEquals(expected, subject.asEntityNumPair());
    }

    @Test
    void asNftNumPairWorks() {
        final var subject = new NftId(shard, realm, num, serialNo);
        final var expected = NftNumPair.fromLongs(num, serialNo);

        assertEquals(expected, subject.asNftNumPair());
    }

    @Test
    void gettersWork() {
        // given:
        final var subject = new NftId(shard, realm, num, serialNo);
        TokenID expectedTokenId =
                TokenID.newBuilder().setShardNum(shard).setRealmNum(realm).setTokenNum(num).build();

        assertEquals(shard, subject.shard());
        assertEquals(realm, subject.realm());
        assertEquals(num, subject.num());
        assertEquals(serialNo, subject.serialNo());
        assertEquals(expectedTokenId, subject.tokenId());
    }
}
