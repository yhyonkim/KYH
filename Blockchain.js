// git
const CryptoJS = require("crypto-js"),   // cryptoJS 사용하기 위해 import
    hexToBinary = require("hex-to-binary");

class Block {
    constructor ( index, hash, previousHash, timestamp, data, difficulty, nonce ) {
        this.index = index;  //block no
        this.hash = hash;    //current block hash. 이 값을 제외한 모든 값을 직렬화한 뒤 SHA256
        this.previousHash = previousHash;  
        this.timestamp = timestamp;
        this.data = data;
        this.difficulty = difficulty;
        this.nonce = nonce;
    }
}

const getTimestamp = () => Math.round(new Date().getTime()/1000); //현재 시간 return하는 함수 선언

// current hash 만들어서 return하는 함수 선언
const createHash = ( index, previousHash, timestamp, data, difficulty, nonce) => 
    CryptoJS.SHA256(
        index + previousHash + timestamp + JSON.stringify(data) // data를 JSON format으로 변환
        + difficulty + nonce
    ).toString();

const genesisBlock = new Block(
    0, 
    "4d1bff8db689882e2bb4c5236d054d3513ad4f4500caebfb7b14b4531981aa45",
    "",
    1569523151,
    "", //genesisTx
    0,
    0
);

// blockchain을 첫번째 요소가 genesisBlock인 array로 선언
let blockchain = [genesisBlock];

// 최신 block return
const getNewestBlock = () => blockchain[blockchain.length - 1];

// 왜 필요하지??
const getBlockchain = () => blockchain;

// 
const isBlockValid = (candidateBlock, latestBlock) => {
       //TODO : 
    //  블록 내부 구조 확인 (index는 숫자, hash는 문자열 등등)
    if (!isBlockStructureValid(candidateBlock)) {
        console.log("후보 블록의 구조가 이상합니다.");
        return false;
    }
    //  추가 하려는 블록 index 가 이전 index + 1 이 맞는지 확인
    if (candidateBlock.index != latestBlock.index +  1) {
        console.log("후보 블록의 인덱스가 이상합니다.");
        return false;
    }
    //  previousHash 값이 실제 이전 블록의 해시 값과 맞는지 확인
    if (candidateBlock.previousHash != latestBlock.hash) {
        console.log(
                  "후보 블록의 이전 해시값이 실제 최근 블록의 해시값과 다릅니다."
                  );
        return false;
    }  
    //  현재 블록의 Hash 값이 맞는지 확인
    if (candidateBlock.hash != getBlocksHash(candidateBlock)) {
        console.log("후보 블록의 다이제스트와 해시 계산값이 다릅니다.");
        return false;
    }      
    //  timestamp가 가 현재 시각/이전 블록과 1분 이내 차이 인지 확인
    if (!isTimeStampValid(candidateBlock,latestBlock)) {
        console.log("후보 블록의 시간이 올바르지 않습니다.");
        return false;
    }   
    return true;
};

const getBlocksHash = block =>
  createHash(
    block.index,
    block.previousHash,
    block.timestamp,
    block.data,
    block.difficulty,
    block.nonce);

const isTimeStampValid = (newBlock, oldBlock) => {
        return (oldBlock.timestamp - 60 < newBlock.timestamp &&
          newBlock.timestamp - 60 < getTimestamp());
};

const isBlockStructureValid = block => {
    return (
        typeof block.index === "number" &&
        typeof block.hash === "string" &&
        typeof block.previousHash === "string" &&
        typeof block.timestamp === "number" &&
        typeof block.data === "object" 
    );
};

const addBlockToChain = candidateBlock => {
    if ( isBlockValid(candidateBlock, getNewestBlock())) {
        //TODO : Tx 관련 작업
        blockchain.push(candidateBlock);
        return true;
    }
};

const calculateNewDifficulty = ( newestBlock, blockchain) => {
    //TODO : 새로운 Difficulty 계산
    return 0;
};

// PoW 구현 - 난이도가 맞는지 확인
const hashMatchesDifficulty = (hash, difficulty = 0) => {
        //TODO : 해시가 Difficulty 가 맞는지 확인하기
    const hashInBinary = hexToBinary(hash);  //16진수값을 binary로 변환
    const requiredZeros = "0".repeat(difficulty); // difficulty에 있는 0의 갯수
    console.log("Trying difficulty:", difficulty, "with hash", hashInBinary);
    return hashInBinary.startsWith(requiredZeros); // hash가 difficulty만큼의 0으로 시작하면,(즉 hash가 difficulty를 만족하면) 
                                                    //true return
};

