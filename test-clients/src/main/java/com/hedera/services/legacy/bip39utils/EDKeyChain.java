package com.hedera.services.legacy.bip39utils;

/*-
 * ‌
 * Hedera Services Test Clients
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



public final class EDKeyChain implements KeyChain {
  private final ServicesSeed servicesSeed;

  
  public KeyPair keyAtIndex(int index) {
    byte[] var10000 = CryptoUtils.deriveKey(this.servicesSeed.getEntropy(), (long)index, 32);
 //   Intrinsics.checkExpressionValueIsNotNull(var10000, "CryptoUtils.deriveKey(hg…ropy, index.toLong(), 32)");
    byte[] edSeed = var10000;
    return (KeyPair)(new EDKeyPair(edSeed));
  }

  public EDKeyChain( ServicesSeed servicesSeed) {
  //  Intrinsics.checkParameterIsNotNull(hgcSeed, "hgcSeed");
    super();
    this.servicesSeed = servicesSeed;
  }
}


