import java.io.*;
import java.net.*;

public class Server {
    private ServerSocket serverSocket;

    /**
     * Start server and handle multiple clients sequentially
     */
    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server listening on port: " + port);

        while (true) { // Keep server running indefinitely
            System.out.println("\nWaiting for client connection...");
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());

            // Handle communication with this client
            handleClient(clientSocket);
        }
    }

    /**
     * Handle communication with a single client
     */
    private void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Client says: " + message);
                out.println("Server received: " + message);

                // Check for termination command
                if ("terminate".equalsIgnoreCase(message.trim())) {
                    System.out.println("Client requested termination");
                    out.println("Server acknowledges termination");
                    break; // Exit loop for this client only
                }
            }
            System.out.println("Client disconnected: " + clientSocket.getInetAddress());
            clientSocket.close(); // Close this client connection
        } catch (IOException e) {
            e.printStackTrace();
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
