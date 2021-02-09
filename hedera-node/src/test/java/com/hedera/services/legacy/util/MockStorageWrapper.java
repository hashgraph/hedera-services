package com.hedera.services.legacy.util;

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

import com.hedera.services.legacy.unit.FCStorageWrapper;
import java.util.HashMap;
import java.util.Map;

public class MockStorageWrapper extends FCStorageWrapper {

  byte[] rootChunkHash = {(byte) 0xe0, 0x4f, (byte) 0xd0,
      0x20, (byte) 0xea, 0x3a, 0x69, 0x10, (byte) 0xa2, (byte) 0xd8, 0x08, 0x00, 0x2b,
      0x30, 0x30, (byte) 0x9d};


  private Map<String, byte[]> filesMap;

  public MockStorageWrapper() {
    // super();
    filesMap = new HashMap<String, byte[]>();

  }

  //Slurp & Dump are deprecated
  @Override
  public byte[] fileRead(String path) {
    return filesMap.get(path);
  }

  @Override
  public void fileCreate(String path, byte[] content, long createTimeSec,
      int createTimeNs, long expireTimeSec, byte[] metadata) {
    filesMap.put(path, content);
  }


  @Override
  public boolean fileExists(String fsPath) {
    return filesMap.containsKey(fsPath);
  }

  @Override
  public void delete(String arg0, long arg1, int arg2) {
    if (filesMap.containsKey(arg0)) {
      filesMap.remove(arg0);
    }
  }
}
