package com.hedera.services.legacy.core.jproto;

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

import java.util.HashMap;

/**
 *  Mapping of Class name and Object Id
 *
 * @author Akshay
 */
public enum JObjectType {

  JKey, JKeyList, JThresholdKey, JEd25519Key, JECDSA_384Key, JRSA_3072Key, JContractIDKey,
  JFileInfo,
  JMemoAdminKey;

  private static final HashMap<JObjectType, Long> LOOKUP_TABLE = new HashMap<>();
  private static final HashMap<Long, JObjectType> REV_LOOKUP_TABLE = new HashMap<>();

  static {
    addLookup(JKey, 15503731);
    addLookup(JKeyList, 15512048);
    addLookup(JThresholdKey, 15520365);
    addLookup(JEd25519Key, 15528682);
    addLookup(JECDSA_384Key, 15536999);
    addLookup(JRSA_3072Key, 15620169);
    addLookup(JContractIDKey, 15545316);
    addLookup(JFileInfo, 15636803);
    addLookup(JMemoAdminKey, 15661754);
  }

  private static void addLookup(final JObjectType type, final long value) {
    LOOKUP_TABLE.put(type, value);
    REV_LOOKUP_TABLE.put(value, type);
  }

  public static JObjectType valueOf(final long value) {
    return REV_LOOKUP_TABLE.get(value);
  }

  public long longValue() {
    return LOOKUP_TABLE.get(this);
  }
}
