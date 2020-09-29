package com.hedera.test.utils;

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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransferList;

import java.util.List;
import java.util.UUID;

import static com.hedera.services.legacy.proto.utils.CommonUtils.toReadableString;
import static com.hedera.test.factories.txns.CryptoTransferFactory.newSignedCryptoTransfer;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;

public class TxnUtils {
	public static String txnToString(Transaction txn) throws InvalidProtocolBufferException {
		return toReadableString(txn);
	}

	public static TransferList withAdjustments(AccountID a, long A, AccountID b, long B, AccountID c, long C) {
		return TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
				.build();
	}

	public static TransferList withAdjustments(
			AccountID a, long A,
			AccountID b, long B,
			AccountID c, long C,
			AccountID d, long D) {
		return TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(d).setAmount(D).build())
				.build();
	}

	public static List<TokenTransferList> withTokenAdjustments(
			TokenID a, AccountID aId, long A,
			TokenID b, AccountID bId, long B,
			TokenID c, AccountID cId, long C
	) {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(a)
						.addTransfers(AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(b)
						.addTransfers(AccountAmount.newBuilder().setAccountID(bId).setAmount(B).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(c)
						.addTransfers(AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
						.build()
		);
	}

	public static List<TokenTransferList> withTokenAdjustments(
			TokenID a, AccountID aId, long A,
			TokenID b, AccountID bId, long B,
			TokenID c, AccountID cId, long C,
			TokenID d, AccountID dId, long D
	) {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(a)
						.addTransfers(AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(b)
						.addTransfers(AccountAmount.newBuilder().setAccountID(bId).setAmount(B).build())
						.addTransfers(AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
						.addTransfers(AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(c)
						.addTransfers(AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(d)
						.addTransfers(AccountAmount.newBuilder().setAccountID(dId).setAmount(D).build())
						.build()
		);
	}

	public static List<TokenTransferList> withTokenAdjustments(
			TokenID a, TokenID b
	) {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(a)
						.build(),
				TokenTransferList.newBuilder()
						.setToken(b)
						.build()
		);
	}

	public static Transaction payerSponsoredTransfer(
			String payer,
			KeyTree payerKey,
			String beneficiary,
			long amount
	) throws Throwable {
		return newSignedCryptoTransfer()
				.payer(payer)
				.payerKt(payerKey)
				.transfers(tinyBarsFromTo(payer, beneficiary, amount))
				.get();
	}

	public static byte[] randomUtf8Bytes(int n) {
		byte[] data = new byte[n];
		int i = 0;
		while (i < n) {
			byte[] rnd = UUID.randomUUID().toString().getBytes();
			System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
			i += rnd.length;
		}
		return data;
	}

	public static ByteString randomUtf8ByteString(int n) {
		return ByteString.copyFrom(randomUtf8Bytes(n));
	}
}
