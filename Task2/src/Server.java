import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Multi-client Server - Handles Publishers and Subscribers
 * Task 2: Supports concurrent clients with role-based message routing
 */
public class Server {
    private ServerSocket serverSocket;
    private final Set<ClientHandler> publishers = ConcurrentHashMap.newKeySet();
    private final Set<ClientHandler> subscribers = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;

    /**
     * Start server and accept multiple client connections
     */
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("===========================================");
        System.out.println("Server started on port: " + port);
        System.out.println("Waiting for clients to connect...");
        System.out.println("===========================================\n");
        
        // Accept multiple client connections concurrently
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                
                // Handle each client in a separate thread
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
                
            } catch (SocketException e) {
                if (!running) {
                    System.out.println("Server shutdown initiated");
                }
            }
        }
        
        cleanup();
    }

    /**
     * Broadcast message from publishers to all subscribers
     */
    private synchronized void broadcastToSubscribers(String message, ClientHandler sender) {
        System.out.println("[BROADCAST] Publishing message to " + subscribers.size() + " subscriber(s)");
        
        for (ClientHandler subscriber : subscribers) {
            try {
                subscriber.sendMessage(message);
            } catch (Exception e) {
                System.err.println("Error sending to subscriber: " + e.getMessage());
            }
        }
    }

    /**
     * Remove client from appropriate list
     */
    private synchronized void removeClient(ClientHandler handler) {
        publishers.remove(handler);
        subscribers.remove(handler);
    }

    /**
     * Cleanup server resources
     */
    private void cleanup() throws IOException {
        running = false;
        
        // Close all client connections
        for (ClientHandler handler : publishers) {
            handler.close();
        }
        for (ClientHandler handler : subscribers) {
            handler.close();
        }
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        System.out.println("Server shutdown complete");
    }

    /**
     * Inner class to handle individual client connections
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String role;
        private String clientId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        @Override
        public void run() {
            try {
                // Setup I/O streams
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                // First message from client should be their role
                role = in.readLine();
                
                if (role == null) {
                    System.out.println("Client disconnected before sending role");
                    return;
                }
                
                role = role.trim().toUpperCase();
                
                // Register client based on role
                if ("PUBLISHER".equals(role)) {
                    publishers.add(this);
                    System.out.println("[PUBLISHER CONNECTED] " + clientId);
                    System.out.println("Total Publishers: " + publishers.size());
                    out.println("Registered as PUBLISHER");
                } else if ("SUBSCRIBER".equals(role)) {
                    subscribers.add(this);
                    System.out.println("[SUBSCRIBER CONNECTED] " + clientId);
                    System.out.println("Total Subscribers: " + subscribers.size());
                    out.println("Registered as SUBSCRIBER");
                } else {
                    out.println("Invalid role. Use PUBLISHER or SUBSCRIBER");
                    socket.close();
                    return;
                }
                
                // Handle messages from client
                String message;
                while ((message = in.readLine()) != null) {
                    
                    // Check for termination
                    if ("terminate".equalsIgnoreCase(message.trim())) {
                        System.out.println("[" + role + " DISCONNECTED] " + clientId);
                        break;
                    }
                    
                    // Display message on server
                    System.out.println("[" + role + " - " + clientId + "]: " + message);
                    
                    // If publisher, broadcast to all subscribers
                    if ("PUBLISHER".equals(role)) {
                        String broadcastMsg = "[Publisher " + clientId + "]: " + message;
                        broadcastToSubscribers(broadcastMsg, this);
                    }
                }
                
            } catch (IOException e) {
                System.err.println("Client handler error: " + e.getMessage());
            } finally {
                close();
                removeClient(this);
                System.out.println("Remaining - Publishers: " + publishers.size() + 
                                 ", Subscribers: " + subscribers.size() + "\n");
            }
        }

        /**
         * Send message to this client
         */
        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        /**
         * Close client connection
         */
        public void close() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                System.err.println("Error closing client: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Server <PORT>");
            System.out.println("Example: java Server 5000");
            return;
        }
        
        try {
            int port = Integer.parseInt(args[0]);
            Server server = new Server();
            server.start(port);
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid number");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}