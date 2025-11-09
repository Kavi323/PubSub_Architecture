import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Client - Supports Publisher and Subscriber roles with Topic filtering
 * IMPROVED FROM TASK 2: Added topic parameter (4th command line argument)
 * FIXED: Clean termination without exceptions
 */
public class Client {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = true;
    private String role;
    private String topic;

    /**
     * IMPROVED: Added topic parameter
     */
    public void start(String serverIP, int serverPort, String clientRole, String topic) throws IOException {
        this.role = clientRole.toUpperCase();
        this.topic = topic;
        
        // Validate role
        if (!role.equals("PUBLISHER") && !role.equals("SUBSCRIBER")) {
            System.err.println("Error: Role must be PUBLISHER or SUBSCRIBER");
            return;
        }
        
        // Establish TCP connection
        socket = new Socket(serverIP, serverPort);
        System.out.println("===========================================");
        System.out.println("Connected to server: " + serverIP + ":" + serverPort);
        System.out.println("Client Role: " + role);
        System.out.println("Topic: " + topic);
        System.out.println("===========================================\n");
        
        // Setup I/O streams
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        // Send role to server as first message
        out.println(role);
        
        // Send topic to server as second message
        out.println(topic);
        
        // Wait for server acknowledgment
        String serverResponse = in.readLine();
        System.out.println("[Server]: " + serverResponse + "\n");
        
        // Start receiver thread for subscriber functionality
        Thread receiver = new Thread(() -> receiveMessages());
        receiver.setDaemon(true); // FIXED: Make daemon so it doesn't prevent clean exit
        receiver.start();
        
        // Main thread handles user input
        if ("PUBLISHER".equals(role)) {
            publishMessages();
        } else {
            subscriberInput();
        }
        
        // FIXED: Give receiver thread time to finish gracefully
        cleanup();
        
        // FIXED: Wait briefly for receiver thread to finish
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    /**
     * Publisher: Send messages to server
     */
    private void publishMessages() {
        Scanner scanner = new Scanner(System.in);
        String message;
        
        System.out.println("--- PUBLISHER MODE ---");
        System.out.println("Publishing on topic: " + topic);
        System.out.println("Type messages to publish to subscribers on this topic");
        System.out.println("Type 'terminate' to disconnect\n");
        
        while (running) {
            System.out.print("Publish: ");
            message = scanner.nextLine();
            
            // Send message to server
            out.println(message);
            
            // Check for termination
            if ("terminate".equalsIgnoreCase(message.trim())) {
                System.out.println("\nDisconnecting from server...");
                running = false;
                break;
            }
        }
        
        scanner.close();
    }

    /**
     * Subscriber: Wait for messages but allow termination
     */
    private void subscriberInput() {
        Scanner scanner = new Scanner(System.in);
        String message;
        
        System.out.println("--- SUBSCRIBER MODE ---");
        System.out.println("Subscribed to topic: " + topic);
        System.out.println("Listening for published messages on this topic...");
        System.out.println("Type 'terminate' to disconnect\n");
        
        while (running) {
            message = scanner.nextLine();
            
            // Only respond to terminate command
            if ("terminate".equalsIgnoreCase(message.trim())) {
                System.out.println("\nDisconnecting from server...");
                running = false;
                out.println(message); // Send terminate to server
                break;
            }
        }
        
        scanner.close();
    }

    /**
     * Receive messages from server
     * FIXED: Proper exception handling for clean termination
     */
    private void receiveMessages() {
        try {
            String serverMessage;
            while (running && (serverMessage = in.readLine()) != null) {
                // Display received message
                if ("SUBSCRIBER".equals(role)) {
                    System.out.println("\nReceived: " + serverMessage);
                    if (running) {
                        System.out.print("You: ");
                        System.out.flush();
                    }
                }
            }
        } catch (SocketException e) {
            // FIXED: Expected exception when connection closes - don't print stack trace
            if (running) {
                System.err.println("\nConnection closed by server");
            }
        } catch (IOException e) {
            // FIXED: Only show error if we're still supposed to be running
            if (running && !socket.isClosed()) {
                System.err.println("\nError receiving from server: " + e.getMessage());
            }
        }
    }

    /**
     * Cleanup resources
     * FIXED: Stop running flag first, then close resources
     */
    private void cleanup() {
        running = false; // FIXED: Stop flag before closing to prevent error messages
        
        try {
            // FIXED: Close in proper order
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("Client disconnected successfully");
        } catch (IOException e) {
            // FIXED: Suppress cleanup errors
            System.err.println("Cleanup error (can be ignored): " + e.getMessage());
        }
    }

    /**
     * Main method - accepts 4 arguments
     */
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java Client <SERVER_IP> <SERVER_PORT> <ROLE> <TOPIC>");
            System.out.println("Example: java Client 192.168.10.2 5000 PUBLISHER TOPIC_A");
            System.out.println("         java Client 192.168.10.2 5000 SUBSCRIBER TOPIC_A");
            System.out.println("         java Client localhost 5000 PUBLISHER SPORTS");
            System.out.println("         java Client localhost 5000 SUBSCRIBER SPORTS");
            return;
        }
        
        try {
            String serverIP = args[0];
            int serverPort = Integer.parseInt(args[1]);
            String role = args[2];
            String topic = args[3];
            
            Client client = new Client();
            client.start(serverIP, serverPort, role, topic);
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid number");
        } catch (ConnectException e) {
            System.err.println("Error: Could not connect to server");
            System.err.println("Make sure server is running at specified address");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}