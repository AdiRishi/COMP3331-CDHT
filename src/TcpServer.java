import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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
        threadManager = Executors.newFixedThreadPool(3);
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
                System.out.println("We have a connection on TCP BOYS");
                threadManager.execute(new TcpWorker(socketChannel));
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
            tcpServer.bind(new InetSocketAddress(boundPeer.getTcpPort()));
            //we will set it to non-blocking
            tcpServer.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private class TcpWorker implements Runnable {
        SocketChannel socketChannel;
        ByteBuffer storeBuffer;

        public TcpWorker(SocketChannel incommingConnection) {
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

                ByteBuffer response = ByteBuffer.allocate(MessageFormatter.MAX_TCP_SIZE);
                response.put(request);
                response.flip();

                while (response.hasRemaining()) {
                    socketChannel.write(response);
                }

                socketChannel.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
