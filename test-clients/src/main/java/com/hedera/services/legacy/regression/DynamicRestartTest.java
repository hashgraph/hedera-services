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
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FreezeServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.client.test.ClientBaseThread;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hedera.services.legacy.regression.umbrella.TransactionIDCache;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.io.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Common methods for Dynamic restart tests.
 * This class provides common methods used in DynamicRestartTestBefore and DynamicRestartTestAfter
 * @author Tirupathi Mandala Created on 2019-08-06
 */
public class DynamicRestartTest extends TestHelperComplex {

    private static final Logger log = LogManager.getLogger(DynamicRestartTest.class);
    protected static ManagedChannel channel = null;

    protected static String CRYPTO_ACCOUNT_MAP_FILE = "crypto_account_map.txt";
    protected static String FILE_MAP_FILE = "file_map.txt";
    protected static String SMART_CONTRACT_MAP_FILE = "smart_contract_map.txt";
    protected static Map<AccountID, CryptoGetInfoResponse.AccountInfo> accountInfoMap = new HashMap<>();
    protected static Map<FileID, FileGetInfoResponse.FileInfo> fileInfoMap = new HashMap<>();
    protected static Map<ContractID, ContractGetInfoResponse.ContractInfo> contractInfoMap = new HashMap<>();
    protected static FreezeServiceGrpc.FreezeServiceBlockingStub stub = null;
    protected static CryptoServiceBlockingStub cstub = null;
    protected static FileServiceGrpc.FileServiceBlockingStub fstub = null;
    protected static SmartContractServiceGrpc.SmartContractServiceBlockingStub sstub;
    protected static List<AccountKeyListObj> genesisAccountList;
    protected static AccountID genesisAccountID;
    protected static PrivateKey genesisPrivateKey;
    protected static KeyPair genKeyPair;
    protected static String host = null;
    protected static int port = 50211;
    protected static long accountDuration;
    protected static long fileDuration;
    protected static long contractDuration;
    protected static AccountID account55;
    protected static int objectCount = 5;
    protected static AccountID nodeID = null;
    protected static TransactionIDCache cache = null;
    protected static Duration transactionDuration = Duration.newBuilder().setSeconds(TX_DURATION_SEC)
            .build();
    public DynamicRestartTest() {
    }

    /**
     * Method to setup Account and channel required for Dynamic restart
     * @throws Throwable
     */
    public void setUp() throws Throwable {
        Properties properties = TestHelper.getApplicationProperties();
        if (host == null) {
            this.host = properties.getProperty("host","localhost");
        }
        accountDuration = Long.parseLong(properties.getProperty("ACCOUNT_DURATION"));
        fileDuration = Long.parseLong(properties.getProperty("FILE_DURATION"));
        contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
        //   fit = new SmartContractServiceTest(testConfigFilePath);
        account55 = RequestBuilder.getAccountIdBuild(55l, 0l, 0l);
        TransactionSigner.SIGNATURE_FORMAT = "SignatureMap";
        nodeID = RequestBuilder.getAccountIdBuild(3l, 0l, 0l);
        readGenesisInfo();
        createStubs();
        TestHelper.initializeFeeClient(channel, genesisAccountID, genKeyPair, nodeID);

        cache = TransactionIDCache
                .getInstance(TransactionIDCache.txReceiptTTL, TransactionIDCache.txRecordTTL);
        // Transfer hbars from 2 to 55, because initially 55 doesn't have enough hbars to pay for Freeze Transaction
        Transaction transferTx = getSignedTransferTx(
                genesisAccountID, nodeID, genesisAccountID, account55, 300000, "Transfer from 2 to 55");
        TransactionID transferTxID = CommonUtils.extractTransactionBody(transferTx).getTransactionID();

        TransactionResponse response = cstub.cryptoTransfer(transferTx);
        Assert.assertNotNull(response);
        Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());

