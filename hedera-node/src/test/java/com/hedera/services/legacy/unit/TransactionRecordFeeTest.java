package com.hedera.services.legacy.unit;

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

import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeBuilder;

public class TransactionRecordFeeTest {
	
	
	TransactionReceipt transactionReceipt;
	TransactionRecord transactionRecord;
	int recieptStorageTime = 180;
	
	@Before
	public void init() {
		ExchangeRate currentRate = ExchangeRate.newBuilder().setCentEquiv(12).setHbarEquiv(1).build();
		ExchangeRate nextRate = ExchangeRate.newBuilder().setCentEquiv(15).setHbarEquiv(1).build();
		ExchangeRateSet exchangeRateSet = ExchangeRateSet.newBuilder().setCurrentRate(currentRate).setNextRate(nextRate).build();
		AccountID firstAccount = AccountID.newBuilder().setAccountNum(1l).setRealmNum(0l).setShardNum(0l).build();
		AccountID secondAccount = AccountID.newBuilder().setAccountNum(2l).setRealmNum(0l).setShardNum(0l).build();
		transactionReceipt =  TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.OK)
															.setAccountID(firstAccount)
															.setExchangeRate(exchangeRateSet).build();
		
		
		byte[] transactionHash = new byte[48];		
		new Random().nextBytes(transactionHash);
		Timestamp commonTimeStamp = Timestamp.newBuilder().setSeconds(10000000l).build();
		TransactionID txId = TransactionID.newBuilder().setAccountID(firstAccount).setTransactionValidStart(commonTimeStamp).build();
		String memo = "TestTransactionRecord";
		long transactionFee = 10000l;
		AccountAmount accountAmount1 = AccountAmount.newBuilder().setAccountID(firstAccount).setAmount(10000).build();
		AccountAmount accountAmount2 = AccountAmount.newBuilder().setAccountID(secondAccount).setAmount(10000).build();	
		
		TransferList transferList = TransferList.newBuilder().addAccountAmounts(accountAmount1).addAccountAmounts(accountAmount2).build();
		
		transactionRecord = TransactionRecord.newBuilder().setReceipt(transactionReceipt)
														  .setTransactionHash(ByteString.copyFrom(transactionHash))
														  .setConsensusTimestamp(commonTimeStamp)
														  .setTransactionID(txId)
														  .setMemo(memo)
														  .setTransactionFee(transactionFee)
														  .setTransferList(transferList)
														  .build();		
	} 
	
	
	@Test
	public void testTransactionRecordRBH() {
		long transactionRecordRbh = FeeBuilder.getTxRecordUsageRBH(transactionRecord,recieptStorageTime);
		Assert.assertNotEquals(0, transactionRecordRbh);
	}

}
