package com.hedera.node.app.spi.fee;

import edu.umd.cs.findbugs.annotations.NonNull;

public interface Chargeable {

	@NonNull
	String getName();
	
}
