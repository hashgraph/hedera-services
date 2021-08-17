package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.services.utils.EntityIdUtils.contractParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.CommonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerkleEntityIdUtilsTest {
  @Test
  void correctLiteral() {
    // expect:
    assertEquals("1.2.3", asLiteralString(asAccount("1.2.3")));
    assertEquals("11.22.33", asLiteralString(IdUtils.asFile("11.22.33")));
  }

  @Test
  void serializesExpectedSolidityAddress() {
    // given:
    byte[] shardBytes = {
      (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xAB,
    };
    int shard = Ints.fromByteArray(shardBytes);
    byte[] realmBytes = {
      (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xCD,
      (byte) 0xFE, (byte) 0x00, (byte) 0x00, (byte) 0xFE,
    };
    long realm = Longs.fromByteArray(realmBytes);
    byte[] numBytes = {
      (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xDE,
      (byte) 0xBA, (byte) 0x00, (byte) 0x00, (byte) 0xBA
    };
    long num = Longs.fromByteArray(numBytes);
    byte[] expected = {
      (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xAB,
      (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xCD,
      (byte) 0xFE, (byte) 0x00, (byte) 0x00, (byte) 0xFE,
      (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xDE,
      (byte) 0xBA, (byte) 0x00, (byte) 0x00, (byte) 0xBA
    };
    // and:
    AccountID equivAccount = asAccount(String.format("%d.%d.%d", shard, realm, num));
    ContractID equivContract = asContract(String.format("%d.%d.%d", shard, realm, num));

    // when:
    byte[] actual = asSolidityAddress(shard, realm, num);
    byte[] anotherActual = asSolidityAddress(equivContract);
    // and:
    String actualHex = asSolidityAddressHex(equivAccount);

    // then:
    assertArrayEquals(expected, actual);
    assertArrayEquals(expected, anotherActual);
    // and:
    assertEquals(CommonUtils.hex(expected), actualHex);
    // and:
    assertEquals(equivAccount, accountParsedFromSolidityAddress(actual));
    // and:
    assertEquals(equivContract, contractParsedFromSolidityAddress(actual));
  }

  @ParameterizedTest
  @CsvSource({
    "0,Cannot parse '0' due to only 0 dots",
    "0.a.0,Argument 'literal=0.a.0' is not an account",
    "...,Argument 'literal=...' is not an account",
    "1.2.3.4,Argument 'literal=1.2.3.4' is not an account",
    "1.2.three,Argument 'literal=1.2.three' is not an account",
    "1.2.333333333333333333333,Cannot parse '1.2.333333333333333333333' due to overflow"
  })
  void rejectsInvalidAccountLiterals(String badLiteral, String desiredMsg) {
    // expect:
    final var e = assertThrows(IllegalArgumentException.class, () -> parseAccount(badLiteral));
    assertEquals(desiredMsg, e.getMessage());
  }

  @ParameterizedTest
  @CsvSource({"1.0.0", "0.1.0", "0.0.1", "1.2.3"})
  void parsesValidLiteral(String goodLiteral) {
    // expect:
    assertEquals(asAccount(goodLiteral), parseAccount(goodLiteral));
  }

  @Test
  void prettyPrintsScheduleIds() {
    // given:
    ScheduleID id = ScheduleID.newBuilder().setShardNum(1).setRealmNum(2).setScheduleNum(3).build();

    // expect:
    assertEquals("1.2.3", EntityIdUtils.readableId(id));
  }

  @Test
  void prettyPrintsTokenIds() {
    // given:
    TokenID id = TokenID.newBuilder().setShardNum(1).setRealmNum(2).setTokenNum(3).build();

    // expect:
    assertEquals("1.2.3", EntityIdUtils.readableId(id));
  }

  @Test
  void prettyPrintsTopicIds() {
    // given:
    TopicID id = TopicID.newBuilder().setShardNum(1).setRealmNum(2).setTopicNum(3).build();

    // expect:
    assertEquals("1.2.3", EntityIdUtils.readableId(id));
  }

  @Test
  void prettyPrintsAccountIds() {
    // given:
    AccountID id = AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

    // expect:
    assertEquals("1.2.3", EntityIdUtils.readableId(id));
  }

  @Test
  void prettyPrintsFileIds() {
    // given:
    FileID id = FileID.newBuilder().setShardNum(1).setRealmNum(2).setFileNum(3).build();

    // expect:
    assertEquals("1.2.3", EntityIdUtils.readableId(id));
  }

  @Test
  void givesUpOnNonAccountIds() {
    // given:
    String id = "my-account";

    // expect:
    assertEquals(id, EntityIdUtils.readableId(id));
  }

  @Test
  void asContractWorks() {
    // setup:
    ContractID expected =
        ContractID.newBuilder().setShardNum(1).setRealmNum(2).setContractNum(3).build();

    // given:
    AccountID id = AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

    // when:
    ContractID cid = EntityIdUtils.asContract(id);

    // then:
    assertEquals(expected, cid);
  }

  @Test
  void asFileWorks() {
    // setup:
    FileID expected = FileID.newBuilder().setShardNum(1).setRealmNum(2).setFileNum(3).build();

    // given:
    AccountID id = AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

    // when:
    FileID fid = EntityIdUtils.asFile(id);

    // then:
    assertEquals(expected, fid);
  }
}
