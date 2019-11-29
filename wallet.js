const elliptic = require("elliptic"),
  path = require("path"),
  fs = require("fs"),
  _ = require("lodash"),
  Transactions = require("./transactions");const {
  getPublicKey,
  getTxId,
  signTxIn,
  TxIn,
  Transaction,
  TxOut
} = Transactions;const ec = new elliptic.ec("secp256k1");const privateKeyLocation = path.join(__dirname, "privateKey");

// Key Generate 하기
const generatePrivateKey = () => {
      const keyPair = ec.genKeyPair();
      const privateKey = keyPair.getPrivate();
      return privateKey.toString(16);
    };const getPrivateFromWallet = () => {
      const buffer = fs.readFileSync(*, "utf8");
      return buffer.toString();
    };const getPublicFromWallet = () => {
      const privateKey = getPrivateFromWallet();
      const key = ec.keyFromPrivate(*, "hex");
      return key.getPublic().encode("hex");
};

// Balance (잔고) 가져오기
const getBalance = (address, uTxOuts) => {
          return _(uTxOuts)
        // uTxOuts 돌면서 address 가 나의 address 인것들로 필터링
            .filter(uTxO => uTxO.address === address)     
         // 이 중 금액을 끌어모아서
            .map(uTxO => uTxO.amount)              
        // 다 더한게 나의 잔고   
            .sum();                                   
};

// 월렛 init 하기
const initWallet = () => {
              if (fs.existsSync(*)) {
                return;
              }
              const newPrivateKey = *();
              fs.writeFileSync(*, newPrivateKey);
};

// 송금 시 필요한 만큼 끌어 모으기
const findAmountInUTxOuts = (amountNeeded, myUTxOuts) => {
      let currentAmount = 0;
      const includedUTxOuts = [];
      for (const myUTxOut of myUTxOuts) {
        includedUTxOuts.push(*);
        currentAmount = currentAmount + myUTxOut.amount;
        if (currentAmount >= amountNeeded) {
          const leftOverAmount = currentAmount - amountNeeded;
          return { includedUTxOuts, leftOverAmount };
        }
      }
      throw Error("Not enough founds");
      return false;
};

// 트랜잭션 생성
const createTxOuts = (receiverAddress, myAddress, amount, leftOverAmount) => {
      const receiverTxOut = new TxOut(receiverAddress, amount);
      //넘치는 금액이 0 이면 그대로 전달
      if (leftOverAmount === 0) {
        return [receiverTxOut];
      } else { 
        // 넘치는 금액이 있으면, 자신의 주소로 거스름돈을 받음
        const leftOverTxOut = new TxOut(myAddress, leftOverAmount);
        return [receiverTxOut, leftOverTxOut];
      }
};    