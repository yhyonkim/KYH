const express = require("express"),
  _ = require("lodash"),
  cors = require("cors"), 
  bodyParser = require("body-parser"), 
  morgan = require("morgan"),  
  Blockchain = require("./blockchain"),
  P2P = require("./p2p");

// 웹서버에서 p2p, blockchain이용 
const {
    getBlockchain,
    createNewBlock
} = Blockchain;
const { startP2PServer, connectToPeers } = P2P;

// 웹서버 설정
//LINUX : export HTTP_PORT=4000, WINDOWS : set HTTP_PORT=4000
const PORT = process.env.HTTP_PORT || 3000;
const app = express();
app.use(bodyParser.json()); // HTTP Request 시 Body 를 파싱, 없어도 구현은 가능하나 매우 불편.
app.use(cors()); // Cross-Origin Resource Sharing : 타 도메인으로 부터 리소스 허용 (타 포트 포함)
app.use(morgan("combined")); // 로깅
app
  .route("/blocks")
  .get((req, res) => {
    res.send(getBlockchain());
  })
  .post((req, res) => {
    const newBlock = createNewBlock();
    res.send(newBlock);
  });

// 웹서버 기동
const server = app.listen(PORT, () =>
  console.log(`LectureChain HTTP Server running on port ${PORT} ✅`)
);//TODO : Wallet
// initWallet();

// P2P서버 기동
startP2PServer(server);

// 다른 peer에 연결
app.post("/peers", (req, res) => {
      const { body: { peer } } = req;
      connectToPeers(peer);
      res.send();
});