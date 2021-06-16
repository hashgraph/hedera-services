package com.hedera.services.txns.span;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.utils.TxnAccessor;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class SpanMapManager {
	private final GlobalDynamicProperties dynamicProperties;
	private final ImpliedTransfersMarshal impliedTransfersMarshal;
	private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

	public SpanMapManager(
			ImpliedTransfersMarshal impliedTransfersMarshal,
			GlobalDynamicProperties dynamicProperties
	) {
		this.impliedTransfersMarshal = impliedTransfersMarshal;
		this.dynamicProperties = dynamicProperties;
	}

	public void expandSpan(TxnAccessor accessor) {
		if (accessor.getFunction() == CryptoTransfer) {
			expandImpliedTransfers(accessor);
		}
	}

	public void rationalizeSpan(TxnAccessor accessor) {
		if (accessor.getFunction() == CryptoTransfer) {
			rationalizeImpliedTransfers(accessor);
		}
	}

	private void rationalizeImpliedTransfers(TxnAccessor accessor) {
		final var impliedTransfers = spanMapAccessor.getImpliedTransfers(accessor);
		if (impliedTransfers.getMeta().wasDerivedFrom(dynamicProperties)) {
			return;
		} else {
			expandImpliedTransfers(accessor);
		}
	}

	private void expandImpliedTransfers(TxnAccessor accessor) {
		final var op = accessor.getTxn().getCryptoTransfer();
		final var impliedTransfers = impliedTransfersMarshal.marshalFromGrpc(op);

		spanMapAccessor.setImpliedTransfers(accessor, impliedTransfers);
	}
}
