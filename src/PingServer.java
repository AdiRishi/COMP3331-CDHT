import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ping server listens for UDP data on port 50000 + boundPeer.getUdpPort()
 *
 * @author Adiswhar Rishi
 */
public class PingServer implements Runnable {
    private DatagramChannel udpserver;
    private boolean state;
    private cdht_ex boundPeer;
    private ExecutorService threadManager;

    /**
     * the boundPeer given is the peer that this server will be attached to
     */
    public PingServer(cdht_ex boundPeer) {
        state = true;
        this.boundPeer = boundPeer;
        threadManager = Executors.newFixedThreadPool(3);
        try {
            udpserver = DatagramChannel.open();
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
        ByteBuffer storeBuffer = ByteBuffer.allocate(MessageFormatter.MAX_PING_SIZE);
        try {
            while (state) {
                InetSocketAddress senderAddress = (InetSocketAddress) udpserver.receive(storeBuffer);
                if (senderAddress == null) continue;
                storeBuffer.flip(); //get buffer ready for read
                //now do something with the data received
                byte[] request = new byte[MessageFormatter.MAX_PING_SIZE];

                int i = 0;
                while (storeBuffer.hasRemaining()) {
                    request[i++] = storeBuffer.get();
                }

                threadManager.execute(new PingWorker(request, senderAddress));

                storeBuffer.clear();
            }
            udpserver.close();
            threadManager.shutdown();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Binds the server to the port provided by boundPeer
     */
    private void bindServer() {
        try {
            udpserver.bind(new InetSocketAddress("localhost", boundPeer.getUdpPort()));
            //we will configure blocking to false
            udpserver.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops server execution. The thread will shut down after all worker tasks have finished
     */
    public void close() {
        state = false;
    }

    /**
     * A worker class that handles any requests received
     */
    private class PingWorker implements Runnable {
        private byte[] request;
        private InetSocketAddress senderAddress;

        /**
         * @param request       the byte string received
         * @param senderAddress the address of the request
         */
        public PingWorker(byte[] request, InetSocketAddress senderAddress) {
            this.request = request;
            this.senderAddress = senderAddress;
        }

        @Override
        public void run() {
            ByteBuffer response = null;
            if (MessageFormatter.isPingResponse(request)) {
                MessageFormatter.PingData pingData = MessageFormatter.decodePing(request, senderAddress);
                boundPeer.peerTracker.registerPingResponse(pingData.getPeerId(), pingData.getSequenceNumber());
//                System.out.println("A ping response message was received from Peer " + pingData.getPeerId());
            } else if (MessageFormatter.isPingRequest(request)) {
                MessageFormatter.PingData pingData = MessageFormatter.decodePing(request, senderAddress);
                boundPeer.peerTracker.registerPingRequest(pingData.getPeerId());
//                System.out.println("A ping request message was received from Peer " + pingData.getPeerId());
                byte[] resp = MessageFormatter.encodePingResponse(request);
                response = (ByteBuffer) (ByteBuffer.allocate(resp.length)).put(resp).flip();
            } else {
                System.out.println("Unknown Ping type, will act as an echo server");
                response = (ByteBuffer) (ByteBuffer.allocate(request.length)).put(request).flip();
            }

            if (response != null) {
                try {
                    udpserver.send(response, senderAddress);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * Sends a datagram from the bound port.
     *
     * @param data            any byte data
     * @param receiverAddress the address to send to
     */
    public void sendDatagram(byte[] data, SocketAddress receiverAddress) {
        try {
            udpserver.send((ByteBuffer) (ByteBuffer.allocate(data.length)).put(data).flip(), receiverAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}