package com.hedera.services.test;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;

public class TxnUtils {
	public static TransferList withAdjustments(AccountID a, long A, AccountID b, long B, AccountID c, long C) {
		return TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
				.build();
	}
}
