import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.security.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.*;

public class ChatServer {
    private static final int SERVER_PORT = 12345;
    private static final ExecutorService clientHandlers = Executors.newCachedThreadPool();  // Handles multiple clients
    private static final String EXIT_COMMAND = "/exit";  // Command to gracefully shutdown server

    public static void main(String[] args) {
        System.out.println("Server starting...");

        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("Server started on port " + SERVER_PORT);

            // Accept and handle clients
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                    // Spawn a new thread for handling the client connection
                    clientHandlers.submit(new ClientHandler(clientSocket));
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // This class handles communication with a single client
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private DiffieHellman dh;
        private SecretKey sharedKey;
        private String username;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                // Initialize the input and output streams
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Step 1: Read username
                out.println("Enter your username: ");
                username = in.readLine();
                if (username == null || username.trim().isEmpty()) {
                    out.println("Invalid username. Disconnecting...");
                    clientSocket.close();
                    return;
                }

                System.out.println("Username: " + username);

                // Step 2: Initialize Diffie-Hellman key exchange
                dh = new DiffieHellman();
                BigInteger clientPublicKey = new BigInteger(in.readLine(), 16);
                System.out.println("Received client's public key: " + clientPublicKey.toString(16));

                // Generate and send the server's public key
                BigInteger serverPublicKey = dh.getPublicKey();
                out.println(serverPublicKey.toString(16));

                // Generate the shared secret
                BigInteger sharedSecret = dh.generateSharedSecret(clientPublicKey);
                byte[] sharedSecretBytes = sharedSecret.toByteArray();
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                byte[] hashedKey = sha256.digest(sharedSecretBytes);
                sharedKey = new SecretKeySpec(hashedKey, 0, 32, "AES");

                // Key exchange successful
                out.println("Key exchange successful. Shared key established.");

                // Step 3: Handle commands and messages
                handleClientCommands();

            } catch (IOException | GeneralSecurityException e) {
                System.err.println("Error handling client: " + e.getMessage());
                e.printStackTrace();
            } finally {
                closeResources();
            }
        }

        // Handle client commands and messages
        private void handleClientCommands() {
            try {
                String command;
                while ((command = in.readLine()) != null) {
                    if (command.equalsIgnoreCase(EXIT_COMMAND)) {
                        out.println("Goodbye " + username + "!");
                        break;
                    }
                    // Handle the message with encryption and decryption
                    String decryptedMessage = decryptMessage(command);
                    System.out.println("Received from " + username + ": " + decryptedMessage);
                    out.println("Message received: " + decryptedMessage);
                }
            } catch (IOException e) {
                System.err.println("Error reading message from " + username + ": " + e.getMessage());
                e.printStackTrace();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        // Encrypt the message using AES
        private String encryptMessage(String message) throws GeneralSecurityException {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, sharedKey, ivSpec);
            byte[] encryptedData = cipher.doFinal(message.getBytes());

            // Combine IV and encrypted data
            byte[] result = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);

            return Base64.getEncoder().encodeToString(result);
        }

        // Decrypt the message using AES
        private String decryptMessage(String encryptedMessage) throws GeneralSecurityException {
            byte[] data = Base64.getDecoder().decode(encryptedMessage);
            byte[] iv = Arrays.copyOfRange(data, 0, 16);
            byte[] encryptedData = Arrays.copyOfRange(data, 16, data.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, sharedKey, ivSpec);

            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData);
        }

        // Close resources
        private void closeResources() {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
