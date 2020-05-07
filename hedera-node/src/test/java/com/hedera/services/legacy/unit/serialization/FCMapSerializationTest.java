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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.unit.handler.SolidityAddress;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import org.apache.commons.codec.DecoderException;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Akshay
 * @Date : 10/18/2018
 */
public class FCMapSerializationTest {

	public static <T extends FastCopyable> byte[] serialize(final T copyable) throws IOException {
		try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			try (final FCDataOutputStream dos = new FCDataOutputStream(bos)) {
				copyable.copyTo(dos);
				copyable.copyToExtra(dos);

				dos.flush();
				bos.flush();

				return bos.toByteArray();
			}
		}
	}

	@Test
	public void testMapValueSerialization() throws Exception {
		Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		Key key = KeyExpansion.genSampleComplexKey(2, pubKey2privKeyMap);
		AccountID proxyAccountId = AccountID.newBuilder().setAccountNum(1l).setRealmNum(0)
				.setShardNum(0).build();
		HederaAccount mapValue = new HederaAccountCustomizer()
				.fundsReceivedRecordThreshold(100)
				.fundsSentRecordThreshold(100)
				.isReceiverSigRequired(true)
				.key(JKey.mapKey(key))
				.proxy(JAccountID.convert(proxyAccountId))
				.autoRenewPeriod(1800)
				.isDeleted(false)
				.expiry(1000)
				.memo("")
				.isSmartContract(false)
				.customizing(new HederaAccount());
		mapValue.setBalance(100);


		final byte[] serialize = serialize(mapValue);

		HederaAccount deserilise = HederaAccount
				.deserialize(new FCDataInputStream(new ByteArrayInputStream(serialize)));
		Assert.assertEquals(mapValue.toString(), deserilise.toString());


		Assert.assertArrayEquals(serialize(mapValue), serialize(deserilise));
		Assert
				.assertEquals(mapValue.getAccountKeys().toString(), deserilise.getAccountKeys().toString());
		Assert.assertArrayEquals(mapValue.getAccountKeys().serialize(),
				deserilise.getAccountKeys().serialize());
		Assert.assertEquals(mapValue.getAutoRenewPeriod(), deserilise.getAutoRenewPeriod());
		Assert.assertEquals(mapValue.getProxyAccount().getAccountNum(),
				deserilise.getProxyAccount().getAccountNum());
		Assert.assertEquals(mapValue.getProxyAccount().getRealmNum(),
				deserilise.getProxyAccount().getRealmNum());
		Assert.assertEquals(mapValue.getProxyAccount().getShardNum(),
				deserilise.getProxyAccount().getShardNum());
		Assert.assertEquals(mapValue.isDeleted(), deserilise.isDeleted());
		Assert.assertEquals(mapValue.isSmartContract(), deserilise.isSmartContract());
		Assert.assertEquals(mapValue.getMemo(), deserilise.getMemo());
		//   Assert.assertEquals(mapValue.getRecordHash(), deserilise.getRecordHash());

	}

	@Test
	public void testSolidityAddressMapping() throws IOException {
		SolidityAddress solidityAddress = new SolidityAddress(
				"aa3dffa4758bbdd07207109caa21d6f3f9fef65d");

		final byte[] serialize = serialize(solidityAddress);

		SolidityAddress deseralized = SolidityAddress
				.deserialize(new DataInputStream(new ByteArrayInputStream(serialize)));
		Assert.assertEquals(deseralized, solidityAddress);
		Assert.assertEquals(solidityAddress.getSolidityAddress(), deseralized.getSolidityAddress());
	}

	@Test
	public void testMapKey() throws IOException {
		MapKey mapKey = new MapKey(0l, 0l, 5l);

		byte[] serialize = serialize(mapKey);

		MapKey deseralized = MapKey
				.deserialize(new DataInputStream(new ByteArrayInputStream(serialize)));
		Assert.assertEquals(deseralized, mapKey);
	}

	@Test(expected = NegativeAccountBalanceException.class)
	public void testCPPBodyBytes()
			throws InvalidProtocolBufferException, NegativeAccountBalanceException, DecoderException {
		try {
			byte[] bytes = MiscUtils.commonsHexToBytes(
					"0A0D0A06088881D1E605120318EA071202180318A08D06320E437265617465204163636F756E745A0B10FFFFFFFFFFFFFFFFFF01");
			TransactionBody bodyToReturn = TransactionBody.parseFrom(bytes);

			System.out.println("TxBody :: " + bodyToReturn);
			CryptoCreateTransactionBody txBody = bodyToReturn.getCryptoCreateAccount();
			HederaAccount hAccount = new HederaAccount();
			hAccount.setBalance(txBody.getInitialBalance());
		} catch (InvalidProtocolBufferException | DecoderException | NegativeAccountBalanceException e) {
			Assert.assertTrue(true);
			throw e;
		}
		Assert.fail();
	}
}
