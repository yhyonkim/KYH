const CryptoJS = require("crypto-js"),
  elliptic = require("elliptic"),
  _ = require("lodash"),
  utils = require("./utils");
const ec = new elliptic.ec("secp256k1");

//CoinBase 로 부터 오는 보상 금액 (50BTC)
//실제로는 매 210000 블록마다 반으로 줄어듬
const COINBASE_AMOUNT = 50;

//트랜잭션 
class TxOut {
      constructor(address, amount) {
        this.address = address;
        this.amount = amount;
      }
}

class TxIn {
    txOutId string;
    txOutIndex number;
    Signature string;
}

class Transaction {
      ID string;
      txIns[] TxIn;
      txOuts[] TxOut;
}

class UTxOut {
            constructor(txOutId, txOutIndex, address, amount) {
              this.txOutId = txOutId;
              this.txOutIndex = txOutIndex;
              this.address = address;
              this.amount = amount;
            }
}
        
//Transaction ID : 모두 다 해시 한 것.
const getTxId = tx => {
      const txInContent = tx.txIns //txIns 에서 txOutId 와 txOutIndex를 모두 더함
        .map(txIn => txIn.txOutId + txIn.txOutIndex)
        .reduce((a, b) => a + b, "");
      const txOutContent = tx.txOuts //txOuts 에서 주소, amount 를 모두 더함
        .map(txOut => txOut.address + txOut.amount)
        .reduce((a, b) => a + b, "");
      //위에서 더한 것과 timestamp 를 더해서 해시
      return CryptoJS.SHA256(txInContent + txOutContent + tx.timestamp).toString();
};

//사용되지 않은 트랜잭션 리스트에서 txOutId가 같고 txOutIndex 가 같은 것을 찾음.
const findUTxOut = (txOutId, txOutIndex, uTxOutList) => {
      return uTxOutList.find(
        uTxO => uTxO.txOutId === txOutId && uTxO.txOutIndex === txOutIndex
      );
};
    
const getPublicKey = privateKey => {
      return ec
        .keyFromPrivate(privateKey, "hex")
        .getPublic()
        .encode("hex");
};

//트랜잭션의 id 는 전체 해시값임. 이걸 사이닝 할 것임.
const signTxIn = (tx, txInIndex, privateKey, uTxOutList) => {
      const txIn = tx.txIns[txInIndex];
      const dataToSign = tx.*;
      const referencedUTxOut = findUTxOut(
        txIn.*,
        txIn.*,
        *
      );
      if (referencedUTxOut === null || referencedUTxOut === undefined) {
        throw Error("uTxOut 참조를 찾지 못했으므로 사이닝 안함.");
        return;
      }
      const referencedAddress = referencedUTxOut.address;
      if (getPublicKey(referencedAddress) !== referencedAddress) {
        return false;
      }
      const key = ec.keyFromPrivate(*, "hex");
      const signature = utils.toHexString(key.sign(*).toDER());
      return signature;
};
        
const getPublicKey = privateKey => {
      return ec
        .keyFromPrivate(privateKey, "hex")
        .getPublic()
        .encode("hex");
};

// 50 -> 40 을 보낸다면, 50짜리 UTxOut 는 비워 주고 40짜리, 10짜리를 새로 생성해 준다.
const updateUTxOuts = (newTxs, uTxOutList) => {
      //신규 Tx 들에서 UTxOut 모으기
      const newUTxOuts = newTxs
        .map(tx => tx.txOuts
          .map(
            (txOut, index) => new UTxOut(tx.id, index, txOut.address, txOut.amount)
          )).reduce((a, b) => a.concat(b), []);
      //신규 Tx 들에서 사용된(txIn) TxOut들 모으기
      const spentTxOuts = newTxs
        .map(tx => tx.txIns) // 트랜잭션 인풋의 Array
        .reduce((a, b) => a.concat(b), [])
        .map(txIn => new UTxOut(txIn.*, txIn.*, "", 0)); //사용된 것들이므로 비울 것
      //UTxOut 을 가져다가 spentTxOutput 에서 찾을거고, 찾아지면 쓸것이므로 삭제함
      const resultingUTxOuts = uTxOutList
        .filter(uTxO => !findUTxOut(uTxO.*, uTxO.txOut*, *))
        .concat(newUTxOuts);
      return resultingUTxOuts;
};

