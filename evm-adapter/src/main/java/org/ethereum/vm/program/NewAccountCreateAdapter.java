package org.ethereum.vm.program;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;

public  class NewAccountCreateAdapter {
	public NewAccountCreateAdapter(){
		createdContracts = new HashMap<byte[],List<byte[]>>();
	}
	private Map<byte[],List<byte[]>> createdContracts ;
	public byte[] calculateNewAddress(byte[] ownerAddress , Repository track) {
		byte[] nonce = track.getNonce(ownerAddress).toByteArray();
		byte[] newAddress = HashUtil.calcNewAddr(ownerAddress, nonce);
		return newAddress;
	}
	public void addCreatedContract(byte[] newContractAddress , byte[] creatorAddress ,Repository track) {
		List<byte[]> contractsForCreator = createdContracts.get(creatorAddress);
		if(contractsForCreator==null) {
			contractsForCreator= new ArrayList<byte[]>();
		}
		contractsForCreator.add(newContractAddress);
		createdContracts.put(creatorAddress, contractsForCreator);
	}
	
	public Map<byte[],List<byte[]>>getCreatedContracts() {
		return createdContracts;
	}
	
	
}
