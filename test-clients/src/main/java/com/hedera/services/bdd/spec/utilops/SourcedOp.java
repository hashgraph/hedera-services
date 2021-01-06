package com.hedera.services.bdd.spec.utilops;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;

import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

public class SourcedOp extends UtilOp {
	private final Supplier<HapiSpecOperation> source;

	public SourcedOp(Supplier<HapiSpecOperation> source) {
		this.source = source;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		allRunFor(spec, source.get());
		return false;
	}
}
