import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * * The TCP server listens for TCP data on port 50000 + boundPeer.getUdpPort()
 */
public class TcpServer implements Runnable {
    private cdht boundPeer;
    private ServerSocketChannel tcpServer;
    private ExecutorService threadManager;
    private boolean state;

    public TcpServer(cdht boundPeer) {
        this.boundPeer = boundPeer;
        threadManager = Executors.newFixedThreadPool(5);
        state = true;
        try {
            tcpServer = ServerSocketChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
        bindServer();
    }

    @Override
    /**
     * Runs the server in non-blocking mode and creates worker threads to handle requests
     */
    public void run() {
        try {
            while (state) {
                SocketChannel socketChannel = null;
                socketChannel = tcpServer.accept();
                if (socketChannel == null) continue;
                //at this point we have a connection
                threadManager.execute(new TcpReceiver(socketChannel));
            }
            tcpServer.close();
            threadManager.shutdown();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Stops server execution. The thread will shut down after all worker tasks have finished
     */
    public void close() {
        state = false;
    }

    /**
     * Binds the server to the port provided by boundPeer
     */
    private void bindServer() {
        try {
            tcpServer.bind(new InetSocketAddress("localhost", boundPeer.getTcpPort()));
            //we will set it to non-blocking
            tcpServer.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(byte[] data, SocketAddress reveiverAddress) {
        ByteBuffer d = (ByteBuffer) (ByteBuffer.allocate(data.length)).put(data).flip();
        try {
            SocketChannel socketChannel = SocketChannel.open();
            threadManager.execute(new TcpSender(socketChannel, reveiverAddress, d));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private class TcpReceiver implements Runnable {
        SocketChannel socketChannel;
        ByteBuffer storeBuffer;

        public TcpReceiver(SocketChannel incommingConnection) {
            socketChannel = incommingConnection;
            storeBuffer = ByteBuffer.allocate(MessageFormatter.MAX_TCP_SIZE);
        }

        @Override
        public void run() {
            try {
                int bytesread = socketChannel.read(storeBuffer);
                storeBuffer.flip();
                byte[] request = new byte[MessageFormatter.MAX_TCP_SIZE];
                int i = 0;
                while (storeBuffer.hasRemaining()) {
                    request[i++] = storeBuffer.get();
                }

                //for now lets just act as an echo server
                if (MessageFormatter.isDepartingMessage(request)) {
                    ArrayList<Integer> decodedMessage = MessageFormatter.decodeDepartingMessage(request);
                    boundPeer.peerTracker.registerGracefulDepart(decodedMessage.get(0),
                            decodedMessage.subList(1,decodedMessage.size()));
                } else if (MessageFormatter.isSuccessorRequest(request)) {
                    byte[] response = MessageFormatter.encodeSuccessorResponse(boundPeer.ID,
                            boundPeer.peerTracker.getSuccessors());
                    InetSocketAddress address = new InetSocketAddress("localhost",
                            cdht.PORT_BASE + MessageFormatter.determineTcpPeer(request));
                    send(response, address);
                } else if (MessageFormatter.isSuccessorResponse(request)) {
//                    System.out.println("Successor response received - " + new String(request));
                    ArrayList<Integer> decodedMessage = MessageFormatter.decodeSuccessorResponse(request);
                    boundPeer.peerTracker.registerSuccessorResponse(decodedMessage.get(0),
                            decodedMessage.subList(1,decodedMessage.size()));
                } else {
                    //act as an echo server
                    ByteBuffer response = ByteBuffer.allocate(MessageFormatter.MAX_TCP_SIZE);
                    response.put(request);
                    response.flip();

                    while (response.hasRemaining()) {
                        socketChannel.write(response);
                    }
                }
                socketChannel.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class TcpSender implements Runnable {
        SocketChannel socketChannel;
        SocketAddress socketAddress;
        ByteBuffer data;

        public TcpSender(SocketChannel socketChannel, SocketAddress socketAddress, ByteBuffer data) {
            this.data = data;
            this.socketChannel = socketChannel;
            this.socketAddress = socketAddress;
        }

        @Override
        public void run() {
            try {
                socketChannel.configureBlocking(true);
                socketChannel.connect(socketAddress);
                socketChannel.write(data);
                socketChannel.close();
            } catch (ConnectException ex) {
                //the peer is most likely dead
                InetSocketAddress address = (InetSocketAddress) socketAddress;
                boundPeer.peerTracker.registerDeathDetection(MessageFormatter.determineUdpPeer(address));
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        }
    }

}
