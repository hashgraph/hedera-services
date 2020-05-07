package com.hedera.services.legacy.client.util;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.util.Arrays;

import java.io.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetupAccount {
    private static final Logger log = LogManager.getLogger(SetupAccount.class);
    private static final String GEN_PUB_KEY_PATH = "TestPubKey.txt";
    private static final String GEN_PRIV_KEY_PATH = "TestPrivKey.txt";
    private static final String ACCOUNT_FOR_TESTS = "AccountForTestsStartUp.txt";
    public static void main(String args[]) throws Exception {
        if(args.length < 2) {
            log.info("Usage: SetupAccount <ACCOUNT_NUM> <PEM_KEY_FILE_PATH>");
            return;
        }
        //createStartupAccountFile(args[1], Long.parseLong(args[0]));
        long accountNum = 8013L;
        String pubKey ="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        String privKey = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        createStartupAccountTemp(accountNum, pubKey, privKey);
    }


    public static void createStartupAccountTemp(long accountNum, String pubKeyStr, String privkeyStr) throws Exception {
        log.info("Creation of Public and Private Key of Startup Account started");
        List<KeyPairObj> tempGenesisStore = new ArrayList<>();
        List<AccountKeyListObj> genKeyList = new ArrayList<>();
        Map<String, List<AccountKeyListObj>> accountKeyPairHolder = new HashMap<>();
        KeyPairObj keyPair = new KeyPairObj(pubKeyStr, privkeyStr);
        tempGenesisStore.add(keyPair);
        AccountID accountID =
                AccountID.newBuilder().setAccountNum(accountNum).setRealmNum(0).setShardNum(0).build();
        AccountKeyListObj genKeyListObj = new AccountKeyListObj(accountID, tempGenesisStore);

        genKeyList.add(genKeyListObj);
        accountKeyPairHolder.put("START_ACCOUNT", genKeyList);
        // Now Serialize this Map and Store in File for future usage
        byte[] accountKeyPairHolderBytes = CommonUtils.convertToBytes(accountKeyPairHolder);
        String keyBase64Pub = CommonUtils.base64encode(accountKeyPairHolderBytes);
        log.info("Public and Private Key of Startup account completed.. going to store in file");
        String pathPrefix = System.getProperty("user.dir")+"\\src\\main\\resource\\";
        // Store in file
        CommonUtils.writeToFileUTF8(pathPrefix+ACCOUNT_FOR_TESTS, keyBase64Pub);

        log.info("Key Pair of startup account stored in file");
    }

    public static void createStartupAccountFile(String path, long accountNum) throws Exception {
        log.info("Creation of Public and Private Key of Startup Account started");
        List<AccountKeyListObj> genKeyList = new ArrayList<>();
        Map<String, List<AccountKeyListObj>> accountKeyPairHolder = new HashMap<>();
        String pubKeyStr = "";
        String privkeyStr = "";
        AccountKeyListObj genKeyListObj = getAccountKeyListObj(path, accountNum);

        genKeyList.add(genKeyListObj);
        accountKeyPairHolder.put("START_ACCOUNT", genKeyList);
        // Now Serialize this Map and Store in File for future usage
        byte[] accountKeyPairHolderBytes = CommonUtils.convertToBytes(accountKeyPairHolder);
        String keyBase64Pub = CommonUtils.base64encode(accountKeyPairHolderBytes);
        log.info("Public and Private Key of Startup account completed.. going to store in file");
        String pathPrefix = System.getProperty("user.dir")+"\\src\\main\\resource\\";
        // Store in file
        CommonUtils.writeToFileUTF8(pathPrefix+ACCOUNT_FOR_TESTS, keyBase64Pub);
        // Store Account Public and Private Key in File
        CommonUtils.writeToFileUTF8(pathPrefix+GEN_PUB_KEY_PATH, pubKeyStr);
        CommonUtils.writeToFileUTF8(pathPrefix+GEN_PRIV_KEY_PATH, privkeyStr);
        log.info("Key Pair of startup account stored in file");
    }

    public static AccountKeyListObj getAccountKeyListObj(String path, long accountNum) throws Exception {
        System.out.println("Is PassPhrase Required for Key File: ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String pCheck = reader.readLine();
        char[] password = null;
        if(pCheck.equalsIgnoreCase("Y") || pCheck.equalsIgnoreCase("YES") ) {
            password = getPassword("Passphrase");
        }
        List<KeyPairObj> tempGenesisStore = loadPEMKey(path, password);

        AccountID accountID =
                AccountID.newBuilder().setAccountNum(accountNum).setRealmNum(0).setShardNum(0).build();
        return new AccountKeyListObj(accountID, tempGenesisStore);
    }

    public static KeyPairObj getKeyPair(Object object, char[] password) throws Exception {
        KeyPair keyPair = null;
        JcaPEMKeyConverter converter= new JcaPEMKeyConverter().setProvider(new EdDSASecurityProvider());
        if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
            PKCS8EncryptedPrivateKeyInfo ekpi = (PKCS8EncryptedPrivateKeyInfo) object;
            InputDecryptorProvider decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder()
                    .setProvider(new BouncyCastleProvider()) //new BouncyCastleProvider() is working
                    .build(password);
            PrivateKeyInfo pki = ekpi.decryptPrivateKeyInfo(decryptor);
            EdDSAPrivateKey sk = (EdDSAPrivateKey) converter.getPrivateKey(pki);
            EdDSAPublicKey pk = new EdDSAPublicKey(
                    new EdDSAPublicKeySpec(sk.getA(), EdDSANamedCurveTable.ED_25519_CURVE_SPEC));
            keyPair = new KeyPair(pk, sk);
        } else if(object instanceof PEMKeyPair){
            PEMKeyPair ukp = (PEMKeyPair) object;
            keyPair = converter.getKeyPair(ukp);
        } else {
            log.info("KeyPair Type: "+object.getClass().getName());
        }
        byte[] pubKey = ((EdDSAPublicKey) keyPair.getPublic()).getEncoded();
        PrivateKey priv = keyPair.getPrivate();
        KeyPairObj keyPairObj = new KeyPairObj(Common.bytes2Hex(pubKey), Common.bytes2Hex(priv.getEncoded()));
        return keyPairObj;
    }

    public static KeyPairObj getKeyPair(Object object) throws Exception {
        KeyPair keyPair = null;
        JcaPEMKeyConverter converter= new JcaPEMKeyConverter().setProvider(new EdDSASecurityProvider());
        if(object instanceof PEMKeyPair){
            PEMKeyPair ukp = (PEMKeyPair) object;
            keyPair = converter.getKeyPair(ukp);
        } else {
            String errorMsg = "Invalid KeyPair Type: "+object.getClass().getName();
            throw new Exception(errorMsg);
        }
        byte[] pubKey = ((EdDSAPublicKey) keyPair.getPublic()).getEncoded();
        PrivateKey priv = keyPair.getPrivate();
        KeyPairObj keyPairObj = new KeyPairObj(Common.bytes2Hex(pubKey), Common.bytes2Hex(priv.getEncoded()));
        return keyPairObj;
    }
    public static List<KeyPairObj> loadPEMKey(String filePath, char[] password) throws Exception {
        File file = new File(filePath);
        List<KeyPairObj> keyPairList = new ArrayList<>();

        FileInputStream fis = new FileInputStream(file);
        InputStreamReader isr = new InputStreamReader(fis);
        PEMParser parser = new PEMParser(isr);
        Object rawObject;
        while ((rawObject = parser.readObject()) != null) {
            if(password!=null && password.length>0) {
                keyPairList.add(getKeyPair(rawObject, password));
            } else {
                keyPairList.add(getKeyPair(rawObject));
            }
        }
        isr.close();
        fis.close();
        return keyPairList;
    }

    /**
     *  Method to read password from Console and confirm the password
     * @return password
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public static char[] getPassword(String passwordPrompt) throws IOException, IllegalArgumentException {
        for(int i=0;i<3;i++) {
            final char[] password;
            final char[] confirmedPassword;
            if (System.console() != null) {
                log.info(passwordPrompt);
                password = System.console().readPassword(passwordPrompt);
                log.info("confirm "+passwordPrompt);
                confirmedPassword = System.console().readPassword("Confirm "+passwordPrompt);
            } else {
                log.info("Console is not available using System in: ");
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        System.in));
                log.info(passwordPrompt);
                password = reader.readLine().toCharArray();
                log.info("Confirm "+passwordPrompt);
                confirmedPassword = reader.readLine().toCharArray();
            }

            if (Arrays.areEqual(password, confirmedPassword)) {
                return password;
            }
            log.error("Password and confirm password is not matched, try again");
        }
        throw new IllegalArgumentException("Entered passwords are not maching, please try again");
    }

}
