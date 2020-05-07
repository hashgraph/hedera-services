pragma solidity ^0.4.22;
contract createInConstructor {
	Test2 test2;
	constructor() {
        test2 = new Test2();
    }
    function add(int a, int b) returns(int){  //Simply add the two arguments and return
        return a+b;
    }

}

contract Test2 {
	constructor() {
        Test3 test3 = new Test3();
    }
   function substract(int a, int b) returns(int){  //Simply add the two arguments and return
        return a-b;
   }
	
}


contract Test3 {

   function multiply(int a, int b) returns(int){  //Simply add the two arguments and return
        return a*b;
   }
	
}