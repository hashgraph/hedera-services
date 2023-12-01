pragma solidity ^0.5.0;

contract LandRegistry {

    address payable theQueen;

    address[100001] public sold;  // sold[propertyID] -> buyer address
    mapping (address => uint24) public bought;  // bought[address] -> propertyID
    uint256[3] allprices = [25000000,50000000,100000000];
    uint256 public highestSold;

    constructor()  public {
        theQueen = msg.sender;
        highestSold = 0;
    }

    function buyProperty(address buyer, uint24 propertyID, uint256 amount ) payable public  returns  (string memory) {

        // recreate the message hash that was signed in JAVA.
//        bytes32 hash = keccak256(mypack(propertyID, amount, x, y));
        // Verify that Nik signed it
//        address signer = ecrecover(hash, v, r, s);
//        require(signer == address(0x9D736080738d67218991bDaFF3fe567b2A61CA68),"NOK|This is not signed by us");
        require(buyer == msg.sender, "NOK|The sender parameter and the signature sender need to be the same");
        require(amount == msg.value, "NOK|The amount transferred and the amount attached to transaction must match");
        require(sold[propertyID] == address(0) ,"NOK|This property is gone"); // this property was already bought;
        require(bought[buyer] == 0, "NOK|You have bought one property already"); // this buyer already bought a property
        require(allprices[propertyID % 3] == amount, "NOK|The price isn't right");

        // otherwise buy the property

        sold[propertyID] = buyer;
        bought[buyer] = propertyID;
        if (highestSold < propertyID)
            highestSold = propertyID;

        return "OK|";
    }

//    function mypack( uint24 propertyID, uint256 amount, uint16 x, uint16 y ) public pure  returns (bytes) {
//        return abi.encodePacked(propertyID, amount, x, y);
//    }

//    function getInt () public pure returns (uint) {
//        return 42;
//    }

    function getContractBalance() public view returns (uint256){
        return address(this).balance;
    }

    function addFundsToContract(uint256 ammount) public payable {
        require(ammount == msg.value, "NOK|param and val must be same");
    }

    function transferFundsToQueen(uint256 ammount) public payable {
        require(msg.sender == theQueen, "NOK|Only queen can withraw from contract");
        require(address(this).balance >= ammount,"NOK|Contract does not have the required amount");
        theQueen.transfer(ammount);
    }

    function getQueen ()  view external returns (address){
        return theQueen;
    }
}