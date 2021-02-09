package com.hedera.services.legacy.unit.serialization;

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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JFileInfoSerializer;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;

/**
 * Unit tests for JFileInfoSerializer class.
 * 
 * @author Hua Li
 * Created on 2019-01-15
 */
public class JFileInfoSerializerTest {
  @Test
  public void serDeserTest() throws Exception {
    FileID fid = FileID.newBuilder().setFileNum(1001).build();
    long size = 1024;
    Timestamp exp = Timestamp.newBuilder().setSeconds(System.currentTimeMillis()/1000).build();
    boolean deleted = false;
    Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    KeyList keyList = KeyExpansion.genKeyListInstance(5, pubKey2privKeyMap).getKeyList();
    
    // create file info obj
    FileInfo fi = FileInfo.newBuilder().setFileID(fid).setSize(size)
        .setExpirationTime(exp).setDeleted(deleted)
        .setKeys(keyList).build();
    
    JFileInfo jfi = JFileInfo.convert(fi);
    
    // ser, then deser, then ser again
    byte[] ser = jfi.serialize();
    
    JFileInfo jfi_reborn = JFileInfoSerializer.deserialize(new DataInputStream(new ByteArrayInputStream(ser)));
    FileInfo fi_reborn = jfi_reborn.convert(fid, size);
    
    byte[] ser1 = jfi_reborn.serialize();
    
    Assert.assertArrayEquals(ser, ser1);
    Assert.assertArrayEquals(fi.toByteArray(), fi_reborn.toByteArray());
  }
}
