import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Task 1: Basic Client - Connects to server and sends messages
 * Runs until user types "terminate"
 * Demonstrates bidirectional communication by receiving server responses
 */
public class Client {
    private Socket socket;              // Connection to server
    private PrintWriter out;            // Send messages to server
    private BufferedReader in;          // Receive responses from server
    private volatile boolean running = true;  // Control flag for threads

    /**
     * Connect to server and start bidirectional communication
     */
    public void start(String serverIP, int serverPort) throws IOException {
        // Establish TCP connection to server
        socket = new Socket(serverIP, serverPort);
        System.out.println("Connected to server: " + serverIP + ":" + serverPort);
        System.out.println("Two-way communication established\n");
        
        // Setup output stream to send messages
        out = new PrintWriter(socket.getOutputStream(), true);
        
        // Setup input stream to receive server responses
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        // Start separate thread to receive server responses
        // This demonstrates bidirectional communication
        Thread receiver = new Thread(() -> receiveMessages());
        receiver.start();
        
        // Main thread handles sending user input to server
        sendMessages();
        
        // Wait for receiver thread to finish
        try {
            receiver.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        cleanup();
    }

    /**
     * Send messages from user input to server
     */
    private void sendMessages() {
        Scanner scanner = new Scanner(System.in);
        String message;
        
        System.out.println("Type your messages below:");
        System.out.println("Type 'terminate' to disconnect and exit\n");
        
        while (running) {
            System.out.print("You: ");
            message = scanner.nextLine();
            
            // Send message to server
            out.println(message);
            
            // Check for termination command
            if ("terminate".equalsIgnoreCase(message.trim())) {
                System.out.println("\nDisconnecting from server...");
                running = false;
                break;
            }
        }
        
        scanner.close();
    }

    /**
     * Receive messages from server (runs in separate thread)
     * This demonstrates the "communicating with each other" requirement
     */
    private void receiveMessages() {
        try {
            String serverMessage;
            while (running && (serverMessage = in.readLine()) != null) {
                // Display server's response
                System.out.println("\n[Server] " + serverMessage);
                
                // Reprint prompt for better user experience
                if (running) {
                    System.out.print("You: ");
                    System.out.flush();
                }
            }
        } catch (IOException e) {
            // Only show error if we're still running (not during shutdown)
            if (running && !socket.isClosed()) {
                System.err.println("Error receiving from server: " + e.getMessage());
            }
        }
    }

    /**
     * Close all connections and streams
     */
    private void cleanup() throws IOException {
        running = false;
        if (in != null) in.close();
        if (out != null) out.close();
        if (socket != null) socket.close();
        System.out.println("Client disconnected successfully");
    }

    public static void main(String[] args) {
        // Validate command line arguments: IP and PORT required
        if (args.length != 2) {
            System.out.println("Usage: java Client <SERVER_IP> <SERVER_PORT>");
            System.out.println("Example: java Client 192.168.10.2 5000");
            System.out.println("         java Client localhost 5000");
            return;
        }
        
        try {
            String serverIP = args[0];
            int serverPort = Integer.parseInt(args[1]);
            
            Client client = new Client();
            client.start(serverIP, serverPort);
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid number");
        } catch (ConnectException e) {
            System.err.println("Error: Could not connect to server");
            System.err.println("Make sure server is running at specified address");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}