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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.*;

import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.bip39utils.bip39.Mnemonic;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * @author  SK
 * This utility helps with the BIP39 keys generation for new keys
 * this does not work with the old key generation mechanism.
 *
 */
public class BIP39KeysUtil {

  private  CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static final Logger log = LogManager.getLogger("Bip39Util");
  private  String host;
  private long nodeAccountId;
  private long bipAccountId;


  public BIP39KeysUtil( String host, int port, long nodeAccountId, long accountId) {
    // connecting to the grpc server on the port
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    this.stub = CryptoServiceGrpc.newBlockingStub(channel);
    this.host = host;
    this.nodeAccountId = nodeAccountId;
    this.bipAccountId = accountId;
  }


  /**
   * Arg 0 - Host IP
   * Arg 1 - Node Account
   * Arg 2 - AccountId for the key owner
   * Arg 3 - Comma separated Mnemonic
    * @param args
   */
  public static void main(String[] args) {

    String mnemonic = "one two three";
    String host = "35.188.20.11";
    long nodeAccount =3l;
    long qAccountId = 11628l;
    long payerAccountId = 11337l;

    Properties properties = TestHelper.getApplicationProperties();

    if ((args.length) > 0) { // Get host ip
      host = args[0];  }
    else
    {
      host =  properties.getProperty("host");
    }

    if ((args.length) > 1) {
      try {
        nodeAccount = Long.parseLong(args[1]);
      }
      catch(Exception ex){
        log.info("Invalid data passed for node id");
        nodeAccount = Utilities.getDefaultNodeAccount();
      }
    }
    else
    {
      nodeAccount = Utilities.getDefaultNodeAccount();
    }

    if ((args.length) > 2) {
      try {
        qAccountId = Long.parseLong(args[2]);
      }
      catch(Exception ex){
        log.info("Invalid data passed for node id");
        qAccountId = 52142l;
      }
    }
    else
    {
      nodeAccount = 52142l;
    }

    if ((args.length) > 3) {

        mnemonic = args[3];

        if(mnemonic.length() > 50)
        {
          mnemonic = mnemonic.replaceAll(","," ");
        }
        else
        {
          log.warn("Mnemonic Provided : " + mnemonic);
          log.warn("Does not look right");
        }

    }

    BIP39KeysUtil util = new BIP39KeysUtil(  host, 50211, nodeAccount , qAccountId);
    try {
      EDKeyPair keyPair = (EDKeyPair) util.getED25519_Bip39Keys(mnemonic);
      byte[] privateKeyBytes = keyPair.getPrivateKey();
      byte[] pubKeyBytes = keyPair.getPublicKey();
      System.out.println("PubKey[ " + HexUtils.bytes2Hex(pubKeyBytes) + " ]");
      System.out.println("PrivKey[ " + HexUtils.bytes2Hex(privateKeyBytes) + " ]");
      System.out.println(keyPair.getPublicKey().length);
      System.out.println(keyPair.getPrivateKey().length);

//      long queryStuff = 1000l;
//      Response accountrecResponse =executeGetAccountRecords(util.stub,
//          AccountID.newBuilder().setAccountNum(qAccountId).build(),
//          AccountID.newBuilder().setAccountNum(payerAccountId).build(),
//          keyPair, AccountID.newBuilder().setAccountNum(nodeAccount).build(),queryStuff,ResponseType.ANSWER_ONLY);
//
//      System.out.println("******* Account Rec Resp Start***************");
//      System.out.println(accountrecResponse);
//      System.out.println("******* Account Rec Resp End***************");
//      System.exit(0);


      Response accountInfoResponse = getCryptoGetAccountInfo(util.stub,
              AccountID.newBuilder().setAccountNum(qAccountId).build(),
              AccountID.newBuilder().setAccountNum(payerAccountId).build(),
              keyPair, AccountID.newBuilder().setAccountNum(nodeAccount).build());
      System.out.println("******* Account Info Resp Start***************");
      System.out.println(accountInfoResponse);
      System.out.println("******* Account Info Resp End***************");
      if(accountInfoResponse.getCryptoGetInfo().hasAccountInfo()) {
        com.hederahashgraph.api.proto.java.Key myKey = accountInfoResponse.getCryptoGetInfo()
            .getAccountInfo().getKey();
        System.out.println("******* Key in Hex Start***************");
      System.out.println( HexUtils.bytes2Hex(myKey.getEd25519().toByteArray()));
        System.out.println("******* Key in Hex End***************");
      }

    } catch (Exception ex) {
      ex.printStackTrace();
    }

  }

