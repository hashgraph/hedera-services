package com.hedera.services.legacy.client.util;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hedera.services.legacy.regression.BaseClient;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.operator.OutputEncryptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.security.*;
import java.util.ArrayList;
import java.util.List;

public class CreateTestAccount extends BaseClient {

    protected static final Logger log = LogManager.getLogger(CreateTestAccount.class);
    private static String testConfigFilePath = "config/umbrellaTest.properties";
    public static List<ManagedChannel> channelList = new ArrayList<>();
    public static String host="localhost";
    public static int port= 50211;
    public static long ACCOUNT_FOR_TEST_INIT_BALANCE=100000000000000L;
    private static final String ACCOUNT_FOR_TESTS_PEM = "AccountForTestsPEM.pem";
    public CreateTestAccount(String testConfigFilePath) {
        super(testConfigFilePath);
    }


    public static void main(String[] args) throws Throwable {
        CreateTestAccount tester = new CreateTestAccount(testConfigFilePath);
        tester.init(args);
        log.info("Started ");
        CryptoServiceGrpc.CryptoServiceBlockingStub stub = createCryptoServiceStub_local(host, port);
        AccountID nodeAccount =RequestBuilder
                .getAccountIdBuild(3L, 0l, 0l);
        TransactionResponse response = createAccount(stub, nodeAccount);
        log.info("response "+response);
    }


    public static TransactionResponse createAccount(CryptoServiceGrpc.CryptoServiceBlockingStub stub, AccountID nodeID) throws Exception{

        KeyPair pair = new KeyPairGenerator().generateKeyPair();
        byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
        byte[] prvKey = ((EdDSAPrivateKey) pair.getPrivate()).getH();
        String pubKeyHex = HexUtils.bytes2Hex(pubKey);
        String pprvKeyHex = HexUtils.bytes2Hex(prvKey);
        Key akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
        AccountID payerID = genesisAccountList.get(0).getAccountId();
        KeyPairObj genesisKeyPair = genesisAccountList.get(0).getKeyPairList().get(0);
        String payerPubKeyHex = genesisKeyPair.getPublicKeyAbyteStr();
        Key key = KeyExpansion.genEd25519Key(genesisKeyPair.getPublicKey());
        List<Key> keyList = new ArrayList<>();
        keyList.add(key);
        acc2ComplexKeyMap.put(payerID, key);
        pubKey2privKeyMap.put(payerPubKeyHex, genesisKeyPair.getPrivateKey());

        Transaction createAccountRequest = TestHelperComplex
                .createAccount(payerID, key, nodeID, akey, ACCOUNT_FOR_TEST_INIT_BALANCE, 10000000,
                        false, 1, 90*24*60*60);
        TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
        TransactionResponse response = stub.createAccount(createAccountRequest);
        log.info("response = "+response);
        Thread.sleep(500);
        TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), stub);
        AccountID newAccountID = txReceipt1.getAccountID();
        log.info("Account Num: "+newAccountID.getAccountNum()+" : Public Key Hex: "+pubKeyHex);
        log.info("Account Num: "+newAccountID.getAccountNum()+" :Private Key Hex: "+pprvKeyHex);
        String pathPrefix = System.getProperty("user.dir")+"\\src\\main\\resource\\";
        log.info("Keys Generated for Account pathPrefix = "+pathPrefix);
        CommonUtils.writeToFile(pathPrefix+newAccountID.getAccountNum()+"_publicKey.pub", pubKey);
        CommonUtils.writeToFile(pathPrefix+newAccountID.getAccountNum()+"_privateKey.prv", prvKey);
        List<KeyPair> keyPairList = new ArrayList<>();
        keyPairList.add(pair);
        writePEMKey(keyPairList, new File(pathPrefix+ACCOUNT_FOR_TESTS_PEM), "password".toCharArray());
        return response;
    }


    public static void writePEMKey(List<KeyPair> keyPairList, File file, char[] password) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        SecureRandom random = SecureRandom.getInstance("DRBG",
                DrbgParameters.instantiation(256, DrbgParameters.Capability.RESEED_ONLY, null));
        OutputEncryptor encryptor = (new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC))
                .setPRF(PKCS8Generator.PRF_HMACSHA384)
                .setIterationCount(10000)
                .setRandom(random)
                .setPasssword(password)
                .setProvider(new BouncyCastleProvider())
                .build();

        JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(fos));
        for (KeyPair keyPair : keyPairList) {
            pemWriter.writeObject(new JcaPKCS8Generator(keyPair.getPrivate(), encryptor).generate());
        }
        pemWriter.flush();
        fos.close();
    }

    public static CryptoServiceGrpc.CryptoServiceBlockingStub createCryptoServiceStub_local(String host, int port)  throws Exception {
        return CryptoServiceGrpc.newBlockingStub(createChannel_local(host, port));
    }
    public static SmartContractServiceGrpc.SmartContractServiceBlockingStub createSmartContractStub_local(String host, int port)  throws Exception {
        ManagedChannel channel= createChannel_local(host, port);
        channelList.add(channel);
        return SmartContractServiceGrpc.newBlockingStub(channel);
    }

    public static ManagedChannel createChannel_local(String host, int port) throws Exception {
        return NettyChannelBuilder.forAddress(host, port)
                .negotiationType(NegotiationType.PLAINTEXT)
                .directExecutor()
                .enableRetry()
                .build();
    }
}