//txIn 을 가져와서 구조가 올바른지 본다.
const isTxInStructureValid = txIn => {
      if ( txIn === null) {
        console.log("txIn 이 없음");
        return false;
      } else if (typeof  txIn.signature !== "string") {
        console.log("txIn의 signature가 문자열이 아님 ");
        return false;
      } else if (typeof  txIn.txOutId !== "string") {
        console.log("txIn의 txOutId 가 문자열이 아님");
        return false;
      } else if (typeof  txIn.txOutIndex !== "number") {
        console.log("txIn의 txOutIndex 가 숫자가 아님");
        return false;
      } else {
        return true;
      }
};
    
//주소 구조가 올바른지 확인
const isAddressValid = address => {
      if (address.length !== 130) {
            return false; console.log("주소 길이가 130자가 아님");
        return false;
      } else if (address.match("^[a-fA-F0-9]+$") === null) {
        console.log("주소에 이상한 문자가 있음");
        return false;
      } else if (!address.startsWith("04")) {
        console.log("주소가 04로 시작 해야 함");
    
      } else {
        return true;
      }
};

const isTxOutStructureValid = txOut => {
      if (txOut === null) {
        return false;
      } else if (typeof txOut.address !== "string") {
        console.log("txOut의 주소가 문자열이 아님");
        return false;
      } else if (!isAddressValid(txOut.address)) {
        console.log("txOut의 주소가 문자열은 맞긴한데 올바르지 않음.");
        return false;
      } else if (typeof txOut.amount !== "number") {
        console.log("txOut의 금액이 숫자가 아님.");
        return false;
      } else {
        return true;
      }
};
    
const isTxStructureValid = tx => {
      if (typeof tx.id !== "string") {
        console.log("Tx id 가 문자열이 아님");
        return false;
      } else if (!(tx.txIns instanceof Array)) {
        console.log("txIns 가 배열이 아님");
        return false;
      } else if (
        !tx.txIns.map(*).reduce((a, b) => a && b, true)
      ) {
        console.log("txIn 들 중 하나 이상의 구조가 잘못됨");
        return false;
      } else if (!(tx.txOuts instanceof Array)) {
        console.log("txOuts 가 배열이 아님");
        return false;
      } else if (
        !tx.txOuts.map(*).reduce((a, b) => a && b, true)
      ) {
        console.log("txOut 중 하나 이상의 구조가 잘못 됨");
        return false;
      } else {
        return true;
      }
};

const validateTxIn = (txIn, tx, uTxOutList) => {
      const wantedTxOut = uTxOutList.find(
        //uTxOutList 에서 인풋의 txOutId 가 존재하는지.
        //txOutIndex 가 존재하는지.
        uTxO => uTxO.txOutId === txIn.txOutId && uTxO.txOutIndex === txIn.txOutIndex
      );
      if (wantedTxOut === undefined) {
        console.log(`uTxOut를 못 찾았음, tx: ${tx} 가 올바르지 않음.`);
        return false;
      } else {
        const address = wantedTxOut.address;
        //key는 주소를 hex 한 값이다. 
        const key = ec.keyFromPublic(*, "hex");
        //tx.id 와 signature 를 복호화한 값을 비교.
        return key.verify(tx.*, txIn.*);
      }
};
    
//uTxOutList 에서 txIn의 금액을 찾음
const getAmountInTxIn = (txIn, uTxOutList) =>
  findUTxOut(txIn.txOutId, txIn.txOutIndex, uTxOutList).amount;

