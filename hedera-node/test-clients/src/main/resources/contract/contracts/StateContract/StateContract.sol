// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

enum Choices {Top, Bottom, Left, Right}

struct ContractStruct {
    uint256 varUint256;
    address varAddress;
    bytes32 varBytes32;
    string varString;
    Choices varContractType;
    uint256[] varUint256Arr;
    string varStringConcat;
}

contract ContractType {
    uint256 public number;

    constructor() {
        number = block.number;
    }
}

contract StateContract {

    bool varBool;

    uint8 varUint8;
    uint16 varUint16;
    uint32 varUint32;
    uint64 varUint64;
    uint128 varUint128;
    uint256 varUint256;

    int8 varInt8;
    int16 varInt16;
    int32 varInt32;
    int64 varInt64;
    int128 varInt128;
    int256 varInt256;

    address varAddress;
    ContractType varContractType;
    bytes32 varBytes32;
    string varString;


    Choices varEnum;

    uint[] varIntArrDataAllocBefore;
    uint[] varIntArrDataAllocAfter;

    string varStringConcat;
    ContractStruct varContractStruct;

    constructor() {}

    function setVarBool(bool newVar) external {
        varBool = newVar;
    }

    function getVarBool() external view returns (bool) {
        return varBool;
    }

    function setVarUint8(uint8 newVar) external {
        varUint8 = newVar;
    }

    function getVarUint8() external view returns (uint8) {
        return varUint8;
    }

    function setVarUint16(uint16 newVar) external {
        varUint16 = newVar;
    }

    function getVarUint16() external view returns (uint16) {
        return varUint16;
    }

    function setVarUint32(uint32 newVar) external {
        varUint32 = newVar;
    }

    function getVarUint32() external view returns (uint32) {
        return varUint32;
    }

    function setVarUint64(uint64 newVar) external {
        varUint64 = newVar;
    }

    function getVarUint64() external view returns (uint64) {
        return varUint64;
    }

    function setVarUint128(uint128 newVar) external {
        varUint128 = newVar;
    }

    function getVarUint128() external view returns (uint128) {
        return varUint128;
    }

    function setVarUint256(uint256 newVar) external {
        varUint256 = newVar;
    }

    function getVarUint256() external view returns (uint256) {
        return varUint256;
    }

    function setVarInt8(int8 newVar) external {
        varInt8 = newVar;
    }

    function getVarInt8() external view returns (int8) {
        return varInt8;
    }

    function setVarInt16(int16 newVar) external {
        varInt16 = newVar;
    }

    function getVarInt16() external view returns (int16) {
        return varInt16;
    }

    function setVarInt32(int32 newVar) external {
        varInt32 = newVar;
    }

    function getVarInt32() external view returns (int32) {
        return varInt32;
    }

    function setVarInt64(int64 newVar) external {
        varInt64 = newVar;
    }

    function getVarInt64() external view returns (int64) {
        return varInt64;
    }

    function setVarInt128(int128 newVar) external {
        varInt128 = newVar;
    }

    function getVarInt128() external view returns (int128) {
        return varInt128;
    }

    function setVarInt256(int256 newVar) external {
        varInt256 = newVar;
    }

    function getVarInt256() external view returns (int256) {
        return varInt256;
    }

    function setVarAddress(address newVar) external {
        varAddress = newVar;
    }

    function getVarAddress() external view returns (address) {
        return varAddress;
    }

    function setVarContractType() external {
        varContractType = new ContractType();
    }

    function getVarContractType() external view returns (ContractType) {
        return varContractType;
    }

    function setVarBytes32(bytes32 newVar) external {
        varBytes32 = newVar;
    }

    function getVarBytes32() external view returns (bytes32) {
        return varBytes32;
    }

    function setVarString(string memory newVar) external {
        varString = newVar;
    }

    function getVarString() external view returns (string memory) {
        return varString;
    }

    function setVarEnum(Choices newVar) external {
        varEnum = newVar;
    }

    function getVarEnum() external view returns (Choices) {
        return varEnum;
    }

    function setVarIntArrDataAlloc(uint[] calldata newVar) external {
        varIntArrDataAllocBefore = newVar;

        uint[] storage localVar = varIntArrDataAllocBefore;
        localVar.pop();
        // pointer to varIntArrDataAllocBefore, so varIntArrDataAllocAfter should pop varIntArrDataAllocBefore as well
        varIntArrDataAllocAfter = localVar;
    }

    function getVarIntArrDataAlloc() external view returns (uint[] memory, uint[] memory) {
        return (varIntArrDataAllocBefore, varIntArrDataAllocAfter);
    }

    function deleteVarIntArrDataAlloc() external {
        delete varIntArrDataAllocBefore;
        delete varIntArrDataAllocAfter;
    }

    function setVarStringConcat(string memory newVar) external {
        varStringConcat = string.concat(varStringConcat, newVar);
    }

    function getVarStringConcat() external view returns (string memory) {
        return varStringConcat;
    }

    function deleteVarStringConcat() external {
        delete varStringConcat;
    }

    function setVarContractStruct(ContractStruct memory newVar) external {
        varContractStruct = newVar;
    }

    function getVarContractStruct() external view returns (ContractStruct memory) {
        return varContractStruct;
    }

    function deleteVarContractStruct() external {
        delete varContractStruct;
    }
}
