package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

public interface SysFileSerde<T> {
	T fromRawFile(byte[] bytes);
	byte[] toRawFile(T styledFile);
	String preferredFileName();
}
