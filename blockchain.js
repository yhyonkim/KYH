// library import
const CryptoJS = require("crypto-js"),
  hexToBinary = require("hex-to-binary");

// Block class 선언
class Block {
        constructor(index, hash, previousHash, timestamp, data, difficulty, nonce) {
          this.index = index;
          this.hash = hash; // 이 값을 제외한 모든 값을 직렬화한 뒤 SHA256
          this.previousHash = previousHash;
          this.timestamp = timestamp;
          this.data = data; // TX
          this.difficulty = difficulty;
          this.nonce = nonce;
        }
}

// 블록 생성 시간을 1초로 세팅
const BLOCK_GENERATION_INTERVAL = 1; //비트코인은 600초 (10분)
// 블록 10개 생성시, 난이도 조절하도록 세팅
const DIFFICULTY_ADJUSTMENT_INTERVAL = 10; //비트코인은 2016개 블록

// genesis Block 생성
const genesisBlock = new Block(
        0, //index
        "4d1bff8db689882e2bb4c5236d054d3513ad4f4500caebfb7b14b4531981aa45", //무엇에 대한 hash??
        "", //previousHash
        1569523151, //timestamp
        {}, //genesisTx
        4, //difficulty
        0 //nonce
);
    
// 블록체인 선언 : Block class array
let blockchain = [genesisBlock]; 

// Block 관련 함수 선언
const getTimestamp = () => Math.round(new Date().getTime() / 1000);
const createHash = (index, previousHash, timestamp, data, difficulty, nonce) =>
    CryptoJS.SHA256(
    index + previousHash + timestamp + JSON.stringify(data) + difficulty + nonce
    ).toString();
//가장 최신 블록얻는 함수선언
const getNewestBlock = () => blockchain[blockchain.length - 1];
//블록 체인 전체얻는 함수선언
const getBlockchain = () => blockchain;

const getBlocksHash = block =>
  createHash(
    block.index,
    block.previousHash,
    block.timestamp,
    block.data,
    block.difficulty,
    block.nonce);

const isBlockStructureValid = block => {
    return (
        typeof block.index === "number" &&
        typeof block.hash === "string" &&
        typeof block.previousHash === "string" &&
        typeof block.timestamp === "number" //&&
       // typeof block.data === "object" 
    );
};  

const isTimeStampValid = (newBlock, oldBlock) => {
        return (oldBlock.timestamp - 60 < newBlock.timestamp &&
          newBlock.timestamp - 60 < getTimestamp());
};

