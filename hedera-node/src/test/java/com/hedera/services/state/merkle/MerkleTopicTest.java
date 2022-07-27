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
package com.hedera.services.state.merkle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerkleTopicTest {
    String[] memos =
            new String[] {
                "First memo", "Second memo", "Third memo",
            };
    JKey[] adminKeys =
            new JKey[] {
                null,
                new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes()),
                new JKeyList(
                        List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes())))
            };
    JKey[] submitKeys =
            new JKey[] {
                null,
                new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes()),
                new JKeyList(
                        List.of(new JEd25519Key("AbCdEfGhIjKlMnOpQrStUvWxYz012345".getBytes())))
            };

    @Test
    void toStringWorks() throws IOException {
        // expect:
        assertEquals(
                "MerkleTopic{number=0 <-> 0.0.0, "
                        + "memo=First memo, "
                        + "expiry=1234567.0, "
                        + "deleted=false, "
                        + "adminKey=<N/A>, "
                        + "submitKey=<N/A>, "
                        + "runningHash=<N/A>, "
                        + "sequenceNumber=0, "
                        + "autoRenewSecs=1234567, "
                        + "autoRenewAccount=1.2.3}",
                topicFrom(0).toString());
        // and:
        assertEquals(
                "MerkleTopic{number=1 <-> 0.0.1, "
                        + "memo=Second memo, "
                        + "expiry=2234567.1, "
                        + "deleted=false, "
                        + "adminKey="
                        + MiscUtils.describe(adminKeys[1])
                        + ", "
                        + "submitKey="
                        + MiscUtils.describe(submitKeys[1])
                        + ", runningHash=3c8e1604b2cd20068f02976fa10217491561cc864b7bff28451e1f1a0a8c58c02df56f60562f129e845e0ba16e3420eb,"
                        + " sequenceNumber=1, autoRenewSecs=2234567, autoRenewAccount=2.4.6}",
                topicFrom(1).toString());
        // and:
        assertEquals(
                "MerkleTopic{number=2 <-> 0.0.2, "
                        + "memo=Third memo, "
                        + "expiry=3234567.2, "
                        + "deleted=false, "
                        + "adminKey="
                        + MiscUtils.describe(adminKeys[2])
                        + ", "
                        + "submitKey="
                        + MiscUtils.describe(submitKeys[2])
                        + ", runningHash=a19f77d351424204e3eeec1bb42bcdc728e521483bb99103dc7fa7c527db0c14aeefe4b0a8a7d0924b2f2c4a1d237bc5,"
                        + " sequenceNumber=2, autoRenewSecs=3234567, autoRenewAccount=3.6.9}",
                topicFrom(2).toString());
    }

    @Test
    void merkleMethodsWork() {
        final var topic = new MerkleTopic();
        assertEquals(MerkleTopic.CURRENT_VERSION, topic.getVersion());
        assertEquals(MerkleTopic.RUNTIME_CONSTRUCTABLE_ID, topic.getClassId());
    }

    @Test
    void copyWorks() throws IOException {
        final var original = topicFrom(2);
        final var reproduction = original.copy();
        assertEquals(original, reproduction);
    }

    private MerkleTopic topicFrom(int s) throws IOException {
        long v = 1_234_567L + s * 1_000_000L;
        long t = s + 1;
        AccountID payer = AccountID.newBuilder().setAccountNum(123).build();
        TopicID id = TopicID.newBuilder().setTopicNum(s).build();
        var topic =
                new MerkleTopic(
                        memos[s],
                        adminKeys[s],
                        submitKeys[s],
                        v,
                        new EntityId(t, t * 2, t * 3),
                        new RichInstant(v, s));
        for (int i = 0; i < s; i++) {
            topic.updateRunningHashAndSequenceNumber(
                    payer, "Hello world!".getBytes(), id, Instant.ofEpochSecond(v, i));
        }
        topic.setKey(EntityNum.fromInt(s));
        return topic;
    }
}
