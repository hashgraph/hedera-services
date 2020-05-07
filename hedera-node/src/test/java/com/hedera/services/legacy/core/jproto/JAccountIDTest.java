package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.TopicID;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
public class JAccountIDTest {
  @Test
  public void convertTopicId() {
    final var shard = 11L;
    final var realm = 222L;
    final var num = 3333L;
    final var topicId = TopicID.newBuilder().setShardNum(shard).setRealmNum(realm).setTopicNum(num).build();
    final var cut = JAccountID.convert(topicId);

    assertAll(
            () -> assertEquals(shard, cut.getShardNum()),
            () -> assertEquals(realm, cut.getRealmNum()),
            () -> assertEquals(num, cut.getAccountNum())
    );
  }

  @Test
  public void convertTopicIdNull() {
    assertNull(JAccountID.convert((TopicID)null));
  }
}
