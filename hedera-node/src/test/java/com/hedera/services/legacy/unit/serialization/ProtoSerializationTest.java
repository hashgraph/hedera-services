package com.hedera.services.legacy.unit.serialization;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRate.Builder;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.core.jproto.JAccountAmount;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JContractFunctionResult;
import com.hedera.services.legacy.core.jproto.JContractLogInfo;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.hedera.services.legacy.core.jproto.JTransactionID;
import com.hedera.services.legacy.core.jproto.JTransactionReceipt;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.legacy.core.jproto.JTransferList;
import com.hedera.services.legacy.exception.DeserializationException;
import com.swirlds.common.io.FCDataInputStream;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.legacy.unit.serialization.FCMapSerializationTest.serialize;

/**
 * This tests custom serialization of proto classes.
 *
 * @author Akshay
 * @Date : 1/9/2019
 */
public class ProtoSerializationTest {


	@Test
	public void jTransactionReceiptTest() throws IOException, DeserializationException {
		AccountID accountID = RequestBuilder.getAccountIdBuild(12l, 12l, 12l);
		TransactionReceipt transactionReceipt = TransactionReceipt.newBuilder().setAccountID(accountID)
				.build();
		JTransactionReceipt jTransactionReceipt = JTransactionReceipt.convert(transactionReceipt);
		byte[] serialize = serialize(jTransactionReceipt);
		Assert.assertNotNull(serialize);
		Assert.assertTrue(serialize.length > 0);
		JTransactionReceipt deserilized = JTransactionReceipt.deserialize(
				new FCDataInputStream(new ByteArrayInputStream(serialize)));

		Assert.assertNotNull(deserilized);
		Assert.assertEquals(deserilized.getAccountID(), jTransactionReceipt.getAccountID());
		Assert.assertEquals(deserilized.getFileID(), jTransactionReceipt.getFileID());
		Assert.assertEquals(deserilized.getContractID(), jTransactionReceipt.getContractID());
		Assert.assertEquals(deserilized.getStatus(), jTransactionReceipt.getStatus());
	}

