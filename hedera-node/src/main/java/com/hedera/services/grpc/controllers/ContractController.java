package com.hedera.services.grpc.controllers;

public class ContractController {
	/* Transactions */
	public static final String CALL_CONTRACT_METRIC = "contractCallMethod";
	public static final String CREATE_CONTRACT_METRIC = "createContract";
	public static final String UPDATE_CONTRACT_METRIC = "updateContract";
	public static final String DELETE_CONTRACT_METRIC = "deleteContract";
	public static final String SYS_DELETE_CONTRACT_METRIC = "smartContractSystemDelete";
	public static final String SYS_UNDELETE_CONTRACT_METRIC = "smartContractSystemUndelete";
	/* Queries */
	public static final String GET_CONTRACT_INFO_METRIC = "getContractInfo";
	public static final String LOCALCALL_CONTRACT_METRIC = "contractCallLocalMethod";
	public static final String GET_CONTRACT_RECORDS_METRIC = "getTxRecordByContractID";
	public static final String GET_CONTRACT_BYTECODE_METRIC = "ContractGetBytecode";
	public static final String GET_SOLIDITY_ADDRESS_INFO_METRIC = "getBySolidityID";
}
