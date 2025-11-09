import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Multi-client Server - Handles Publishers and Subscribers with Topic filtering
 * IMPROVED FROM TASK 2: Added topic-based message routing
 */
public class Server {
    private ServerSocket serverSocket;
    
    // IMPROVED: Changed from simple Sets to Topic-based Maps
    // Task 2: Set<ClientHandler> publishers
    // Task 3: Map<String, Set<ClientHandler>> publishers
    private final Map<String, Set<ClientHandler>> publishers = new ConcurrentHashMap<>();
    private final Map<String, Set<ClientHandler>> subscribers = new ConcurrentHashMap<>();
    
    private volatile boolean running = true;

    /**
     * Start server and accept multiple client connections (same as Task 2)
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
     * IMPROVED: Broadcast message to subscribers on specific topic only
     * Task 2: Broadcasted to ALL subscribers
     * Task 3: Broadcasts only to subscribers on the SAME topic
     */
    private synchronized void broadcastToSubscribers(String topic, String message, ClientHandler sender) {
        // Get subscribers for this specific topic only
        Set<ClientHandler> topicSubscribers = subscribers.get(topic);
        
        if (topicSubscribers != null && !topicSubscribers.isEmpty()) {
            System.out.println("[BROADCAST] Publishing message on topic [" + topic + "] to " + 
                             topicSubscribers.size() + " subscriber(s)");
            
            for (ClientHandler subscriber : topicSubscribers) {
                try {
                    subscriber.sendMessage(message);
                } catch (Exception e) {
                    System.err.println("Error sending to subscriber: " + e.getMessage());
                }
            }
        } else {
            System.out.println("[BROADCAST] No subscribers on topic: " + topic);
        }
    }

    /**
     * IMPROVED: Remove client from all topics (was simple remove in Task 2)
     */
    private synchronized void removeClient(ClientHandler handler) {
        // Remove from all publisher topics
        for (Set<ClientHandler> pubSet : publishers.values()) {
            pubSet.remove(handler);
        }
        // Remove from all subscriber topics
        for (Set<ClientHandler> subSet : subscribers.values()) {
            subSet.remove(handler);
        }
    }

    /**
     * Cleanup server resources (same as Task 2)
     */
    private void cleanup() throws IOException {
        running = false;
        
        // Close all client connections
        for (Set<ClientHandler> pubSet : publishers.values()) {
            for (ClientHandler handler : pubSet) {
                handler.close();
            }
        }
        for (Set<ClientHandler> subSet : subscribers.values()) {
            for (ClientHandler handler : subSet) {
                handler.close();
            }
        }
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        System.out.println("Server shutdown complete");
    }

    /**
     * Inner class to handle individual client connections
     * IMPROVED: Added topic awareness
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String role;
        private String topic;  // NEW: Added topic attribute
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
                
                // NEW: Second message from client is the topic
                topic = in.readLine();
                
                if (topic == null) {
                    System.out.println("Client disconnected before sending topic");
                    return;
                }
                
                topic = topic.trim();
                
                // IMPROVED: Register client based on role AND topic
                if ("PUBLISHER".equals(role)) {
                    publishers.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(this);
                    System.out.println("[PUBLISHER CONNECTED] " + clientId + " on topic: " + topic);
                    System.out.println("Total Publishers on topic [" + topic + "]: " + 
                                     publishers.get(topic).size());
                    out.println("Registered as PUBLISHER on topic: " + topic);
                } else if ("SUBSCRIBER".equals(role)) {
                    subscribers.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(this);
                    System.out.println("[SUBSCRIBER CONNECTED] " + clientId + " on topic: " + topic);
                    System.out.println("Total Subscribers on topic [" + topic + "]: " + 
                                     subscribers.get(topic).size());
                    out.println("Registered as SUBSCRIBER on topic: " + topic);
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
                        System.out.println("[" + role + " DISCONNECTED]");
                        System.out.println("  Client ID: " + clientId);
                        System.out.println("  Topic: " + topic);
                        break;
                    }
                    
                    // Display message on server
                    System.out.println("[" + role + " - " + clientId + " - Topic: " + topic + "]: " + message);
                    
                    // IMPROVED: If publisher, broadcast to subscribers on SAME topic only
                    if ("PUBLISHER".equals(role)) {
                        String broadcastMsg = "[Publisher " + clientId + "]: " + message;
                        broadcastToSubscribers(topic, broadcastMsg, this);
                    }
                }
                
            } catch (IOException e) {
                System.err.println("Client handler error: " + e.getMessage());
            } finally {
                close();
                removeClient(this);
                
                // Display remaining counts per topic
                int totalPubs = 0, totalSubs = 0;
                for (Set<ClientHandler> pubSet : publishers.values()) {
                    totalPubs += pubSet.size();
                }
                for (Set<ClientHandler> subSet : subscribers.values()) {
                    totalSubs += subSet.size();
                }
                System.out.println("Remaining - Publishers: " + totalPubs + 
                                 ", Subscribers: " + totalSubs + "\n");
            }
        }

        /**
         * Send message to this client (same as Task 2)
         */
        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        /**
         * Close client connection (same as Task 2)
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