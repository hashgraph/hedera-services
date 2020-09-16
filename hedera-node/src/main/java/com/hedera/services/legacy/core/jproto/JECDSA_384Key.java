package com.hedera.services.legacy.core.jproto;

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

import com.hedera.services.utils.MiscUtils;

/**
 * Maps to proto KeyList.
 *
 * @author hua Created on 2018-11-02
 */
public class JECDSA_384Key extends JKey {

	private static final long serialVersionUID = 1L;
	private byte[] ECDSA_384Key = null;

	public JECDSA_384Key(byte[] ECDSA_384Key) {
		this.ECDSA_384Key = ECDSA_384Key;
	}

	@Override
	public String toString() {
		return "<JECDSA_384Key: ECDSA_384Key hex=" + MiscUtils.commonsBytesToHex(ECDSA_384Key) + ">";
	}

	@Override
	public boolean isEmpty() {
		return ((null == ECDSA_384Key) || (0 == ECDSA_384Key.length));
	}

	public boolean hasECDSA_384Key() {
		return true;
	}

	public byte[] getECDSA384() {
		return ECDSA_384Key;
	}

	@Override
	public boolean isValid() {
		return !isEmpty();
	}
}
