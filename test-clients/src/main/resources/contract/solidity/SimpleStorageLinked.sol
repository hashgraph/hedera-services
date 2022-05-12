pragma solidity ^0.5.3;

contract SimpleStorageLinked {
    uint levels;
    SimpleStorageInner myInnerStorage;
    constructor(uint _levels) public {
        levels = _levels;
        SimpleStorageInner previousElement;
        for (uint i = 1; i <=levels; i++){
            SimpleStorageInner currentStorage = new SimpleStorageInner(i);
            if(i>1){
                previousElement.setNext(currentStorage);
            }else{
                myInnerStorage = currentStorage;
            }
            previousElement = currentStorage;
        }
        
    }
    function set(uint level , uint x) public {
        myInnerStorage.set(level , x);
    }

    function get(uint level) public view returns (uint) {
        return myInnerStorage.get(level);
    }
}

contract SimpleStorageInner {
    uint storedData;
    uint myLevel;
    SimpleStorageInner nextStorageElement;

    constructor(uint _myLevel) public {
        myLevel = _myLevel;

    }
    
    function setNext(SimpleStorageInner _nextStorage) public {
        nextStorageElement = _nextStorage;
    }
    function set(uint level , uint x) public {
        if(level==myLevel){
            storedData = x;
        }else{
            nextStorageElement.set(level,x);
        }
    }

    function get(uint level) public view returns (uint) {
         if(level==myLevel){
              return storedData;
         }else{
             return nextStorageElement.get(level);
         }
       
    }
}