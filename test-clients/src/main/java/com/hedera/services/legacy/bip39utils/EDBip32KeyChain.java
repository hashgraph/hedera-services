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

import com.hedera.services.legacy.bip39utils.bip39.Mnemonic;

import java.util.List;

public final class EDBip32KeyChain implements KeyChain {
  private final ServicesSeed servicesSeed;

  public KeyPair keyAtIndex(int index) {
    List var10000 = this.servicesSeed.toWordsList();
    KeyPair thisPair = null;
    try {
      String words = servicesSeed.toWordsList().toString();
      byte[] seed = Mnemonic.generateSeed(words, "");
      byte[] ckd = SLIP10.deriveEd25519PrivateKey(seed, new int[]{44, 3030, 0, 0, index});
      thisPair = (KeyPair)(new EDKeyPair(ckd));
    }catch (Exception ex){
      ex.printStackTrace();
    }

    return thisPair;
  }

  public EDBip32KeyChain( ServicesSeed servicesSeed) {
    super();
    this.servicesSeed = servicesSeed;
  }
}
