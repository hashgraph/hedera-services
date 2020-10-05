package com.hedera.services.legacy.regression;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.client.util.Common;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.client.test.ClientBaseThread;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Expired Transaction Record Test
 * This will test transaction for cyrpto transfer and wait for the record to be expired and validate the response
 *
 */
public class ExpiredTxRecordTests extends BaseClient {

    protected static final Logger log = LogManager.getLogger(ExpiredTxRecordTests.class);

    public static List<ManagedChannel> channelList = new ArrayList<>();
    public static Map<AccountID, String> accountPubKeyHexMap = new HashMap<>();
    public static Map<AccountID, String> accountPrvKeyHexMap = new HashMap<>();
    public static Map<AccountID, PublicKey> accountPubKeyMap = new HashMap<>();
    public static Map<AccountID, PrivateKey> accountPrvKeyMap = new HashMap<>();


    public ExpiredTxRecordTests(String testConfigFilePath) {
        super(testConfigFilePath);
    }


    public static void main(String[] args) throws Throwable {
        ExpiredTxRecordTests tester = new ExpiredTxRecordTests(testConfigFilePath);
        tester.readGenesisInfo();
        tester.readAppConfig();
        CryptoServiceGrpc.CryptoServiceBlockingStub stub = createCryptoServiceStub_local(host, port);
        AccountID payerAccount =RequestBuilder
                .getAccountIdBuild(2L, 0l, 0l);
        AccountID nodeAccount =RequestBuilder
                .getAccountIdBuild(3L, 0l, 0l);

        AccountID accountA = createAccountID(stub, payerAccount, nodeAccount, 1000000);
        AccountID accountB = createAccountID(stub, payerAccount, nodeAccount, 1000000);
        long account_A_Balance_step_1 = getBalance(stub, accountA, payerAccount, tester.genesisPrivateKey, nodeAccount);
        long account_B_Balance_step_1 = getBalance(stub, accountB, payerAccount, tester.genesisPrivateKey, nodeAccount);
        long node_Balance_step_1 = getBalance(stub, nodeAccount, payerAccount, tester.genesisPrivateKey, nodeAccount);
        log.info("account_A_Balance_step_1 "+account_A_Balance_step_1);
        log.info("account_B_Balance_step_1 "+account_B_Balance_step_1);
        log.info("node_Balance_step_1 "+node_Balance_step_1);
        Key key = KeyExpansion.genEd25519Key(accountPubKeyMap.get(accountA));
        List<Key> keyList = new ArrayList<>();
        keyList.add(key);
        pubKey2privKeyMap.put(accountPubKeyHexMap.get(accountA), accountPrvKeyMap.get(accountA));
        Transaction transferTransaction = createTransferWithKeyList(accountA, accountB,
                accountA, keyList, pubKey2privKeyMap,
                nodeAccount, 1000L, 1L);
        TransactionResponse transferResponse = stub.cryptoTransfer(transferTransaction);
        log.info("transferResponse " + transferResponse);
        Thread.sleep(1000);
        long account_A_Balance_step_2 = getBalance(stub, accountA, payerAccount, tester.genesisPrivateKey, nodeAccount);
        long account_B_Balance_step_2 = getBalance(stub, accountB, payerAccount, tester.genesisPrivateKey, nodeAccount);
        long node_Balance_step_2 = getBalance(stub, accountB, payerAccount, tester.genesisPrivateKey, nodeAccount);
        log.info("account_A_Balance_step_2 "+account_A_Balance_step_2);
        log.info("account_B_Balance_step_2 "+account_B_Balance_step_2);
        log.info("node_Balance_step_2 "+node_Balance_step_2);
        Assert.assertEquals(1000000, account_A_Balance_step_2);
        List<Key> payerKeyList = new ArrayList<>();
        payerKeyList.add(startUpKey);
        TransactionBody body = TransactionBody.parseFrom(transferTransaction.getBodyBytes());
        Transaction getRecordTransaction = createTransferWithKeyList(payerAccount, nodeAccount,
               payerAccount, payerKeyList, pubKey2privKeyMap,
                nodeAccount, 100000L, 30L);
        Query query = RequestBuilder.getTransactionGetRecordQuery(body.getTransactionID(), getRecordTransaction,
                ResponseType.ANSWER_ONLY);

        Response transactionRecord = stub.getTxRecordByTxID(query);
        TransactionRecord record = transactionRecord.getTransactionGetRecord().getTransactionRecord();
        log.info("transactionRecord : "+record);
        ResponseCodeEnum responseCodeEnum = record.getReceipt().getStatus();
        Assert.assertEquals(ResponseCodeEnum.TRANSACTION_EXPIRED, responseCodeEnum);
        if( responseCodeEnum ==  ResponseCodeEnum.TRANSACTION_EXPIRED) {
            Assert.assertFalse(listContainsAccountID(record.getTransferList().getAccountAmountsList(), accountA));
            Assert.assertTrue(listContainsAccountID(record.getTransferList().getAccountAmountsList(), nodeAccount));
        }

    }

