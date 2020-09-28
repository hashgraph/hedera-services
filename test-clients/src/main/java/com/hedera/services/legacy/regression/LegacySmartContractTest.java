package com.hedera.services.legacy.regression;

import com.hederahashgraph.api.proto.java.AccountID;

public abstract class LegacySmartContractTest {
	abstract protected void demo(String grpcHost, AccountID nodeAccount) throws Exception;
}
