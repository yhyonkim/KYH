const Blockchain = require("./blockchain");
const {
    getNewestBlock,
    isBlockStructureValid,
    replaceChain,
    getBlockchain,
    addBlockToChain,
    //handleIncomingTx // TODO: With Tx
  } = Blockchain;

// 웹 소켓으로 P2P 통신 
const WebSockets = require("ws");
const sockets = [];
const getSockets = () => sockets;
const startP2PServer = server => {
      const wsServer = new WebSockets.Server({ server });
      wsServer.on("connection", ws => {
        initSocketConnection(ws);
      });
      wsServer.on("error", () => {
        console.log("error");
      });
      console.log("lectureChain P2P Server running");
};

//웹 소켓 관련 Request 부분과 Response 부분 작성 
const GET_LATEST = "GET_LATEST";
const GET_ALL = "GET_ALL";
const BLOCKCHAIN_RESPONSE = "BLOCKCHAIN_RESPONSE";

// Message Creators
// 가장 최신 블록 요청
const getLatest = () => {
    return {
      type: GET_LATEST,
      data: null
    };
  };
// 블록 전체 요청
const getAll = () => {
    return {
      type: GET_ALL,
      data: null
    };
  };
// 블록 체인 응답(배열)
const blockchainResponse = data => {
  return {
    type: BLOCKCHAIN_RESPONSE,
    data
  };
};
// Blockchain - P2P 연동 함수 작성
const responseLatest = () => blockchainResponse([getNewestBlock()]);
const responseAll = () => blockchainResponse(getBlockchain());
const sendMessage = (ws, message) => ws.send(JSON.stringify(message));
const sendMessageToAll = message =>
  sockets.forEach(ws => sendMessage(ws, message));
const broadcastNewBlock = () => sendMessageToAll(responseLatest());

/*
함수 명세
함수명 : handleBlockchainResponse
매개 변수 : receivedBlocks
함수 내용 : 받은 블록체인을 검수하고 블록을 추가하거나 체인을 교체 함.
*/
const handleBlockchainResponse = receivedBlocks => {
    // receivedBlocks.length 가 0이면 아무것도 하지 않고 리턴.
    if ( receivedBlocks.length === 0 ) {
        console.log("받은 블록체인 길이가 0");
        return;
    }
    // 2 receivedBlocks 배열 중 마지막(최신) 블록을 latestBlockReceived 변수에 대입 한 뒤,  
    // isBlockStructureValid 함수의 매개 변수로 호출해 확인하고 올바르지 않으면 리턴
    const latestBlockReceived = receivedBlocks[receivedBlocks.length - 1];
    if ( !isBlockStructureValid(latestBlockReceived)) {
        console.log("받은 블록체인 구조가 이상함");
        return;
    }
    // 3 getNewestBlock 함수를 사용해 내가 가진 가장 최신 블록을 newestBlock 변수에 대입.
    const newestBlock = getNewestBlock();

    // 4 latestBlockReceived.index > newestBlock.index 가 아니면 리턴
    if ( latestBlockReceived.index > newestBlock.index) {
    // 5 만약 newestBlock.hash === latestBlockReceived.previousHash 즉. 블록이 바로 하나 차이이면 
    // addBlockToChain 함수를 이용해 latestBlockReceived 를 체인에 추가하고, broadcastNewBlock 함수를 호출하여 타 노드에 알리고 리턴
        if ( newestBlock.hash === latestBlockReceived.previousHash ) {
            if ( addBlockToChain(latestBlockReceived)) {
               broadcastNewBlock();        
            }    
    // 6 receivedBlocks 의 길이가 1이면, 다 달라고 요청하고 리턴. sendMessageToAll(getAll());
        } else if ( receivedBlocks.length === 1 ) {
            sendMessageToAll(getAll());
        } else {
    // 7 모두 통과 했으면 receivedBlocks을 replaceChain 함수의 매개변수로 호출 하여 체인 자체를 교체 함.
            replaceChain(receivedBlocks);
        }
    }
};

// 웹 소켓 메시지를 핸들링 하는 함수 
const handleSocketMessages = ws => {
      ws.on("message", data => {
        //try / catch 필요
        const message = JSON.parse(data);
        if (message === null) {
          return;
        }
        // 메시지 타입에 따라 응답
        switch (message.type) {  
          case GET_LATEST:
            sendMessage(ws, responseLatest());
            break;
          case GET_ALL:
            sendMessage(ws, responseAll());
            break;
          case BLOCKCHAIN_RESPONSE:
            const receivedBlocks = message.data;
            if (receivedBlocks === null) {
              break;
            }
            handleBlockchainResponse(receivedBlocks);
            break;
        }
      });
};
// 웹 소켓 에러를 핸들링 하는 부분 작성    
const handleSocketError = ws => {
          const closeSocketConnection = ws => {
            ws.close();
            sockets.splice(sockets.indexOf(ws), 1);
          };
          ws.on("close", () => closeSocketConnection(ws));
          ws.on("error", () => closeSocketConnection(ws));
};

// Websocket Server 초기화 및 관리
const initSocketConnection = ws => {
      sockets.push(ws);
      handleSocketMessages(ws);
      handleSocketError(ws);
      sendMessage(ws, getLatest());
      // TODO : Tx
      setInterval(() => {
        if (sockets.includes(ws)) {
          sendMessage(ws, "");
        }
      }, 1000);
};

// 신규 Peer를 웹 소켓으로 연결하는 부분
const connectToPeers = newPeer => {
      const ws = new WebSockets(newPeer);
      ws.on("open", () => {
        initSocketConnection(ws);
      });
      ws.on("error", () => console.log("Connection failed"));
      ws.on("close", () => console.log("Connection failed"));
};

module.exports = {
      startP2PServer,
      connectToPeers,
      broadcastNewBlock
};
