package com.hedera.services.yahcli.output;

import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;

public enum CommonMessages {
	COMMON_MESSAGES;

	public void printGlobalInfo(ConfigManager config) {
		var msg = String.format("Targeting %s, paying with %s", config.getTargetName(), ConfigUtils.asId(config.getDefaultPayer()));
		System.out.println(msg);
	}

	public String fq(Integer num) {
		return "0.0." + num;
	}
}
