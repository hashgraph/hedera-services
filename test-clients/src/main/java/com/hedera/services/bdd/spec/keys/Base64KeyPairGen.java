package com.hedera.services.bdd.spec.keys;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.common.io.ByteSink;
import com.google.common.io.Files;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.proto.utils.CommonUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

/**
 * This class is used to generate a base64 encoded Public key (enc hex) - Private key (enc hex) pair.
 *
 * You can get these hex key pairs form .pem file and its passphrase by using tolls-cli
 *
 * Once the NewStartUpAccount.txt is generated, use the contents of this file and replace
 * the contents in StartUpAccount.txt to ensure that you dont get invalid signature for the appropriate account used.
 */
public class Base64KeyPairGen {

    public static void main(String... args) throws IOException{
        String privateKeyHex = "302e020100300506032b6570042204204cc2d50f9449affc43e851b8d4338f98c50ff5c56cb3c741f3f9b3d075a709fd";
        String publicKeyHex = "302a300506032b657003210034171a7d250b6a41cc2355eeb3e36919bace1f848b14341daa4ab4b67c337a00";
        String account = "0.0.950";

        KeyPairObj tempKeyPair = new KeyPairObj(publicKeyHex, privateKeyHex);
        AccountKeyListObj tempAccountKeyListObj = new AccountKeyListObj(HapiPropertySource.asAccount(account), List.of(tempKeyPair));
        Map<String, List<AccountKeyListObj>> tempMap = Map.of("START_ACCOUNT", List.of(tempAccountKeyListObj));

        ByteArrayOutputStream tempByteAS = new ByteArrayOutputStream();
        ObjectOutputStream tempObjS = new ObjectOutputStream(tempByteAS);
        tempObjS.writeObject(tempMap);
        tempObjS.close();

        ByteSink byteSink = Files.asByteSink(new File("NewStartUpAccount.txt"));
        byteSink.write(CommonUtils.base64encode(tempByteAS.toByteArray()).getBytes());
    }

}
