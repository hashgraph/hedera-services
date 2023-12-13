pragma solidity ^0.8.9;

// standard Zeppelin Ownable modified for Plantidote LLC - ie no owner xfer and no owner relinquish.

contract Ownable {
  address public owner;


    event OwnershipRenounced(address indexed previousOwner);
    event OwnershipTransferred(
    address indexed previousOwner,
    address indexed newOwner
  );


  /**
   * @dev The Ownable constructor sets the original `owner` of the contract to the sender
   * account.
   */
  constructor() {
    owner = msg.sender;
  }

  /**
   * @dev Throws if called by any account other than the owner.
   */
  modifier onlyOwner() {
    require(msg.sender == owner);
    _;
  }

  /**
   * @dev Allows the current owner to transfer control of the contract to a newOwner.
   * @param newOwner The address to transfer ownership to.
   *
   * Disabled for Plantidote LLC - A profile owner cannot xfer his profile to another Hedera Account ie a non plantidote Account
   * This is especially true IF the Account hold is KYC approved (for rewards of course).

  function transferOwnership(address newOwner) public onlyOwner {
    require(newOwner != address(0));
    emit OwnershipTransferred(owner, newOwner);
    owner = newOwner;
  }
  */

  /**
   * @dev Allows the current owner ONLY to relinquish control of the contract but this DOES NOT delete it from Hedera - only suicide func does.
   * not permitted for Plantidote - Account must be deletable only by owner and to not be able to exist with no-control.

  function renounceOwnership() public onlyOwner {
    emit OwnershipRenounced(owner);
    owner = address(0);
  }
  */

}

