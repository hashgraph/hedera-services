package com.opencrowd.core;

import com.opencrowd.core.KeyPairObj;
import com.hederahashgraph.api.proto.java.AccountID;

import java.io.Serializable;
import java.util.List;

public class AccountKeyListObj implements Serializable {
	private static final long serialVersionUID = -4429672793456228453L;

	private AccountID accountId;
	private List<KeyPairObj> keyPairList;

	public AccountKeyListObj(AccountID accountId, List<KeyPairObj> keyPairList) {
		this.accountId = accountId;
		this.keyPairList = keyPairList;
	}

	public List<KeyPairObj> getKeyPairList() {
		return keyPairList;
	}

	public void setKeyPairList(List<KeyPairObj> keyPairList) {
		this.keyPairList = keyPairList;
	}

	public AccountID getAccountId() {
		return accountId;
	}

	public void setAccountId(AccountID accountId) {
		this.accountId = accountId;
	}
}