const validateTx = (tx, uTxOutList) => {
      //tx 구조 검증
      if (!isTxStructureValid(tx)) {
        console.log("Tx 구조가 이상함.");
        return false;
      }
      //실제 해시 계산
      if (getTxId(tx) !== tx.id) {
        console.log("tx 해시한거랑 id 랑 다름.");
        return false;
      }
      const hasValidTxIns = tx.txIns.map(txIn =>
        validateTxIn(txIn, tx, uTxOutList)
      );
      //validTxIns
      if (!hasValidTxIns) {
        console.log(`tx: ${tx} 올바르지 않음.`);
        return false;
      }

      //amountInTxIns
  const amountInTxIns = tx.txIns
    .map(txIn => getAmountInTxIn(txIn, uTxOutList))
    .reduce((a, b) => a + b, 0);
  const amountInTxOuts = tx.txOuts
    .map(txOut => txOut.amount)
    .reduce((a, b) => a + b, 0);
  if (amountInTxIns !== amountInTxOuts) {
    console.log(
      `tx: ${tx} txIns 의 amount가 txOuts 의 amount와 다름`
    );
    return false;
  } else {
    return true;
  }
};

//Coinbase 기반 트랜잭션 생성
const createCoinbaseTx = (address, blockIndex) => {
      const tx = new Transaction();
      const txIn = new TxIn();
      txIn.signature = "";
      txIn.txOutId = "";
      txIn.txOutIndex = blockIndex;
      tx.txIns = [txIn];
      tx.txOuts = [new TxOut(address, COINBASE_AMOUNT)];
      tx.id = getTxId(tx);
      return tx;
};
    
//Coinbase Transaction 검증
const validateCoinbaseTx = (tx, blockIndex) => {
      if (getTxId(tx) !== tx.id) {
        console.log("잘못 계산된 Tx id");
        return false;
      } else if (tx.txIns.length !== 1) {
        console.log("Coinbase TX 는 반드시 하나의 Input 만 있어야 함");
        return false;
      } else if (tx.TxInx[0].txOutIndex !== blockIndex) {
        console.log("첫번째 txIn의 txOutIndex는 Block Index 와 같아야 함.");
        return false;
      } else if (tx.txOuts.length !== 1) {
        console.log("Coinbase TX 는 하나의 Output 만 가져야 함.");
        return false;
      } else if (tx.txOuts[0].amount !== *) {
        console.log(
          `Coinbase TX 은 오직 정해진 양이어야 함. ${COINBASE_AMOUNT} != ${tx.txOuts[0].amount}`
        );
        return false;
      } else {
        return true;
      }
};

//txOutId 랑 txOutIndex를 더해서 배열로-> Identity 이므로 하나씩 밖에 안나옴
//-> 2개 이상 나오면 중복된게 있는것
const hasDuplicates = txIns => {
      const groups = _.countBy(txIns, txIn => txIn.txOutId + txIn.txOutIndex);
      return _(groups)
        .map(value => {
          if (value > 1) {
            console.log("두개 이상의 중복된 txIn");
            return true;
          } else {
            return false;
          }
        })
        .includes(true);
};
    
const validateBlockTxs = (txs, uTxOutList, blockIndex) => {
      const coinbaseTx = txs[0];
      if (!validateCoinbaseTx(coinbaseTx, blockIndex)) {
        console.log("Coinbase Tx 가 이상합니다");
      }
      const txIns = _(txs)
        .map(tx => tx.txIns) 
        .flatten() //tx.txIns 를 가져와서 1차원 배열로 만들어주고
        .value();  //값을 가져옴  //이중지불 체크
      if (hasDuplicates(txIns)) {
        return false;
      }
      const nonCoinbaseTxs = txs.slice(1); //txs 복사  js에서는 refernce되기 때문!
      return nonCoinbaseTxs
        .map(tx => validateTx(tx, uTxOutList))  // 리턴되는게 true / false
        .reduce((a, b) => a && b, true);         // false 만 
};
    
//Tx에 이상이 없는 경우 UTxOuts 업데이트
const processTxs = (txs, uTxOutList, blockIndex) => {
      if (!validateBlockTxs(txs, uTxOutList, blockIndex)) {
        return null;
      }
      return updateUTxOuts(txs, uTxOutList);
};
   
module.exports = {
      getPublicKey,
      getTxId,
      signTxIn,
      TxIn,
      Transaction,
      TxOut,
      createCoinbaseTx,
      processTxs,
      validateTx
};