    public static boolean listContainsAccountID(List<AccountAmount> accountAmounts, AccountID accountID) {
        for(AccountAmount accountAmount: accountAmounts) {
            if(accountID.equals(accountAmount.getAccountID()) ) {
                log.info("Transfer list contains Account ID");
                return true;
            }
        }
        return false;
    }

    /**
     * Creates crypto account and returns accountID
     * @param stub
     * @param payerID
     * @param nodeID
     * @param balance
     * @return accountID
     * @throws Exception
     */
    public static AccountID createAccountID(CryptoServiceGrpc.CryptoServiceBlockingStub stub, AccountID payerID, AccountID nodeID, long balance) throws Exception{

        KeyPair pair = new KeyPairGenerator().generateKeyPair();
        byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
        byte[] prvKey = ((EdDSAPrivateKey) pair.getPrivate()).getH();
        String pubKeyHex = Common.bytes2Hex(pubKey);
        String pprvKeyHex = Common.bytes2Hex(prvKey);
        Key akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();

        Transaction createAccountRequest = TestHelperComplex
                .createAccount(payerID, startUpKey, nodeID, akey, balance, 10000000,
                    false, 1, CustomPropertiesSingleton.getInstance().getAccountDuration());
        TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
        TransactionResponse response = stub.createAccount(createAccountRequest);
        log.info("response = "+response);
        Thread.sleep(500);
        TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), stub);
        AccountID newAccountID = txReceipt1.getAccountID();
        accountPubKeyHexMap.put(newAccountID, pubKeyHex);
        accountPrvKeyHexMap.put(newAccountID, pprvKeyHex);
        accountPubKeyMap.put(newAccountID, pair.getPublic());
        accountPrvKeyMap.put(newAccountID, pair.getPrivate());
        return newAccountID;
    }


    /**
     * get Crypto Account balance
     * @param stub
     * @param accountID
     * @param payerAccount
     * @param payerAccountKey
     * @param nodeAccount
     * @return balance
     * @throws Exception
     */
    public static long getBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
                                                   AccountID accountID, AccountID payerAccount,
                                                   PrivateKey payerAccountKey, AccountID nodeAccount) throws Exception {
        Response response = getCryptoGetAccountInfo(stub,
                accountID, payerAccount,
                payerAccountKey, nodeAccount);
       return response.getCryptoGetInfo().getAccountInfo().getBalance();
    }

    /**
     * Get Crypto Account Info
     * @param stub
     * @param accountID
     * @param payerAccount
     * @param payerAccountKey
     * @param nodeAccount
     * @return accountInfo
     * @throws Exception
     */
    public static Response getCryptoGetAccountInfo(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
                                                   AccountID accountID, AccountID payerAccount,
                                                   PrivateKey payerAccountKey, AccountID nodeAccount) throws Exception {

        long costForQuery = 1000000L;
        Response response = executeAccountInfoQuery(stub, accountID, payerAccount, payerAccountKey,
                    nodeAccount,
                costForQuery, ResponseType.ANSWER_ONLY);
        return response;
    }

    public static Response executeAccountInfoQuery(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
                                                   AccountID accountID, AccountID payerAccount, PrivateKey payerAccountKey,
                                                   AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
        Transaction transferTransaction = createTransfer(payerAccount, payerAccountKey, nodeAccount,
                payerAccount, payerAccountKey, nodeAccount, costForQuery);
        Query cryptoGetInfoQuery = RequestBuilder
                .getCryptoGetInfoQuery(accountID, transferTransaction, responseType);
        return stub.getAccountInfo(cryptoGetInfoQuery);
    }

    /**
     * Create Crypto Transfer request
     * @param fromAccount
     * @param toAccount
     * @param payerAccount
     * @param payerPublicKeyHex
     * @param payerPrivateKeyHex
     * @param nodeAccount
     * @param amount
     * @return
     * @throws Exception
     */
   public static Transaction createTransfer(AccountID fromAccount, AccountID toAccount,
                                             AccountID payerAccount, String payerPublicKeyHex, String payerPrivateKeyHex,
                                             AccountID nodeAccount, long amount) throws Exception {
        Timestamp timestamp = ExpiredTxRecordTests.getDefaultCurrentTimestampUTC();
        Duration transactionDuration = RequestBuilder.getDuration(30);

        long transferFee = 1000000;
        Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
                payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transferFee, timestamp,
                transactionDuration, false,
                "Test Transfer", fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
                amount);
        Transaction signedTx = ExpiredTxRecordTests.signTransactionWithHexKeys(transferTx, payerPublicKeyHex, payerPrivateKeyHex);
        return signedTx;
    }

    public static Transaction createTransferWithKeyList(AccountID fromAccount, AccountID toAccount,
                                             AccountID payerAccount, List<Key> keyList,
                                             Map<String, PrivateKey> pubKey2privKeyMap,
                                             AccountID nodeAccount, long amount, long txDuration) throws Exception {
        Timestamp timestamp = ExpiredTxRecordTests.getDefaultCurrentTimestampUTC();
        Duration transactionDuration = RequestBuilder.getDuration(txDuration);

        long transferFee = 1000000;
        Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
                payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transferFee, timestamp,
                transactionDuration, false,
                "Test Transfer", fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
                amount);
        Transaction signedTx = TransactionSigner.signTransactionComplexWithSigMap(transferTx, keyList,
                pubKey2privKeyMap);
        return signedTx;
    }

    public static Transaction signTransactionWithHexKeys(Transaction transaction, String publicKeyHex, String privateKeyHex) throws Exception {

        List<Key> keyList = new ArrayList<>();
        Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
        populateKeyListAndMap(publicKeyHex, privateKeyHex, keyList, pubKey2privKeyMap);
        Transaction signedTx = TransactionSigner.signTransactionComplexWithSigMap(transaction, keyList,
                pubKey2privKeyMap);
        return signedTx;
    }

    public static void populateKeyListAndMap(String publicKeyHex, String privateKeyHex,
                                             List<Key> keyList, Map<String, PrivateKey> pubKey2privKeyMap  ) throws Exception {
        PrivateKey payerPrivateKey = getPrivateKey(privateKeyHex);
        PublicKey payerPublicKey = getPublicKey(publicKeyHex);

        byte[] pubKey = ((EdDSAPublicKey) payerPublicKey).getAbyte();
        String tempPublicKeyHex = Common.bytes2Hex(pubKey);
        Key key = KeyExpansion.genEd25519Key(payerPublicKey);
        keyList.add(key);

        pubKey2privKeyMap.put(tempPublicKeyHex, payerPrivateKey);
    }

    private static PrivateKey getPrivateKey(String privateKey)  {
        PrivateKey ret = null;
        try {
            byte[] privArray = Hex.decodeHex(privateKey);
            PKCS8EncodedKeySpec encoded = new PKCS8EncodedKeySpec(privArray);
            ret = new EdDSAPrivateKey(encoded);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public static PublicKey getPublicKey(String publicKey) {
        EdDSAPublicKey ret = null;
        try {
            byte[] pubKeybytes = ClientBaseThread.hexToBytes(publicKey);
            X509EncodedKeySpec pencoded = new X509EncodedKeySpec(pubKeybytes);
            ret = new EdDSAPublicKey(pencoded);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }
    public static CryptoServiceGrpc.CryptoServiceBlockingStub createCryptoServiceStub_local(String host, int port)  throws Exception {
        return CryptoServiceGrpc.newBlockingStub(createChannel_local(host, port));
    }

    public static ManagedChannel createChannel_local(String host, int port) throws Exception {
        return NettyChannelBuilder.forAddress(host, port)
                .negotiationType(NegotiationType.PLAINTEXT)
                .directExecutor()
                .enableRetry()
                .build();
    }
}
