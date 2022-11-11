package com;


import edu.umd.cs.findbugs.annotations.NonNull;

public class NullCheck {

	@NonNull
	static String getNullValue() {
		return null;
	}

	@NonNull
	static String getValue() {
		return "yeah!";
	}

	static void putValue(@NonNull String value) {
		System.out.println(value);
	}

	public static void main(String[] args) {
		getValue();
		getNullValue();
		putValue("A");
		putValue(null);
	}
}