	@Test
	public void jAccountIDTest() throws IOException, DeserializationException {
		JAccountID accountID1 = new JAccountID(0, 0, 1);
		byte[] serialize = serialize(accountID1);
		Assert.assertNotNull(serialize);
		Assert.assertTrue(serialize.length > 0);
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serialize);
		JAccountID deserialize = JAccountID.deserialize(new FCDataInputStream(byteArrayInputStream));
		Assert.assertNotNull(deserialize);
		Assert.assertEquals(accountID1.getAccountNum(), deserialize.getAccountNum());
		Assert.assertEquals(accountID1.getRealmNum(), deserialize.getRealmNum());
		Assert.assertEquals(accountID1.getShardNum(), deserialize.getShardNum());
	}

	@Test
	public void jTxIDTest() throws IOException, DeserializationException {
		Timestamp startTime = Timestamp.newBuilder().build();
		TransactionID transactionID = RequestBuilder.getTransactionID(
				startTime, RequestBuilder.getAccountIdBuild(12L, 0l, 0L));
		JTransactionID jTransactionID = JTransactionID.convert(transactionID);
		Assert.assertNotNull(jTransactionID);
		byte[] serialize = serialize(jTransactionID);
		Assert.assertNotNull(serialize);
		Assert.assertTrue(serialize.length > 0);
		JTransactionID deserialize = JTransactionID.deserialize(
				new FCDataInputStream(new ByteArrayInputStream(serialize)));
		Assert.assertNotNull(deserialize);
	}

	@Test
	public void jTimestampTest() throws IOException {
		JTimestamp jTimestamp = new JTimestamp(1, 11);
		byte[] serialize = serialize(jTimestamp);
		Assert.assertNotNull(serialize);
		Assert.assertTrue(serialize.length > 0);
		JTimestamp deserialize = JTimestamp.deserialize(new FCDataInputStream(new ByteArrayInputStream(serialize)));
		Assert.assertNotNull(deserialize);
		Assert.assertEquals(jTimestamp.getSeconds(), deserialize.getSeconds());
		Assert.assertEquals(jTimestamp.getNano(), deserialize.getNano());
	}

	@Test
	public void jTransferListTest() throws IOException {
		AccountAmount a1 = AccountAmount.newBuilder()
				.setAccountID(RequestBuilder.getAccountIdBuild(13L, 0l, 0l))
				.setAmount(-10).build();
		AccountAmount a2 = AccountAmount.newBuilder()
				.setAccountID(RequestBuilder.getAccountIdBuild(15L, 0l, 0l))
				.setAmount(10).build();
		List<AccountAmount> accountAmounts = new ArrayList<>();
		accountAmounts.add(a1);
		accountAmounts.add(a2);
		JTransferList jTransferList = new JTransferList(accountAmounts);
		byte[] serialize = serialize(jTransferList);
		Assert.assertNotNull(serialize);
		Assert.assertTrue(serialize.length > 0);
		List<JAccountAmount> deserialize = ((JTransferList) JTransferList.deserialize(
				new FCDataInputStream(new ByteArrayInputStream(serialize)))).getjAccountAmountsList();
		Assert.assertNotNull(deserialize);
		Assert.assertEquals(2, deserialize.size());
		Assert.assertEquals(JAccountID.convert(a1.getAccountID()), deserialize.get(0).getAccountID());
		Assert.assertEquals(a1.getAmount(), deserialize.get(0).getAmount());
		Assert.assertEquals(JAccountID.convert(a2.getAccountID()), deserialize.get(1).getAccountID());
		Assert.assertEquals(a2.getAmount(), deserialize.get(1).getAmount());
	}

	@Test
	public void contractFunctionResultTest() throws DeserializationException, IOException {
		JAccountID contractID = new JAccountID(0, 0, 13);
		String result = "this is success result...";
		String error = "NPE Exception";
		String bloom = "i don't know what it is..?";
		List<JContractLogInfo> JContractLogInfos = new ArrayList<>();
		String data = "aljslds  sdlslksd sdklsdsd";
		JContractLogInfo JContractLogInfo = new JContractLogInfo(contractID,
				bloom.getBytes(), null, data.getBytes());
		JContractLogInfos.add(JContractLogInfo);
		JAccountID createdContractID = new JAccountID(0, 0, 17);
		List<JAccountID> jCreatedContractIDs = new ArrayList<>();
		jCreatedContractIDs.add(createdContractID);
		JContractFunctionResult functionResult =
				new JContractFunctionResult(contractID, result.getBytes(), error,
						bloom.getBytes(), 13452L, JContractLogInfos, jCreatedContractIDs);
		byte[] serialize = serialize(functionResult);
		Assert.assertNotNull(serialize);
		Assert.assertTrue(serialize.length > 0);
		JContractFunctionResult deserialize = JContractFunctionResult.deserialize(
				new FCDataInputStream(new ByteArrayInputStream(serialize)));
		Assert.assertNotNull(deserialize);
		Assert.assertEquals(contractID, deserialize.getContractID());
		Assert.assertEquals(result, new String(deserialize.getResult()));
		Assert.assertEquals(error, deserialize.getError());
		Assert.assertEquals(bloom, new String(deserialize.getBloom()));
		Assert.assertNotNull(deserialize.getjContractLogInfo());
		Assert.assertEquals(1, deserialize.getjContractLogInfo().size());
		Assert.assertEquals(contractID, deserialize.getjContractLogInfo().get(0).getContractID());
		Assert.assertEquals(bloom, new String(deserialize.getjContractLogInfo().get(0).getBloom()));
		Assert.assertTrue(deserialize.getjContractLogInfo().get(0).getTopic().isEmpty());
		Assert.assertEquals(data, new String(deserialize.getjContractLogInfo().get(0).getData()));
		List<JAccountID> actual = deserialize.getjCreatedContractIDs();
		Assert.assertEquals(1, actual.size());
		Assert.assertEquals(createdContractID, actual.get(0));
	}

	@Test
	public void transactionRecordTest() throws IOException, DeserializationException {
		// case 1 :
		Timestamp startTime = RequestBuilder.getTimestamp(Instant.now());
		TransactionID transactionID = RequestBuilder.getTransactionID(
				startTime, RequestBuilder.getAccountIdBuild(12L, 0l, 0L));
		String memo = "this is memo";
		TransactionReceipt transactionReceipt = RequestBuilder.getTransactionReceipt(
				RequestBuilder.getAccountIdBuild(155L, 0l, 0l),
				ResponseCodeEnum.SUCCESS, ExchangeRateSet.getDefaultInstance());
		TransactionRecord.Builder transactionRecordBuilder =
				RequestBuilder.getTransactionRecord(22343L, memo, transactionID, startTime,
						transactionReceipt);
		transactionRecordBuilder.setTransactionHash(
				ByteString.copyFrom("this is account create tx", Charset.defaultCharset()));
		TransactionRecord transactionRecord = transactionRecordBuilder.build();

		JTransactionRecord jTransactionRecord = JTransactionRecord.convert(transactionRecord);
		byte[] serialize = serialize(jTransactionRecord);
		Assert.assertNotNull(serialize);
		Assert.assertTrue(serialize.length > 0);
		JTransactionRecord deserialize = JTransactionRecord.deserialize(
				new FCDataInputStream(new ByteArrayInputStream(serialize)));
		Assert.assertNotNull(deserialize);
		Assert.assertEquals(jTransactionRecord.getTxReceipt().getStatus(),
				deserialize.getTxReceipt().getStatus());
	    Assert.assertNull(jTransactionRecord.getTxReceipt().getFileID());
	    Assert.assertNull(jTransactionRecord.getTxReceipt().getContractID());
		Assert.assertEquals(jTransactionRecord.getTransactionID(), deserialize.getTransactionID());
		Assert.assertEquals(jTransactionRecord.getTxHash().length, deserialize.getTxHash().length);
		Assert.assertEquals(jTransactionRecord.getConsensusTimestamp(),
				deserialize.getConsensusTimestamp());
		Assert.assertEquals(jTransactionRecord.getMemo(), deserialize.getMemo());
		Assert.assertEquals(jTransactionRecord.getTransactionFee(), deserialize.getTransactionFee());
		Assert.assertEquals(jTransactionRecord.getjTransferList(), deserialize.getjTransferList());
		TransactionRecord record = JTransactionRecord.convert(deserialize);
		Assert.assertNotNull(record);
		Assert.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);
	}

  @Test
  public void transactionRecordWithExchangeRateTest() throws IOException, DeserializationException {
    Timestamp startTime = RequestBuilder.getTimestamp(Instant.now());
    AccountID payerID = RequestBuilder.getAccountIdBuild(12l, 0l, 0l);
    AccountID createdAccountID = RequestBuilder.getAccountIdBuild(155L, 0l, 0l);
    TransactionID transactionID = RequestBuilder.getTransactionID(
        startTime, payerID );
    String memo = "this is transactionRecordWithExchangeRateTest";
    Builder currentRate = ExchangeRate.newBuilder().setCentEquiv(3).setHbarEquiv(5)
        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(7));
    Builder nextRate = ExchangeRate.newBuilder().setCentEquiv(9).setHbarEquiv(11)
        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(13));
    ExchangeRateSet rateSet = ExchangeRateSet.newBuilder()
        .setCurrentRate(currentRate).setNextRate(nextRate).build();
    TransactionReceipt transactionReceipt = RequestBuilder.getTransactionReceipt(
        createdAccountID, ResponseCodeEnum.SUCCESS, rateSet);
    TransactionRecord.Builder transactionRecordBuilder =
        RequestBuilder.getTransactionRecord(22343L, memo, transactionID, startTime,
            transactionReceipt);
    transactionRecordBuilder.setTransactionHash(
        ByteString.copyFrom("this is account create tx", Charset.defaultCharset()));

    TransferList transferList = TransferList.newBuilder()
        .addAccountAmounts(AccountAmount.newBuilder().setAccountID(payerID).setAmount(-100000))
        .addAccountAmounts(AccountAmount.newBuilder().setAccountID(AccountID.newBuilder().setAccountNum(98).setRealmNum(0).setShardNum(0)).setAmount(10000))
        .build();
    transactionRecordBuilder.setTransferList(transferList);
    TransactionRecord transactionRecord = transactionRecordBuilder.build();
    System.out.println(">>>> original transactionRecord = " + transactionRecord);
  
    JTransactionRecord jTransactionRecord = JTransactionRecord.convert(transactionRecord);
    byte[] serialize = serialize(jTransactionRecord);
    Assert.assertNotNull(serialize);
    Assert.assertTrue(serialize.length > 0);
    JTransactionRecord deserialize = JTransactionRecord.deserialize(
            new FCDataInputStream(new ByteArrayInputStream(serialize)));
    
    TransactionRecord reborn = JTransactionRecord.convert(deserialize);    
    System.out.println(">>>> reborn transactionRecord = " + reborn);
    
    
    Assert.assertNotNull(deserialize);
    Assert.assertEquals(jTransactionRecord.getTxReceipt().getStatus(),
        deserialize.getTxReceipt().getStatus());
    
    // we should have the same proto bytes
    Assert.assertArrayEquals(transactionRecord.toByteArray(), reborn.toByteArray());
    
    // check exchange rate set
    Assert.assertArrayEquals(jTransactionRecord.getTxReceipt().getExchangeRate().serialize(), 
        deserialize.getTxReceipt().getExchangeRate().serialize());
    
    Assert.assertEquals(jTransactionRecord.getTxReceipt().getAccountID(), JAccountID.convert(createdAccountID));
    Assert.assertNull(jTransactionRecord.getTxReceipt().getFileID());
    Assert.assertNull(jTransactionRecord.getTxReceipt().getContractID());
    Assert.assertEquals(jTransactionRecord.getTransactionID(), deserialize.getTransactionID());
    Assert.assertEquals(jTransactionRecord.getTxHash().length, deserialize.getTxHash().length);
    Assert.assertEquals(jTransactionRecord.getConsensusTimestamp(),
        deserialize.getConsensusTimestamp());
    Assert.assertEquals(jTransactionRecord.getMemo(), deserialize.getMemo());
    Assert.assertEquals(jTransactionRecord.getTransactionFee(), deserialize.getTransactionFee());
    Assert.assertEquals(jTransactionRecord.getjTransferList(), deserialize.getjTransferList());
    TransactionRecord record = JTransactionRecord.convert(deserialize);
    Assert.assertNotNull(record);
    Assert.assertEquals(record.getReceipt().getStatus(), ResponseCodeEnum.SUCCESS);
  }

  /**
   * Tests tx record conversion when the transactionID is not set.
   *
   * @author hli
   * @Date :7/24/2019
   */
    @Test
    public void testBadRecordConversion() {
      // creates a receipt
      AccountID accID = AccountID.newBuilder().setAccountNum(1001).build();
      TransactionReceipt transactionReceipt = TransactionReceipt.newBuilder()
          .setAccountID(accID)
          .setStatus(ResponseCodeEnum.SUCCESS).build();
      
      //creates a proto record without setting transactionID
      TransactionRecord protoRecord = TransactionRecord.newBuilder()  
          .setReceipt(transactionReceipt).setMemo("memo")
             .setConsensusTimestamp(Timestamp.newBuilder()
                 .setSeconds(10)).build();
      System.out.println("protoRecord=" + protoRecord);
      Assert.assertFalse(protoRecord.hasTransactionID());
      
      // convert the proto record to jrecord, which should have a transactionID object with null fields
      JTransactionRecord jrecord = JTransactionRecord.convert(protoRecord);
      Assert.assertNull(jrecord.getTransactionID().getPayerAccount());
      Assert.assertNull(jrecord.getTransactionID().getStartTime());
      
      // covert the jrecord back to proto record, whose transactionID should not be set
      TransactionRecord protoRecordReborn = JTransactionRecord.convert(jrecord);
      System.out.println("protoRecordReborn=" + protoRecordReborn);
      Assert.assertFalse(protoRecordReborn.hasTransactionID());
      Assert.assertArrayEquals(protoRecord.toByteArray(), protoRecordReborn.toByteArray());
    }
}
