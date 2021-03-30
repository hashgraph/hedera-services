package com.hedera.services.sysfiles.validation;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Optional;
import java.util.regex.Pattern;

public class ErrorCodeUtils {
	static final String EXC_MSG_TPL = "%s :: %s";
	static final Pattern ERROR_CODE_PATTERN = Pattern.compile("(.*) :: .*");

	public static String exceptionMsgFor(ResponseCodeEnum error, String details) {
		return String.format(EXC_MSG_TPL, error, details);
	}

	public static Optional<ResponseCodeEnum> errorFrom(String exceptionMsg) {
		var matcher = ERROR_CODE_PATTERN.matcher(exceptionMsg);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(ResponseCodeEnum.valueOf(matcher.group(1)));
	}
}
