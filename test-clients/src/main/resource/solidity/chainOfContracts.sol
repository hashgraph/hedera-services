pragma solidity 0.5.11;
contract SimpleStorage {
    uint storedData = 15;

    function set(uint x) public {
        storedData = x;
    }

    function get() public view returns (uint _get) {
        return storedData;
    }
}

contract WrapperStorage{
    LargeStorage myStorage;
    constructor() public {
        myStorage =  new LargeStorage();
        myStorage.newStorage();
    }
     //Set and Get form baseline Storage
    function set(uint x) public payable {
        myStorage.set(x);
    }
    function get() public view returns (uint _get) {
        return myStorage.get();
    }
    function getAddress() public view returns (address _myStorage) {
        return myStorage.getAddress();
    }
}
contract LargeStorage {
    SimpleStorage myStorage;
        
    //This works once Storage is set
    function getAddress() public view returns (address _myStorage) {
        return address(myStorage);
    }

    //Casting an address: does not work in Hedera
    function setAddressCase1(address _myStorage) public {
        myStorage = SimpleStorage( _myStorage );
    } 

    //Casting an address referenced as object: does not work in Hedera
    function setStorageCase2(SimpleStorage _myStorage) public {
        myStorage = SimpleStorage(_myStorage);
    } 

    //Direct assignment does not work in Hedera
    function setStorageCase3(SimpleStorage _myStorage) public {
        myStorage = _myStorage;
    } 

    //This works
    function newStorage() public payable {
        myStorage = new SimpleStorage();
    }
    
    //Set and Get form baseline Storage
    function set(uint x) public payable {
        myStorage.set(x);
    }
    function get() public view returns (uint _get) {
        return myStorage.get();
    }

}