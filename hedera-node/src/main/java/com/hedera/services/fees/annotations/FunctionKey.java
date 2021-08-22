package com.hedera.services.fees.annotations;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import dagger.MapKey;

@MapKey
public @interface FunctionKey {
	HederaFunctionality value();
}