        System.out.println("Transferring hbars from 2 to 55. ");
        TransactionReceipt receipt = getTxReceipt(transferTxID);
        System.out.println("Transfer status: " + receipt.getStatus());
    }

    /**
     * Method creates all 3 Service Stubs ( Crypto, File and SmartContract)
     */
    private void createStubs() {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = FreezeServiceGrpc.newBlockingStub(channel);
        cstub = CryptoServiceGrpc.newBlockingStub(channel);
        fstub = FileServiceGrpc.newBlockingStub(channel);
        sstub = SmartContractServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Get the transaction receipt.
     *
     * @param txId ID of the tx
     * @return the transaction receipt
     */
    public static TransactionReceipt getTxReceipt(TransactionID txId) throws Throwable {
        Query query = Query.newBuilder()
                .setTransactionGetReceipt(
                        RequestBuilder.getTransactionGetReceiptQuery(txId, ResponseType.ANSWER_ONLY))
                .build();
        Response transactionReceipts = fetchReceipts(query, cstub);
        TransactionReceipt rv = transactionReceipts.getTransactionGetReceipt().getReceipt();
        return rv;
    }

    /**
     * Freeze the network for given duration
     * @param freezeDuration
     * @param freezeStartTimeOffset
     * @param freezeStartHour
     * @param freezeStartMin
     * @return responseCode
     * @throws Throwable
     */
    public static ResponseCodeEnum freeze( int freezeDuration, int freezeStartTimeOffset, int freezeStartHour, int freezeStartMin) throws Throwable {

        if (freezeStartTimeOffset > 0) {
            long freezeStartTimeMillis = System.currentTimeMillis() + 60000l * freezeStartTimeOffset;
            int[] startHourMin = Utilities.getUTCHourMinFromMillis(freezeStartTimeMillis);
            freezeStartHour = startHourMin[0];
            freezeStartMin = startHourMin[1];
        }

        Transaction transaction = createFreezeTransaction(freezeStartHour, freezeStartMin, freezeDuration);

        Transaction signedTx = getSignedFreezeTx(transaction);
        FreezeTransactionBody freezeBody =
                TransactionBody.parseFrom(signedTx.getBodyBytes()).getFreeze();
        String freezeBodyToPrint = "\n-----------------------------------" +
                "\nfreeze: FreezeTransactionBody = " +
                "\nstartHour: " + freezeBody.getStartHour() +
                "\nstartMin: " + freezeBody.getStartMin() +
                "\nendHour: " + freezeBody.getEndHour() +
                "\nendMin: " + freezeBody.getEndMin() + "\n";
        log.info(freezeBodyToPrint);
        TransactionResponse response = stub.freeze(signedTx);

        Assert.assertNotNull(response);
        Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
        log.info(
                "Pre Check Response Freeze :: " + response.getNodeTransactionPrecheckCode().name());

        TransactionBody body = TransactionBody.parseFrom(signedTx.getBodyBytes());
        TransactionID transactionID = body.getTransactionID();
        cache.addTransactionID(transactionID);

        // get tx receipt of payer account by txId
        log.info("Get Tx receipt by Tx Id...");
        TransactionReceipt txReceipt = getTxReceipt(transactionID);
        return txReceipt.getStatus();
    }


    /**
     * Fetches the receipts, wait if necessary.
     */
    private static Response fetchReceipts(Query query, CryptoServiceBlockingStub cstub2)
            throws Exception {
        return TestHelperComplex.fetchReceipts(query, cstub2, log, host);
    }

    /**
     * Creates a signed freeze tx.
     */
    private static Transaction getSignedFreezeTx(Transaction unSignedTransferTx) throws Exception {
        TransactionBody txBody = CommonUtils.extractTransactionBody(unSignedTransferTx);
        AccountID payerAccountID = txBody.getTransactionID().getAccountID();
        List<Key> keys = new ArrayList<>();
        Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
        keys.add(payerKey);
        Transaction paymentTxSigned = TransactionSigner
                .signTransactionComplexWithSigMap(unSignedTransferTx, keys, pubKey2privKeyMap);
        return paymentTxSigned;
    }

    /**
     * Read genesis info;
     * Add account 55 and its key;
     */
    private void readGenesisInfo() throws Exception {
        Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(CryptoServiceTest.INITIAL_ACCOUNTS_FILE);

        // Get Genesis Account key Pair
        genesisAccountList = keyFromFile.get("START_ACCOUNT");
        genesisAccountID = genesisAccountList.get(0).getAccountId();

        KeyPairObj genesisKeyPair = genesisAccountList.get(0).getKeyPairList().get(0);
        String pubKeyHex = genesisKeyPair.getPublicKeyAbyteStr();
        Key akey ;

        if (KeyExpansion.USE_HEX_ENCODED_KEY) {
            akey = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyHex)).build();
        } else {
            akey = Key.newBuilder().setEd25519(ByteString.copyFrom(ClientBaseThread.hexToBytes(pubKeyHex)))
                    .build();
        }
        genesisPrivateKey = genesisKeyPair.getPrivateKey();
        genKeyPair = new KeyPair(genesisKeyPair.getPublicKey(), genesisKeyPair.getPrivateKey());
        pubKey2privKeyMap.put(pubKeyHex, genesisKeyPair.getPrivateKey());
        acc2ComplexKeyMap.put(genesisAccountID,
                Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(akey)).build());

        //Add key of account55
        acc2ComplexKeyMap.put(account55,
                Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(akey)).build());
    }

    /**
     * Create a freeze transaction
     *
     * @param startHour
     * @param startMin
     * @param duration in minutes
     * @return a freeze transaction
     */
    public static Transaction createFreezeTransaction(final int startHour, final int startMin, final int duration) {
        AccountID payerAccountId = RequestBuilder.getAccountIdBuild(55l, 0l, 0l);
        AccountID nodeAccountId =
                RequestBuilder.getAccountIdBuild(3l, 0l, 0l);
        FreezeTransactionBody freezeBody;

        int endMin = startMin + duration;
        int endHour = (startHour + endMin / 60) % 24;
        endMin = endMin % 60;
        freezeBody = FreezeTransactionBody.newBuilder()
                .setStartHour(startHour).setStartMin(startMin)
                .setEndHour(endHour).setEndMin(endMin).build();

        Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
        Duration transactionDuration = RequestBuilder.getDuration(30);

        long transactionFee = 100000;
        String memo = "Freeze Test";

        TransactionBody.Builder body = RequestBuilder.getTxBodyBuilder(transactionFee,
                timestamp, transactionDuration, true, memo,
                payerAccountId, nodeAccountId);
        body.setFreeze(freezeBody);
        byte[] bodyBytesArr = body.build().toByteArray();
        ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
        return Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    }

    /**
     * Creates a signed transfer tx.
     */
    private static Transaction getSignedTransferTx(AccountID payerAccountID,
                                                   AccountID nodeAccountID,
                                                   AccountID fromAccountID, AccountID toAccountID, long amount, String memo) throws Exception {

        Transaction paymentTx = CryptoServiceTest.getUnSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID, toAccountID, amount, memo);
        Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
        Key fromKey = acc2ComplexKeyMap.get(fromAccountID);
        List<Key> keys = new ArrayList<Key>();
        keys.add(payerKey);
        keys.add(fromKey);
        Transaction paymentTxSigned = TransactionSigner
                .signTransactionComplexWithSigMap(paymentTx, keys, pubKey2privKeyMap);
        return paymentTxSigned;
    }

    public static FileGetInfoResponse.FileInfo createFileAndGetInfo(AccountID payerAccount, AccountID nodeAccount, int size, long durationSeconds) throws Throwable {
        // long durationSeconds = 30 * 24 * 60 * 60; //30 Day
        byte[] fileContents = new byte[size];
        Random random = new Random();
        random.nextBytes(fileContents);
        ByteString fileData = ByteString.copyFrom(fileContents);
        // List<Key> waclPubKeyList = fit.genWaclComplex(1, "single");
        List<Key> waclPubKeyList = new ArrayList<Key>();
        Key key = KeyExpansion.genSingleEd25519KeyByteEncodePubKey(pubKey2privKeyMap);
        waclPubKeyList.add(key);
        Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
        return createFile(payerAccount, nodeAccount, fileData,
                waclPubKeyList, fileExp, "CreateFileAndGetInfo");
    }


    /**
     * create file
     *
     * @return transaction
     */
    public static FileGetInfoResponse.FileInfo createFile(AccountID payerID, AccountID nodeID, ByteString fileData,
                                                          List<Key> waclKeyList, Timestamp fileExp, String memo) throws Throwable {
        log.info("@@@ upload file: file size in byte = " + fileData.size());
        Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
        // Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(DAY_SEC);

        Transaction FileCreateRequest = RequestBuilder.getFileCreateBuilder(payerID.getAccountNum(),
                payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
                nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, transactionDuration, true,
                memo, fileData,
                fileExp, waclKeyList);
        TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
                .extractTransactionBody(FileCreateRequest);
        TransactionID txId = body.getTransactionID();

        Key payerKey = acc2ComplexKeyMap.get(payerID);
        Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeyList)).build();
        List<Key> keys = new ArrayList<Key>();
        keys.add(payerKey);
        keys.add(waclKey);
        Transaction filesigned = TransactionSigner
                .signTransactionComplexWithSigMap(FileCreateRequest, keys, pubKey2privKeyMap);
        TransactionBody txBody = TransactionBody.parseFrom(filesigned.getBodyBytes());
        if (txBody.getTransactionID() == null || !txBody.getTransactionID().hasTransactionValidStart()) {
            return createFile(payerID, nodeID, fileData, waclKeyList, fileExp, memo);
        }
        log.info("\n-----------------------------------");
        log.info("FileCreate: request = " + filesigned);
        TransactionResponse response = fstub.createFile(filesigned);
        log.info("FileCreate Response :: " + response);
        Assert.assertNotNull(response);
        Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());

        // get the file ID
        TransactionReceipt receipt = getTxReceipt(txId);
        if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
            throw new Exception(
                    "Create file failed! The receipt retrieved receipt=" + receipt);
        }
        FileID fid = receipt.getFileID();

        return getFileInfo(fid);
    }

    public static FileGetInfoResponse.FileInfo getFileInfo(FileID fid) throws Exception{

        Transaction paymentTxSigned = getSignedTransferTx(genesisAccountID, nodeID,genesisAccountID, nodeID, TestHelper.getCryptoMaxFee(),"GetFileInfo");
        Query fileGetInfoQuery = RequestBuilder
                .getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
        Response fileInfoResp = fstub.getFileInfo(fileGetInfoQuery);

        ResponseCodeEnum code = fileInfoResp.getFileGetInfo().getHeader()
                .getNodeTransactionPrecheckCode();
        if (code != ResponseCodeEnum.OK) {
            throw new Exception(
                    "Precheck error geting file info! Precheck code = " + code.name() + "\nfileGetInfoQuery="
                            + fileGetInfoQuery);
        }
        return fileInfoResp.getFileGetInfo().getFileInfo();
    }

    /**
     * Creates an account using appropriate signature format.
     *
     * @param payerAccount
     * @param nodeAccount
     * @param initBal
     * @param txFee
     * @param retrieveTxReceipt whether or not to get receipt

     * @return account ID created or null if failed, also null if retrieveTxReceipt is set to false
     * @throws Exception
     */
    public static AccountID createAccount(AccountID payerAccount, AccountID nodeAccount, long initBal,
                                          long txFee, boolean retrieveTxReceipt ) throws Exception {
        Key key = KeyExpansion.genSingleEd25519KeyByteEncodePubKey(pubKey2privKeyMap);

    Duration duration = RequestBuilder.getDuration(accountDuration);
        Transaction createAccountRequest = TestHelperComplex.createAccountComplex(payerAccount,
                nodeAccount, key, initBal, txFee, true, duration);

        TransactionResponse response = cstub.createAccount(createAccountRequest);
        log.debug("createAccount Response :: " + response.getNodeTransactionPrecheckCodeValue());
        Assert.assertNotNull(response);

        // get transaction receipt
        AccountID accountID = null;
        if (retrieveTxReceipt) {
            log.debug("preparing to getTransactionReceipts....");
            TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
            TransactionID transactionID = body.getTransactionID();

            Query query = Query.newBuilder()
                    .setTransactionGetReceipt(
                            RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
                    .build();
            Response transactionReceipts = fetchReceipts(query, cstub);

            accountID = transactionReceipts.getTransactionGetReceipt().getReceipt().getAccountID();
            acc2ComplexKeyMap.put(accountID, key);
            log.debug("Account created: account num :: " + accountID.getAccountNum());
        }

        return accountID;
    }

    /**
     *  Get Account info for given accountID
     * @param accountID
     * @return accountInfo
     * @throws Exception
     */
    protected static Response getAccountInfo(AccountID accountID) throws Exception{
        Transaction paymentTxSigned = getSignedTransferTx(genesisAccountID, nodeID,genesisAccountID, nodeID, TestHelper.getCryptoMaxFee(),"GetAccountInfo");
        Query cryptoGetInfoQuery = RequestBuilder
                .getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.ANSWER_ONLY);
        return cstub.getAccountInfo(cryptoGetInfoQuery);
    }

    /**
     * Creates simple contract
     * @param autoRenewPeriod
     * @param balance
     * @return contractResponse
     * @throws Exception
     */
    protected static Response createContract(long autoRenewPeriod, long balance) throws Exception{
        KeyPair fileAccountKeyPair = genesisAccountList.get(0).getKeyPairList().get(0).getKeyPair();
        String fileName = "simpleStorage.bin";
        FileID simpleStorageFileId = LargeFileUploadIT
                .uploadFile(genesisAccountID, fileName, fileAccountKeyPair);
        Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
        log.info("Smart Contract file uploaded successfully");
        Transaction sampleStorageTransaction = createContractWithOptions(genesisAccountID, simpleStorageFileId,
                nodeID, autoRenewPeriod, 25000L, balance,
                100000L, null, Collections.singletonList(genesisPrivateKey));
        TransactionResponse response = sstub.createContract(sampleStorageTransaction);
        System.out.println(
                " createContractWithOptions Pre Check Response :: " + response
                        .getNodeTransactionPrecheckCode()
                        .name());
        Thread.sleep(300);
        TransactionBody createContractBody = TransactionBody
                .parseFrom(sampleStorageTransaction.getBodyBytes());
        TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
                createContractBody.getTransactionID());
        if (contractCreateReceipt != null) {
            ContractID createdContract = contractCreateReceipt.getReceipt().getContractID();
            log.info("createdContract = " + createdContract);
        }

        return getContractInfo(contractCreateReceipt.getReceipt().getContractID());
    }

    protected static Transaction createContractWithOptions(AccountID payerAccount, FileID contractFile,
                                                  AccountID useNodeAccount, long autoRenewInSeconds, long gas, long balance,
                                                  long transactionFee, ByteString constructorParams,
                                                  List<PrivateKey> adminPrivateKeys)
            throws Exception {

        Duration contractAutoRenew = Duration.newBuilder().setSeconds(autoRenewInSeconds).build();

        Key adminPubKey = null;
        List<PrivateKey> keyList = new ArrayList<>();
        keyList.add(genesisPrivateKey);
        keyList.addAll(adminPrivateKeys);

        Timestamp timestamp = RequestBuilder
                .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
        Duration transactionDuration = RequestBuilder.getDuration(30);
        Transaction createContractRequest = createContractRequest(
                payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
                useNodeAccount.getAccountNum(), useNodeAccount.getRealmNum(), useNodeAccount.getShardNum(),
                transactionFee, timestamp,
                transactionDuration, true, "Transaction Memo", gas, contractFile, constructorParams,
                balance,
                contractAutoRenew, keyList, "Contract Memo", adminPubKey);
        return createContractRequest;
    }

    public static Transaction createContractRequest(Long payerAccountNum, Long payerRealmNum,
                                                    Long payerShardNum,
                                                    Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
                                                    long transactionFee, Timestamp timestamp, Duration txDuration,
                                                    boolean generateRecord, String txMemo, long gas, FileID fileId,
                                                    ByteString constructorParameters, long initialBalance,
                                                    Duration autoRenewalPeriod, List<PrivateKey> keys, String contractMemo,
                                                    Key adminKey) throws Exception {
        Transaction transaction;

        transaction = RequestBuilder
                .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
                        nodeRealmNum, nodeShardNum, transactionFee, timestamp,
                        txDuration, generateRecord, txMemo, gas, fileId, constructorParameters,
                        initialBalance,
                        autoRenewalPeriod, contractMemo, adminKey);

        transaction = TransactionSigner.signTransaction(transaction, keys);
        transactionFee = FeeClient.getContractCreateFee(transaction, keys.size());
        transaction = RequestBuilder
                .getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
                        nodeRealmNum, nodeShardNum, transactionFee, timestamp,
                        txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
                        autoRenewalPeriod, contractMemo, adminKey);

        transaction = TransactionSigner.signTransaction(transaction, keys);
        return transaction;
    }

    protected static TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
        TransactionGetReceiptResponse receiptToReturn = null;
        Query query = Query.newBuilder()
                .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
                        transactionId, ResponseType.ANSWER_ONLY)).build();
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true)
                .build();
        CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
        Response transactionReceipts = stub.getTransactionReceipts(query);
        int attempts = 1;
        while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
                .getReceipt()
                .getStatus().name().equalsIgnoreCase(ResponseCodeEnum.UNKNOWN.name())) {
            Thread.sleep(1000);
            transactionReceipts = stub.getTransactionReceipts(query);
            System.out.println("waiting to getTransactionReceipts as not Unknown..." +
                    transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name() +
                    "  (" + attempts + ")");
            attempts++;
        }
        if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
                .equals(ResponseCodeEnum.SUCCESS)) {
            receiptToReturn = transactionReceipts.getTransactionGetReceipt();
        }
        channel.shutdown();
        return transactionReceipts.getTransactionGetReceipt();

    }

    protected static Response getContractInfo(ContractID contractID) throws Exception {
        Transaction paymentTx = getSignedTransferTx(genesisAccountID, nodeID,genesisAccountID, nodeID, TestHelper.getContractMaxFee(),"GetContractInfo");
        Query getContractInfoQuery = RequestBuilder
                .getContractGetInfoQuery(contractID, paymentTx, ResponseType.ANSWER_ONLY);
        return sstub.getContractInfo(getContractInfoQuery);
    }

    public static void writeToFile(String path, Object map) {
        try{
            File file=new File(path);
            FileOutputStream fos=new FileOutputStream(file);
            ObjectOutputStream oos=new ObjectOutputStream(fos);

            oos.writeObject(map);
            oos.flush();
            oos.close();
            fos.close();
        }catch(Exception e){}
    }

    public static Object readFromFile(String path) {
        //read from file
        HashMap<AccountID, CryptoGetInfoResponse.AccountInfo> mapInFile= new HashMap<>();
        try {
            File toRead = new File(path);
            FileInputStream fis = new FileInputStream(toRead);
            ObjectInputStream ois = new ObjectInputStream(fis);

            mapInFile = (HashMap<AccountID, CryptoGetInfoResponse.AccountInfo>) ois.readObject();
            ois.close();
            fis.close();
        } catch (Exception e) {
            log.warn("Unable to read file: "+path, e);
        }
        return mapInFile;
    }
}
