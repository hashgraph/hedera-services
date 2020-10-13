package com.hedera.services.legacy.unit.serialization;

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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;

/**
 * Unit tests for support of Contract ID and RSA_3072Key in JKey.
 * 
 * @author Hua Li
 * Created on 2019-01-15
 */
public class JKeyAdditionalTypeSupportTest {
  @Test
  public void serializingJContractIDKeyTest() throws Exception {
    // create a contactID
    ContractID cid =
        ContractID.newBuilder().setShardNum(0).setRealmNum(0).setContractNum(1001).build();
    // convert to JContractIDKey
    Key key = Key.newBuilder().setContractID(cid).build();
    JKey ckey = JKey.mapKey(key);
    // serialize and then deserialize
    byte[] ser = JKeySerializer.serialize(ckey);
    ByteArrayInputStream in = null;
    DataInputStream dis = null;
    JKey jkeyReborn;
    in = new ByteArrayInputStream(ser);
    dis = new DataInputStream(in);

    jkeyReborn = JKeySerializer.deserialize(dis);
    Key key1 = JKey.mapJKey(jkeyReborn);

    // make sure jkey bytes the same
    byte[] ser1 = JKeySerializer.serialize(jkeyReborn);
    Assert.assertArrayEquals(ser, ser1);

    // make sure contract id the same
    Assert.assertArrayEquals(key.getContractID().toByteArray(), key1.getContractID().toByteArray());

  }

  @Test
  public void serializingJRSA_3072KeyTest() throws Exception {
    // convert to JContractIDKey
    byte[] keyBytes = new byte[3072 / 8];
    (new Random()).nextBytes(keyBytes);
    Key key = Key.newBuilder().setRSA3072(ByteString.copyFrom(keyBytes)).build();
    JKey ckey = JKey.mapKey(key);
    // serialize and then deserialize
    byte[] ser = JKeySerializer.serialize(ckey);
    ByteArrayInputStream in = null;
    DataInputStream dis = null;
    JKey jkeyReborn;
    in = new ByteArrayInputStream(ser);
    dis = new DataInputStream(in);

    jkeyReborn = JKeySerializer.deserialize(dis);
    Key key1 = JKey.mapJKey(jkeyReborn);

    // make sure jkey bytes the same
    byte[] ser1 = JKeySerializer.serialize(jkeyReborn);
    Assert.assertArrayEquals(ser, ser1);

    // make sure contract id the same
    Assert.assertArrayEquals(key.getRSA3072().toByteArray(), key1.getRSA3072().toByteArray());
  }
}