  /**
   * Generates the Seed and the EDKeyPair - Matching with Wallet
   * SLIP 10 , SLIP 44 and BIP39 Keys with Hedera Tree of XHB (44, 3030, 0, 0, 0)
   * Pass the mnemonics in the order for a BIP39 key generated by the wallet
   *
   * @param mnemonic24Words
   * @return
   * @throws Exception
   */
  public KeyPair getED25519_Bip39Keys(String mnemonic24Words) throws Exception {
    if ((mnemonic24Words == null) || (mnemonic24Words.length() < 100)) {
      throw new Exception("Invalid Mnemonics Provided ");
    }

    EDKeyPair keyPair = null;
    try {

      byte[] seed = Mnemonic.generateSeed(mnemonic24Words, "");
      byte[] edSeed = SLIP10.deriveEd25519PrivateKey(seed, 44, 3030, 0, 0, 0);
      keyPair = new EDKeyPair(edSeed);



    } catch (Exception ex) {
      ex.printStackTrace();

    }
    return keyPair;
  }

  /**
   *
   * @param fromAccount
   * @param edKeyPair
   * @param toAccount
   * @param payerAccount
   * @param nodeAccount
   * @param amount
   * @return
   */
  public static Transaction createTransfer(AccountID fromAccount, EDKeyPair edKeyPair,
      AccountID toAccount,
      AccountID payerAccount,  AccountID nodeAccount,
      long amount) {
    long txTransactionFees = 200000l;
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), txTransactionFees, timestamp, transactionDuration,
        true,
        "Test Transfer", fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
        amount);
    // sign the tx

    Transaction signedTx = null;
    try{
      signedTx = signTransactionNewWithSignatureMap(transferTx,edKeyPair);

    }catch(Exception ex){
      ex.printStackTrace();
    }
    System.out.println("******* Signed TX Start***************");
    System.out.println(signedTx);
    System.out.println("******* Signed TX End***************");
    TransactionBody body;
    try {
      body = TransactionBody.parseFrom(signedTx.getBodyBytes());
      System.out.println("******* Body TX Start***************");
      System.out.println(body);
      System.out.println("******* Body TX End***************");
    }catch(Exception ex){
      ex.printStackTrace();
    }

    return signedTx;
  }

  public static Response getCryptoGetAccountInfo(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerAccount,
      EDKeyPair edKeyPair, AccountID nodeAccount) throws Exception {

    // first get the fee for getting the account info

    Response response = null;
      long getAcctFee = 100000l;//response.getCryptoGetInfo().getHeader().getCost();
      response = executeAccountInfoQuery(stub, accountID, payerAccount, edKeyPair,
          nodeAccount,
          getAcctFee, ResponseType.ANSWER_ONLY);
   // }
    return response;
  }


  /**
   *
   * @param stub
   * @param accountID
   * @param edKeyPair
   * @param nodeAccount
   * @param costForQuery
   * @param responseType
   * @return
   * @throws Exception
   */
  public static Response executeAccountInfoQuery(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      AccountID accountID, AccountID payerAccount, EDKeyPair edKeyPair,
      AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
    Transaction transferTransaction = createTransfer(payerAccount, edKeyPair, nodeAccount,
            payerAccount, nodeAccount, costForQuery);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, transferTransaction, responseType);
    System.out.println("AccQ -" + cryptoGetInfoQuery);
   // System.exit(0);
    return stub.getAccountInfo(cryptoGetInfoQuery);
  }

  /**
   *
   * @param transaction
   * @param edKeyPair
   * @return
   * @throws Exception
   */
  public static Transaction signTransactionNewWithSignatureMap(Transaction transaction,
      EDKeyPair edKeyPair) throws Exception {
    byte[] bodyBytes = CommonUtils.extractTransactionBodyBytes(transaction).toByteArray();

    SignaturePair sig = signAsSignaturePair(edKeyPair, bodyBytes);
    List<SignaturePair> pairs = new ArrayList<>();
    pairs.add(sig);

    SignatureMap sigsMap = SignatureMap.newBuilder().addAllSigPair(pairs).build();
    return transaction.toBuilder().setSigMap(sigsMap).build();
  }

  private static SignaturePair signAsSignaturePair(EDKeyPair edKeyPair, byte[] bodyBytes) throws DecoderException {
    byte[] pubKeyBytes = edKeyPair.getPublicKey();

    String sigHex = HexUtils.bytes2Hex(edKeyPair.signMessage(bodyBytes));
    SignaturePair rv = SignaturePair.newBuilder()
        .setPubKeyPrefix(ByteString.copyFrom(pubKeyBytes))
        .setEd25519(ByteString.copyFrom(Hex.decodeHex(sigHex))).build();
    return rv;
  }
}
