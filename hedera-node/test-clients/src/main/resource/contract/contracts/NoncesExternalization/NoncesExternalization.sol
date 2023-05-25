// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract ChildContract {
    constructor()  {}
}

contract ParentContract {
    constructor()  {}

    function deployChildContract() external returns(address) {
        return address(new ChildContract());
    }

}

contract NoncesExternalization {
    address[] deployedParentContracts;
    address[] deployedChildContracts;

    /** Initially deploys three new ParentContracts, nonce of NoncesExternalization must be 3 */
    constructor() {
        deployedParentContracts.push(address(new ParentContract()));
        deployedParentContracts.push(address(new ParentContract()));
        deployedParentContracts.push(address(new ParentContract()));
    }

    /** For every call of deployParentContract the nonce of NoncesExternalization must be incremented by 1 */
    function deployParentContract() external {
        address parentContract = address(new ParentContract());
        deployedParentContracts.push(parentContract);
    }

    /** For every call of deployChildFromParentContract the nonce
        of the corresponding parent address must be incremented by 1  */
    function deployChildFromParentContract(uint256 _index) external {
        ParentContract parentContract = ParentContract(deployedParentContracts[_index]);
        address childContract = address(parentContract.deployChildContract());
        deployedChildContracts.push(childContract);
    }

    /**
        Log functions
    */

    function getParentContractsByIndex(uint256 _index) public view returns(address) {
        return address(deployedParentContracts[_index]);
    }

    function getChildContractsByIndex(uint256 _index) public view returns(address) {
        return address(deployedChildContracts[_index]);
    }

    function getParentContractsSize() external view returns(uint256) {
        return deployedParentContracts.length;
    }

    function getChildContractsSize() external view returns(uint256) {
        return deployedChildContracts.length;
    }

}