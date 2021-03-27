package com.hedera.services.sysfiles.validation;

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
