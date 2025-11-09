import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Client - Supports Publisher and Subscriber roles
 * Task 2: Third command line argument specifies role
 */
public class Client {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = true;
    private String role;

    public void start(String serverIP, int serverPort, String clientRole) throws IOException {
        this.role = clientRole.toUpperCase();
        
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
        System.out.println("===========================================\n");
        
        // Setup I/O streams
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        // Send role to server as first message
        out.println(role);
        
        // Wait for server acknowledgment
        String serverResponse = in.readLine();
        System.out.println("[Server]: " + serverResponse + "\n");
        
        // Start receiver thread for subscriber functionality
        Thread receiver = new Thread(() -> receiveMessages());
        receiver.start();
        
        // Main thread handles user input
        if ("PUBLISHER".equals(role)) {
            publishMessages();
        } else {
            subscriberInput(); // Subscribers can still type to terminate
        }
        
        // Wait for receiver thread
        try {
            receiver.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        cleanup();
    }

    /**
     * Publisher: Send messages to server
     */
    private void publishMessages() {
        Scanner scanner = new Scanner(System.in);
        String message;
        
        System.out.println("--- PUBLISHER MODE ---");
        System.out.println("Type messages to publish to all subscribers");
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
        System.out.println("Listening for published messages...");
        System.out.println("Type 'terminate' to disconnect\n");
        
        while (running) {
            message = scanner.nextLine();
            
            // Only respond to terminate command
            if ("terminate".equalsIgnoreCase(message.trim())) {
                out.println(message);
                System.out.println("\nDisconnecting from server...");
                running = false;
                break;
            }
        }
        
        scanner.close();
    }

    /**
     * Receive messages from server (runs in separate thread)
     */
    private void receiveMessages() {
        try {
            String serverMessage;
            while (running && (serverMessage = in.readLine()) != null) {
                // Display received message
                if ("SUBSCRIBER".equals(role)) {
                    System.out.println("\n" + serverMessage);
                }
            }
        } catch (IOException e) {
            if (running && !socket.isClosed()) {
                System.err.println("Error receiving from server: " + e.getMessage());
            }
        }
    }

    /**
     * Cleanup resources
     */
    private void cleanup() throws IOException {
        running = false;
        if (in != null) in.close();
        if (out != null) out.close();
        if (socket != null) socket.close();
        System.out.println("Client disconnected successfully");
    }

    public static void main(String[] args) {
        // Validate command line arguments: IP, PORT, and ROLE required
        if (args.length != 3) {
            System.out.println("Usage: java Client <SERVER_IP> <SERVER_PORT> <ROLE>");
            System.out.println("Example: java Client 192.168.10.2 5000 PUBLISHER");
            System.out.println("         java Client 192.168.10.2 5000 SUBSCRIBER");
            System.out.println("         java Client localhost 5000 PUBLISHER");
            return;
        }
        
        try {
            String serverIP = args[0];
            int serverPort = Integer.parseInt(args[1]);
            String role = args[2];
            
            Client client = new Client();
            client.start(serverIP, serverPort, role);
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