contract plantaccountprofile is Ownable {
  using SafeMath for uint256;

// Plantidote LLC 10/8/2021.
// owner set via ownable constructor to deployer - which is the created new key paired Client Account for the DApp.
// Plantidote LLC has Customers of various Actor types(rolecode) & they own their own data and data control preferences.
// Consumer, Dispenser, Broker, Grower, Processor - TBD/ refined by C-levels as required.
// Actors can be one or multiple types at any given/same time during their lifecycle.
// Owners can enter a KYC process ie if platform offers rewards for a Gov/central photo id verification process. etc. Driv lic/passport.

string private fname;
string private lname;
string private nickname;
string private phone;
string private nationality;
string private rolecode;

// rolecode permitted values C/D/B/G/P  Consumer - Dispenser - Broker - Grower - Processor

string private plantaccountfileid;    // this is the PLANT account# in Customer terms (holds account/keys,pwrdhash,profile sc id, Circle subaccount/keys)
address private planthhaccountid;     // this is the Hedera accountid that holds HBARs and holds PLANT tokens(TBD after MvP)

//string private circlewalletid; //  circle is moot now.
//uint256 private usdcbal;   // TBD - Circle mirror balance - updated sate after each circle call if needed.


// rolecode ids specific attributions - reminder - can be many Actors at the same time.
uint256 private consumeridnum;
uint256 private dispenseridnum;
uint256 private brokeridnum;
uint256 private processoridnum;
uint256 private groweridnum;
// same for grower/processor/broker .. can concat into the accountid later on.

string private dataipfshash1;  // can optimise string to bytes later on for min gas costs(marginal)
string private dataipfshash2;
string private dataipfshash3;  // used for ipfs link to KYC phot id image - to be encrypted of course prior to ipfs put.

address private platformaddress; // used to ensure that only the signed keypair of the Plantidote LLC is authorized.

// demographics, behavioral, interests - sample TBD - optional profile data can be offered by Account holder.

// Likes and interests - PoC small interest selection for demo purposes. 'categories to choose from or indexes'.

bool public kycapproved;                  //  set true or false - after 3rd praty plugin/ or in App KYC - driv lic pic /passport imagery or other TBD
bool public platformaccess_corepermission;     // switch if Customer permits platform to be able to view their profile or not.
bool public platformaccess_noncorepermission;     // switch if Customer permits platform to be able to view their profile or not.

string private interest1;
string private interest2;
string private interest3;

// the following are public so platform's advertisers(sponsors) can see the openness OR not of the Owner, as permissions for the platform and Sponsors to
// see the data owners decisions ie so Data owner can get PLANT token or USDC rewards
// - but only Contract OnlyOwner ie the profile owner can update.

bool public demographic;
bool public behavioral;
bool public interests;

uint256 public sponsorslevel;
uint256 public grpsponsorslevel;

  constructor(
    string memory _fname, 
    string memory _lname, 
    string memory _nickname, 
    string memory _phone, 
    string memory _nationality, 
    string memory _rolecode, 
    string memory _plantaccountfileid, 
    address _planthhacountid, 
    string memory _dataipfshash1, 
    string memory _dataipfshash2, 
    string memory _dataipfshash3, 
    address _platformaddress, 
    bool _platformaccess_corepermission, 
    bool _platformaccess_noncorepermission
  ) {

// Plant hhaccountid is the hedera public key/assigned Account assigned at time of onboarding.

    fname = _fname;
    lname = _lname;
    nickname = _nickname;
    phone = _phone;
    nationality = _nationality;
    rolecode = _rolecode;

    plantaccountfileid = _plantaccountfileid;
    planthhaccountid = _planthhacountid;

    //circlewalletid = _circlewalletid;  moot as of 10/12
    //usdcbal = 0; //  will probably be a PLANT token - of 1USD value in HBAR terms. TBD

    dataipfshash1 = _dataipfshash1;
    dataipfshash2 = _dataipfshash2;
    dataipfshash3 = _dataipfshash3;

    kycapproved = false;  // later function can only enable this.
    platformaddress = _platformaddress;
    platformaccess_corepermission = _platformaccess_corepermission;// on create.. can be set by User
    platformaccess_noncorepermission = _platformaccess_noncorepermission; // on create.. can be set by User


  }


// Events broadcast to ledger as public but anonymous receipt, if needs be. see notes to detect Events on Hedera.
// more to add


 event Profilecreated(
    address smartcontractid
    );

 event Profileupdated(
   address plantaccountfileid
    );

  event Interestsupdated(
    address plantaccountfileid
     );

  event Opennessupdated(
   address plantaccountfileid
    );




// custom Modifiers for Platform updates e.g. when fileID created - this needs to be inserted into this SC

  modifier onlyplant {
    require(msg.sender == platformaddress);
    _;
  }

// custom Modifiers for Owner OR Platform for profile data for DApp operations e.g. rolecode !

  modifier onlyOwnerorplant {
    require((msg.sender == owner) || (msg.sender == platformaddress));
    _;
  }


// custom Modifier for Owner OR Platform for core profile data ie names/nickname - with permission

  modifier onlyOwnerorplantcore {
    require((msg.sender == owner) || (msg.sender == platformaddress && platformaccess_corepermission));
    _;
  }


// custom Modifier for Owner OR Platform for non-core profile data. ie interests and Advertiser (sponsors) exposure settings/rating

  modifier onlyOwnerorplantnoncore {
    require((msg.sender == owner) || (msg.sender == platformaddress && platformaccess_noncorepermission));
    _;
  }





    // getters ..

    // getters for Owner only unless access permission given to Plantidote to see (for later rewards of course)

  function getfname() view onlyOwnerorplantcore public returns(string memory) {

    return fname;
  }


  function getlname() view onlyOwnerorplantcore public returns(string memory) {

    return lname;
  }

  function getnickname() view onlyOwnerorplantcore public returns(string memory) {

    return nickname;
  }


  function getphone() view onlyOwnerorplantcore public returns(string memory) {

    return phone;
  }


  function getnationality() view onlyOwnerorplantcore public returns(string memory) {

    return nationality;
  }


   // platform and Owner can see the role codes, circle walletid- needed by DApp to custom the dashboard

  function getrolecode() view onlyOwnerorplant public returns(string memory) {

    return rolecode;
  }

// mooted.
/*  function getcirclewalletid() view onlyOwnerorplant public returns(string memory) {

    return circlewalletid;
  }
*/

   function getplatformaccess_corepermission() view onlyOwnerorplant public returns(bool) {

     return platformaccess_corepermission;
  }

   function getplatformaccess_noncorepermission() view onlyOwnerorplant public returns(bool) {

     return platformaccess_noncorepermission;
  }

  function getconsumeridnum() view onlyOwnerorplant public returns(uint256) {

     return consumeridnum;
  }

  function getdispenseridnum() view onlyOwnerorplant public returns(uint256) {

     return dispenseridnum;
  }

  function getbrokeridnum() view onlyOwnerorplant public returns(uint256) {

     return brokeridnum;
  }

  function getprocessoridnum() view onlyOwnerorplant public returns(uint256) {

     return processoridnum;
  }

  function getgroweridnum() view onlyOwnerorplant public returns(uint256) {

     return groweridnum;
  }

  function getkycapproved() view onlyOwnerorplant public returns(bool) {

     return kycapproved;
  }


  // in case Customer forgets their Plantidote account fileid or HBAR account. ie the hedera fileid holding their planthhaccountid.

  function getplantaccountfileid() view onlyOwnerorplant public returns(string memory) {

    return plantaccountfileid;
  }

  function getplanthhaccountid() view onlyOwnerorplant public returns(address) {

    return planthhaccountid;
  }



//  picture id - gov/ driv lic/ passport ipfs hash1 is for KYC process and thus owner and platform

  function getdataipfshash1() view onlyOwnerorplant public returns(string memory) {

    return dataipfshash1;
  }

  function getdataipfshash2() view onlyOwnerorplant public returns(string memory) {

    return dataipfshash2;
  }

  function getdataipfshash3() view onlyOwnerorplant public returns(string memory) {

    return dataipfshash3;
  }



// only owner can view AND update these

  function getinterest1() view onlyOwnerorplantnoncore  public returns(string memory) {

    return interest1;
  }


  function getinterest2() view onlyOwnerorplantnoncore  public returns(string memory) {

    return interest2;
  }


  function getinterest3() view onlyOwnerorplantnoncore  public returns(string memory) {

    return interest3;
  }


// openness bools and slider measures to Advertisers/ sponsors


 function getdemographic() view onlyOwnerorplantnoncore  public returns(bool) {

    return demographic;
  }


  function getbehavioral() view onlyOwnerorplantnoncore  public returns(bool) {

    return behavioral;
  }


  function getinterests() view onlyOwnerorplantnoncore  public returns(bool) {

    return interests;
  }


  function getsponsorslevel() view onlyOwnerorplantnoncore  public returns(uint256) {

    return sponsorslevel;
  }


  function getgrpsponsorslevel() view onlyOwnerorplantnoncore  public returns(uint256) {

    return grpsponsorslevel;
  }





//  setters



    // function to update permission flag for core data - access for platform, only Owner can give permissions

    function setplatformcorepermission(bool _permissiongiven) public onlyOwner{

        platformaccess_corepermission = _permissiongiven;
    }


    // function to update permission flag for non-core data - access for platform.

    function setplatformnoncorepermission(bool _permissiongiven) public onlyOwner{

        platformaccess_noncorepermission = _permissiongiven;
    }




 // update core profile by DApp customer ONLY ie OnlyOwner.  Account info is not updatable by customer of course.
 // Owner cannot update the KYC images - ie ipfs has1, for example. - TBD if once KYC'd can it be updated ? or profile SHOULD be
 // suicided by Owner then re-created and go through new KYC process as if a new Account.

  function updateprofile (
    string memory _fname, 
    string memory _lname, 
    string memory _nickname, 
    string memory _phone, 
    string memory _nationality, 
    string memory _rolecode
  )  public  onlyOwner{
    fname = _fname;
    lname = _lname;
    nickname = _nickname;
    phone = _phone;
    nationality = _nationality;
    rolecode = _rolecode;

  }



 // used to update the profile because the profile SC contractID is stored in the Plantidote Account ie the fileID plantaccountfileid. But
 // the profile also is to hold the Plantidote Users account (hedera fileid).. chicken & egg. So this method below is called AFTER the FileIDcreate in the DApp

  function updateplanttaccountid(string memory _createplantaccountfileid) public onlyplant {

  plantaccountfileid = _createplantaccountfileid;

  }


  // non KYC'd  - at this point only platform can update.

  function updateipfshash_nonkyc(string memory _ipfshash2, string memory _ipfshash3) public onlyplant {

      dataipfshash2 = _ipfshash2;
      dataipfshash3 = _ipfshash3;

  }

  function updateinterests(
    string memory _interest1, 
    string memory _interest2, 
    string memory _interest3, 
    bool _demo, 
    bool _behav, 
    bool _inter, 
    bool _platformaccess_corepermission, 
    bool _platformaccess_noncorepermission, 
    uint256 _sponsorslevel, 
    uint256 _grpsponsorslevel
  ) public onlyOwner {
      interest1 = _interest1;
      interest2 = _interest2;
      interest3 = _interest3;

      demographic = _demo;
      behavioral = _behav;
      interests = _inter;

      platformaccess_corepermission = _platformaccess_corepermission;
      platformaccess_noncorepermission = _platformaccess_noncorepermission;

      sponsorslevel = _sponsorslevel;
      grpsponsorslevel = _grpsponsorslevel;
  }


  function removeprofile() public onlyOwner {

    // selfdestruct profile smart contract by the Customer-Owner of this profile ONLY.

    selfdestruct(payable(owner));  // test on hedera to see if the scVM impklements as orig suicide func.

 }
}

library SafeMath {

  /**
  * @dev Multiplies two numbers, throws on overflow.
  */
  function mul(uint256 a, uint256 b) internal pure returns (uint256) {
    if (a == 0) {
      return 0;
    }
    uint256 c = a * b;
    assert(c / a == b);
    return c;
  }

  /**
  * @dev Integer division of two numbers, truncating the quotient.
  */
  function div(uint256 a, uint256 b) internal pure returns (uint256) {
    // assert(b > 0); // Solidity automatically throws when dividing by 0
    uint256 c = a / b;
    // assert(a == b * c + a % b); // There is no case in which this doesn't hold
    return c;
  }

  /**
  * @dev Subtracts two numbers, throws on overflow (i.e. if subtrahend is greater than minuend).
  */
  function sub(uint256 a, uint256 b) internal pure returns (uint256) {
    assert(b <= a);
    return a - b;
  }

  /**
  * @dev Adds two numbers, throws on overflow.
  */
  function add(uint256 a, uint256 b) internal pure returns (uint256) {
    uint256 c = a + b;
    assert(c >= a);
    return c;
  }
}