const isChainValid = candidateChain => {
    const isGenesisValid = block => {
        return JSON.stringify(block) === JSON.stringify(genesisBlock);
    };
    if (!isGenesisValid(candidateChain[0])) {
        console.log("The candidateChains's genesisBlock is not the same as our genesisBlock");
        return null;
    }
    // candidateChain 를 돌면서 이전 블록과 비교해 검증.
    for (let i = 0; i < candidateChain.length; i++) {
        const currentBlock = candidateChain[i];
        if (i !== 0 && !isBlockValid(currentBlock, candidateChain[i - 1])) {
          return null;
        }
        foreignUTxOuts = processTxs(currentBlock.data,foreignUTxOuts,currentBlock.index);
              
        if (foreignUTxOuts === null) {
          return null;
        }
    }
    return foreignUTxOuts;          
};            
// 블럭 검증하는 함수 선언
const isBlockValid = (candidateBlock, latestBlock) => {
        //TODO : 
        //  블록 내부 구조 확인 (index는 숫자, hash는 문자열 등등)
        //  추가 하려는 블록 index 가 이전 index + 1 이 맞는지 확인
        //  previousHash 값이 실제 이전 블록의 해시 값과 맞는지 확인
        //  현재 블록의 Hash 값이 맞는지 확인
        //  timestamp가 가 현재 시각/이전 블록과 1분 이내 차이 인지 확인
        //  블록 내부 구조 확인 (index는 숫자, hash는 문자열 등등)
        if (!isBlockStructureValid(candidateBlock)) {
            console.log("후보 블록의 구조가 이상합니다.");
            return false;
        }
        //  추가 하려는 블록 index 가 이전 index + 1 이 맞는지 확인
        if (candidateBlock.index != latestBlock.index +  1) {
            console.log("후보 블록의 인덱스가 이상합니다.");
            return false;
        }
        //  previousHash 값이 실제 이전 블록의 해시 값과 맞는지 확인
        if (candidateBlock.previousHash != latestBlock.hash) {
            console.log("후보 블록의 이전 해시값이 실제 최근 블록의 해시값과 다릅니다.");
            return false;
        }  
        //  현재 블록의 Hash 값이 맞는지 확인
        if (candidateBlock.hash != getBlocksHash(candidateBlock)) {
            console.log("후보 블록의 다이제스트와 해시 계산값이 다릅니다.");
            return false;
        }      
        //  timestamp가 가 현재 시각/이전 블록과 1분 이내 차이 인지 확인
        if (!isTimeStampValid(candidateBlock,latestBlock)) {
            console.log("후보 블록의 시간이 올바르지 않습니다.");
            return false;
        }
        return true;
};
// 블럭 추가하는 함수 선언
const addBlockToChain = candidateBlock => {
    if (isBlockValid(candidateBlock, getNewestBlock())) {
        //TODO : Tx 관련 작업
        blockchain.push(candidateBlock);
        return true;
    } 
};
// 난이도 관련 함수 선언    
const calculateNewDifficulty = (newestBlock, blockchain) => {
        //TODO : 새로운 Difficulty 계산
    const lastCalculateBlock = blockchain[blockchain.length - DIFFICULTY_ADJUSTMENT_INTERVAL];
    const timeExpected = BLOCK_GENERATION_INTERVAL * DIFFICULTY_ADJUSTMENT_INTERVAL;
    const timeTaken = newestBlock.timestamp - lastCalculateBlock.timestamp;

    if (timeTaken < timeExpected) { //실제 걸린 시간이 적으면, 난이도를 높여야 함
        console.log("timeTaken : " + timeTaken + ", timeExpected :" + timeExpected +
        " Difficulty + 1 = " + (lastCalculateBlock.difficulty + 1));
        return lastCalculateBlock.difficulty + 1;
    } else if ( timeTaken > timeExpected) { // 실제 걸린 시간이 많으면, 난이도를 낮춰야 함
        console.log("timeTaken : " + timeTaken + ", timeExpected :" + timeExpected +
        " Difficulty - 1 = " + (lastCalculateBlock.difficulty - 1));
        return lastCalculateBlock.difficulty - 1;        
    } else {
        console.log("timeTaken : " + timeTaken + ", timeExpected :" + timeExpected );
        return lastCalculateBlock.difficulty;        
    }
};
// 기존 난이도 쓸지, 새로운 난이도 쓸지
const findDifficulty = () => {
        //TODO : Difficulty 리턴 (새로운 Difficulty 로 계산 할지, 기존 것 쓸 지)
    const newestBlock = getNewestBlock();
    // 새 난이도 쓰는 시점이 되고, genesis block이 아니면
    if ( newestBlock.index % DIFFICULTY_ADJUSTMENT_INTERVAL === 0  &&
        newestBlock.index !== 0 ) {
            return calculateNewDifficulty(newestBlock, getBlockchain());
        } else {
            return newestBlock.difficulty;
        }
};
// PoW 구현 : 난이도만큼 0이 반복되는지 확인하는 함수 선언
const hashMatchesDifficulty = (hash, difficulty = 0) => {
      const hashInBinary = hexToBinary(hash);
      const requiredZeros = "0".repeat(difficulty); 
 //     console.log("Trying difficulty:", difficulty, "with hash", hashInBinary);
      return hashInBinary.startsWith(requiredZeros); 
};
// 채굴 : 넌스값 찾아서, 신규 블록 생성하는 함수 선언
const findBlock = (index, previousHash, timestamp, data, difficulty) => {
        let nonce = 0;
        while (true) {
 //         console.log("Current nonce", nonce);
          const hash = createHash( index,previousHash,timestamp, data, difficulty, nonce );
          if (hashMatchesDifficulty(hash, difficulty)) {
            return new Block(index,hash,previousHash,timestamp,data,difficulty,nonce);
          }
          nonce++;
        }
};
// PoW 구현 : 자동 마이닝 함수
const createNewRawBlock = data => {
    const previousBlock = getNewestBlock();
    const newBlockIndex = previousBlock.index + 1;
    const newTimestamp = getTimestamp();
    const difficulty = findDifficulty();
    const newBlock = findBlock(newBlockIndex, previousBlock.hash, newTimestamp, data, difficulty);
    addBlockToChain(newBlock);
    require("./p2p").broadcastNewBlock();
    return newBlock;
};

const sumDifficulty = anyBlockchain =>
  anyBlockchain
    .map(block => block.difficulty)             // [1, 2, 2, 4,  5  ]
    .map(difficulty => Math.pow(2, difficulty)) // [1, 4, 4, 16, 25 ]
    .reduce((a, b) => a + b);                   // [1+ 4+ 4+ 16+ 25 ] // for로 해도 됨

// 수동 채굴
//findBlock(1, "4d1bff8db689882e2bb4c5236d054d3513ad4f4500caebfb7b14b4531981aa45", getTimestamp(), "", 4);
// 자동 채굴(난이도 고정)
//createNewRawBlock({});

// 자동 채굴 + 난이도 조정함수
const createNewBlock = () => {
    console.log("Mining !", getNewestBlock().index + 1);
    return createNewRawBlock();
};

// 
const replaceChain = candidateChain => {
        //TODO : TX 관련 코드
        if (
          isChainValid(candidateChain) &&
          sumDifficulty(candidateChain) > sumDifficulty(getBlockchain())
        ) {
          blockchain = candidateChain;
          //TODO : TX 관련 업데이트
          require("./p2p").broadcastNewBlock();
          return true;
        } else {
          return false;
        }
};
    
// 자동 채굴 + 자동 난이도 조정
while(true) {
    createNewBlock({});
}
module.exports = {
         getNewestBlock,
         getBlockchain,
         isBlockStructureValid,
         addBlockToChain,
         replaceChain,
         createNewBlock
        //getAccountBalance,  TODO : Wallet
        //sendTx,             TODO : Tx
        //handleIncomingTx,   TODO : Tx
        //getUTxOutList       TODO : Tx
};