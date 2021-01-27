package com.hedera.services.yahcli.output;

public enum CommonMessages {
	COMMON_MESSAGES;

	public void printGlobalInfo(String network, String payer) {
		var msg = String.format("Targeting %s, paying with %s", network, asId(payer));
		System.out.println(msg);
	}

	public String fq(Integer num) {
		return "0.0." + num;
	}

	public static String asId(String account) {
		try {
			int number = Integer.parseInt(account);
			return "0.0." + number;
		} catch (NumberFormatException ignore) {}
		return account;
	}
}
