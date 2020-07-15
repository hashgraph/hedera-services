package com.hedera.test.factories.keys;

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

import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.initialization.NodeAccountsCreation;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.legacy.config.PropertiesLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.Logger;

public class AccountCreation {
  protected static Logger log = Logger.getLogger(AccountCreation.class);

  public static void main(String args[]) throws Exception {
    String HederaStartupPath = PropertiesLoader.getHederaStartupPath();
    createStartupAccounts(3, 1, 1, HederaStartupPath);
  }

  @SuppressWarnings("unchecked")
  public static void createStartupAccounts(
          int noOfAccounts,
          int numKeysGenesis,
          int numKeysAccount,
          String path
  ) throws IOException {

    List<KeyPairObj> tempGenesisStore = new ArrayList<>();
    List<AccountKeyListObj> genKeyList = new ArrayList<>();

    List<KeyPairObj> tempAccountStore = new ArrayList<>();
    List<AccountKeyListObj> acctKeyList = new ArrayList<>();

    Map<String, List<AccountKeyListObj>> accountKeyPairHolder = new HashMap<>();
    long accountNum = 1;
    for (int i = 0; i < numKeysGenesis; i++) {
      KeyPair pair = new KeyPairGenerator().generateKeyPair();
      byte[] pubKey = pair.getPublic().getEncoded();
      String pubKeyStr = MiscUtils.commonsBytesToHex(pubKey);
      PrivateKey priv = pair.getPrivate();
      String privkey = MiscUtils.commonsBytesToHex(priv.getEncoded());
      KeyPairObj keyPair = new KeyPairObj(pubKeyStr, privkey);
      tempGenesisStore.add(keyPair);
    }
    AccountID accountID = AccountID.newBuilder()
            .setAccountNum(accountNum)
            .setRealmNum(0)
            .setShardNum(0).build();
    AccountKeyListObj genKeyListObj = new AccountKeyListObj(accountID, tempGenesisStore);

    genKeyList.add(genKeyListObj);
    accountKeyPairHolder.put(ApplicationConstants.GENESIS_ACCOUNT, genKeyList);
    accountNum++;

    for (int i = 0; i < noOfAccounts; i++) {
      for (int j = 0; j < numKeysAccount; j++) {
        KeyPair pair = new KeyPairGenerator().generateKeyPair();
        byte[] pubKey = pair.getPublic().getEncoded();
        String pubKeyStr = MiscUtils.commonsBytesToHex(pubKey);
        PrivateKey priv = pair.getPrivate();
        String privkey = MiscUtils.commonsBytesToHex(priv.getEncoded());
        KeyPairObj keyPair = new KeyPairObj(pubKeyStr, privkey);
        tempAccountStore.add(keyPair);
      }
      accountNum++;
      accountID = AccountID.newBuilder()
              .setAccountNum(accountNum)
              .setRealmNum(0)
              .setShardNum(0)
              .build();
      AccountKeyListObj acctKeyListObj = new AccountKeyListObj(accountID, tempAccountStore);
      acctKeyList.add(acctKeyListObj);
    }

    accountKeyPairHolder.put(ApplicationConstants.INITIAL_ACCOUNTS, acctKeyList);

    byte[] accountKeyPairHolderBytes = convertToBytes(accountKeyPairHolder);
    String keyBase64Pub = Base64.getEncoder().encodeToString(accountKeyPairHolderBytes);
    NodeAccountsCreation.writeToFileUTF8(path, keyBase64Pub);
  }

  public static byte[] convertToBytes(Object object) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        }
    }
}
