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
 * Maps to proto Key of type RSA_3072.
 *
 * @author hua Created on 2019-01-07
 */
public class JRSA_3072Key extends JKey {

	private static final long serialVersionUID = 1L;
	private byte[] RSA_3072Key = null;

	public JRSA_3072Key(byte[] RSA_3072Key) {
		this.RSA_3072Key = RSA_3072Key;
	}

	@Override
	public String toString() {
		return "<JRSA_3072Key: RSA_3072Key hex=" + MiscUtils.commonsBytesToHex(RSA_3072Key) + ">";
	}

	public boolean hasRSA_3072Key() {
		return true;
	}

	public byte[] getRSA3072() {
		return RSA_3072Key;
	}

	@Override
	public boolean isEmpty() {
		return ((null == RSA_3072Key) || (0 == RSA_3072Key.length));
	}

	@Override
	public boolean isValid() {
		return !isEmpty();
	}
}
