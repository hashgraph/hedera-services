package com.hedera.services.legacy.util;

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

import java.math.BigInteger;

import org.ethereum.core.CallTransaction;

/**
 * @author peter
 * 		Smart contract call encoding for use by smart contract unit tests.
 * 		Copied from SmartContractSimpleStorage regression test.
 */
public class SCEncoding {
	private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}]," +
			"\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	public static final String SC_ALL_ABI = "[{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}]," +
			"\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\"," +
			"\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}," +
			"{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"_from\",\"type\":\"address\"}," +
			"{\"indexed\":false,\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"Stored\",\"type\":\"event\"}]";

	private static final String SC_GET_BALANCE = "{\"constant\":true,\"inputs\":[],\"name\":\"getBalance\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SC_DEPOSIT = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\"," +
			"\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";
	private static final String SC_GET_BALANCE_OF = "{\"constant\":true,\"inputs\":[{\"name\":\"accToCheck\"," +
			"\"type\":\"address\"}],\"name\":\"getBalanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_SEND_FUNDS = "{\"constant\":false,\"inputs\":[{\"name\":\"receiver\"," +
			"\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"sendFunds\",\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	private static final String SC_MAP_PUT_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"key\"," +
			"\"type\":\"uint256\"},{\"name\":\"val\",\"type\":\"uint256\"}],\"name\":\"put\",\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_MAP_GET_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"key\"," +
			"\"type\":\"uint256\"}],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}]";

	private static final String SC_CT_CREATE_ABI = "{\"constant\":false,\"inputs\":[],\"name\":\"create\"," +
			"\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_CT_GETINDIRECT_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getIndirect\"," +
			"\"outputs\":[{\"name\":\"value\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SC_CT_GETADDRESS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getAddress\"," +
			"\"outputs\":[{\"name\":\"retval\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";

	private static final String SC_OPSHL_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_one\"," +
			"\"type\":\"uint256\"},{\"name\":\"_two\",\"type\":\"uint256\"}],\"name\":\"opShl\"," +
			"\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"pure\"," +
			"\"type\":\"function\"}";
	private static final String SC_OPSHR_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_one\"," +
			"\"type\":\"uint256\"},{\"name\":\"_two\",\"type\":\"uint256\"}],\"name\":\"opShr\"," +
			"\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"pure\"," +
			"\"type\":\"function\"}";
	private static final String SC_OPSAR_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_one\"," +
			"\"type\":\"uint256\"},{\"name\":\"_two\",\"type\":\"uint256\"}],\"name\":\"opSar\"," +
			"\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"pure\"," +
			"\"type\":\"function\"}";
	private static final String SC_OPEXTCODEHASH_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_addr\"," +
			"\"type\":\"address\"}],\"name\":\"opExtCodeHash\",\"outputs\":[{\"name\":\"_resp\",\"type\":\"bytes32\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static final String GET_MY_VALUE_ABI =
			"{\"constant\":true,\"inputs\":[],\"name\":\"getMyValue\"," +
					"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}]," +
					"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}\n";
	public static final String GROW_CHILD_ABI =
			"{\"constant\":false," +
					"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_childId\",\"type\":\"uint256\"}," +
					"{\"internalType\":\"uint256\",\"name\":\"_howManyKB\",\"type\":\"uint256\"}," +
					"{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}]," +
					"\"name\":\"growChild\"," +
					"\"outputs\":[]," +
					"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}\n";

	public static byte[] encodeVia(String abi, Object... args) {
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(abi);
		return function.encode(args);
	}

	public static <T> T decodeSimpleResponseVia(String abi, byte[] response, Class<T> responseType) {
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(abi);
		Object[] results = function.decodeResult(response);
		return responseType.cast(results[0]);
	}

	/*
	Functions for simpleStorage.bin: set and get the value
	 */
	public static byte[] encodeSet(int valueToAdd) {
		String retVal = "";
		CallTransaction.Function function = getSetFunction();
		byte[] encodedFunc = function.encode(valueToAdd);

		return encodedFunc;
	}

	public static CallTransaction.Function getSetFunction() {
		String funcJson = SC_SET_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeGetValue() {
		String retVal = "";
		CallTransaction.Function function = getGetValueFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getGetValueFunction() {
		String funcJson = SC_GET_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeGetValueResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getGetValueFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Functions for MapStorage.bin: put and get the value
	 */
	public static byte[] encodeMapPut(int key, int valueToAdd) {
		String retVal = "";
		CallTransaction.Function function = getMapPutFunction();
		byte[] encodedFunc = function.encode(key, valueToAdd);

		return encodedFunc;
	}

	public static CallTransaction.Function getMapPutFunction() {
		String funcJson = SC_MAP_PUT_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeMapGet(int key) {
		String retVal = "";
		CallTransaction.Function function = getMapGetFunction();
		byte[] encodedFunc = function.encode(key);
		return encodedFunc;
	}

	public static CallTransaction.Function getMapGetFunction() {
		String funcJson = SC_MAP_GET_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeMapGetResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getMapGetFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Functions for PayTest.bin: deposit, getBalance
	 */
	public static byte[] encodeDeposit(long valueToDeposit) {
		String retVal = "";
		CallTransaction.Function function = getDepositFunction();
		byte[] encodedFunc = function.encode(valueToDeposit);

		return encodedFunc;
	}

	public static CallTransaction.Function getDepositFunction() {
		String funcJson = SC_DEPOSIT.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeGetBalance() {
		String retVal = "";
		CallTransaction.Function function = getGetBalanceFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getGetBalanceFunction() {
		String funcJson = SC_GET_BALANCE.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeGetBalanceResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getGetBalanceFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Functions for PayTest.bin: sendFunds, getValueOf
	 */
	public static byte[] encodeSendFunds(String address, int amount) {
		String retVal = "";
		CallTransaction.Function function = getSendFundsFunction();
		byte[] encodedFunc = function.encode(address, amount);
		return encodedFunc;
	}

	public static CallTransaction.Function getSendFundsFunction() {
		String funcJson = SC_SEND_FUNDS.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeGetBalanceOf(String address) {
		String retVal = "";
		CallTransaction.Function function = getGetBalanceOfFunction();
		byte[] encodedFunc = function.encode(address);
		return encodedFunc;
	}

	public static CallTransaction.Function getGetBalanceOfFunction() {
		String funcJson = SC_GET_BALANCE_OF.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static long decodeGetBalanceOfResult(byte[] value) {
		long decodedReturnedValue = 0;
		CallTransaction.Function function = getGetBalanceFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.longValue();
		}
		return decodedReturnedValue;
	}

	/*
	Functions for CreateTrivial.bin: create, get
	 */
	public static byte[] encodeCreateTrivialCreate() {
		CallTransaction.Function function = getCreateTrivialCreateFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getCreateTrivialCreateFunction() {
		String funcJson = SC_CT_CREATE_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] encodeCreateTrivialGetIndirect() {
		CallTransaction.Function function = getCreateTrivialGetIndirectFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getCreateTrivialGetIndirectFunction() {
		String funcJson = SC_CT_GETINDIRECT_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeCreateTrivialGetResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getCreateTrivialGetIndirectFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	public static byte[] encodeCreateTrivialGetAddress() {
		CallTransaction.Function function = getCreateTrivialGetAddressFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getCreateTrivialGetAddressFunction() {
		String funcJson = SC_CT_GETADDRESS_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] decodeCreateTrivialGetAddress(byte[] value) {
		byte[] retVal = new byte[0];
		CallTransaction.Function function = getCreateTrivialGetAddressFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			retVal = (byte[]) retResults[0];
		}
		return retVal;
	}


	/*
	Functions for opShl
	 */
	public static byte[] encodeOpShl(int one, int two) {
		CallTransaction.Function function = getOpShlFunction();
		byte[] encodedFunc = function.encode(one, two);
		return encodedFunc;
	}

	public static CallTransaction.Function getOpShlFunction() {
		String funcJson = SC_OPSHL_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeOpShl(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getOpShlFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Functions for opShr
	 */
	public static byte[] encodeOpShr(int one, int two) {
		CallTransaction.Function function = getOpShrFunction();
		byte[] encodedFunc = function.encode(one, two);
		return encodedFunc;
	}

	public static CallTransaction.Function getOpShrFunction() {
		String funcJson = SC_OPSHR_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeOpShr(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getOpShrFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Functions for opSar
	 */
	public static byte[] encodeOpSar(int one, int two) {
		CallTransaction.Function function = getOpSarFunction();
		byte[] encodedFunc = function.encode(one, two);
		return encodedFunc;
	}

	public static CallTransaction.Function getOpSarFunction() {
		String funcJson = SC_OPSAR_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeOpSar(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getOpSarFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Functions for opExtCodeHash
	 */
	public static byte[] encodeOpExtCodeHash(String addr) {
		CallTransaction.Function function = getOpExtCodeHashFunction();
		byte[] encodedFunc = function.encode(addr);
		return encodedFunc;
	}

	public static CallTransaction.Function getOpExtCodeHashFunction() {
		String funcJson = SC_OPEXTCODEHASH_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static byte[] decodeOpExtCodeHash(byte[] value) {
		byte[] retVal = new byte[0];
		CallTransaction.Function function = getOpExtCodeHashFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			retVal = (byte[]) retResults[0];
		}
		return retVal;
	}
}