/* PoW 구현 - 블록체인 채굴함수
findBlock 함수를 만들어 봅시다.
1. index, previousHash, timestamp, data, difficulty 를 매개변수로 받는 함수
2. 최초 nonce 를 0으로 설정
3. 아까 만든 createHash 함수를 호출 해 현재 Hash 값 계산
4. 계산한 Hash 값과 받은 difficulty를 hashMatchesDifficulty 함수에 전달 했을 때
5. true 인 경우 - Block 클래스를 생성해서, 리턴.
6. false 인 경우 - nonce를 1 증가시키고 3번으로 돌아가서 무한 반복
*/
const findBlock = ( index, previousHash, timestamp, data, difficulty ) => {
    let nonce = 0;
    while ( true) {
        console.log("current nonce is ", nonce);
        hash = createHash(index, previousHash, timestamp, data, difficulty, nonce);

        if (hashMatchesDifficulty(hash, difficulty)) { 
            return new Block( index, hash, previousHash, timestamp, data, difficulty, nonce);
        }
        nonce++;
    }
};

//PoW 구현 - 수동으로 마이닝 해보자!
//findBlock(1, "4d1bff8db689882e2bb4c5236d054d3513ad4f4500caebfb7b14b4531981aa45",
  //  getTimestamp(), "", 4);

/* [숙제] PoW 구현 - 자동 마이닝 함수
    createNewRawBlock - 신규 블록을 마이닝해 리턴하는 함수를 만들어 봅시다.
1. data 를 매개변수로 받는 함수
2. previousBlock 변수에 getNewestBlock 함수 리턴 값을 대입.
3. newBlock Index 변수에 previousBlock 의 index를 1 증가시켜 대입.
4. newTimestamp 변수에 getTimestamp 함수 리턴 값을 대입.
5. difficulty 변수에 findDifficulty 함수 리턴 값을 대입.
6. newBlock 변수에 findBlock(newBlockIndex, previousBlock.hash, newTimestamp, difficulty) 함수를 호출해 Block을 마이닝 한 것을 대입.
7. addBlockToChain 함수에 newBlock 을 전달해 호출한다.
8. newBlock 을 리턴한다.
*/
const createNewRawBlock = data => {
    const previousBlock = getNewestBlock();
    const newBlockIndex = previousBlock.index + 1;
    const newTimestamp = getTimestamp();
    const difficulty = findeDifficulty();
    const newBlock = findBlock(newBlockIndex, previousBlock.hash, newTimestamp, data, difficulty);
    addBlockToChain(newBlock);
    return newBlock;
};

const isChainValid = candidateChain => {
    const isGenesisValid = block => {
          return JSON.stringify(block) === JSON.stringify(genesisBlock);
    };
    if (!isGenesisValid(candidateChain[0])) {
          console.log("The candidateChains's genesisBlock is not the same as our genesisBlock");
          return null;
    }
};

const BLOCK_GENERATION_INTERVAL = 1;  //비트코인은 600초(10분)이지만, 실습을 위해 1초마다 생성 
const DIFFICULTY_ADJUSTMENT = 10;   //비트코인은 2016개 블록이지만, 실습을 위해 10개마다 난이도 조정

const findeDifficulty = () => {
    const newestBlock = getNewestBlock();
    //TODO : Difficulty 리턴 (새로운 Difficulty 로 계산 할지, 기존 것 쓸 지)
    //Difficulty 가 인터벌로 나누어 지고 제네시스 블록이 아닌 경우
    if ( newestBlock.index % BLOCK_GENERATION_INTERVAL === 0 && newestBlock.index !== 0 ) {
        return calculateNewDifficulty(newestBlock, getBlockchain());
    } else {
        return newestBlock.difficulty;
    }
};

/*
const calculateNewDifficulty = (newestBlock, blockchain) => {
    const lastCalculateBlock = blockchain[blockchain.length - DIFFICULTY_ADJUSTMENT]
}
*/

const createNewBlock = () => {
    console.log("Mining !", getNewestBlock().index +1);
    return createNewRawBlock();
};

while (true) {    
    createNewBlock({});
}
