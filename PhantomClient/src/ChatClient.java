import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import javax.swing.*;

public class ChatClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final String EXIT_COMMAND = "/exit";
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static SecretKey sharedKey;
    private static DiffieHellman dh;
    private static String username;
    private static JFrame frame;
    private static JTextArea chatArea;
    private static JTextField inputField;
    private static boolean isConnected = false;

    public static void main(String[] args) {
        // GUI setup
        setupGUI();

        // Start connection process in a new thread
        new Thread(ChatClient::startConnection).start();
    }

    // Set up the graphical user interface
    private static void setupGUI() {
        frame = new JFrame("ChatClient");
        frame.setSize(400, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.addActionListener(e -> sendMessage());
        frame.add(inputField, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    // Start the connection and login process
    private static void startConnection() {
        try {
            // Establish connection to the server
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Start the login process
            login();

            // Start the communication with the server
            isConnected = true;
            startCommunication();

        } catch (IOException e) {
            showError("Error connecting to server: " + e.getMessage());
        }
    }

    // Login and handle the username input
    private static void login() throws IOException {
        // Prompt for username
        String input = JOptionPane.showInputDialog(frame, "Enter your username: ");
        if (input != null && !input.trim().isEmpty()) {
            username = input.trim();
            out.println(username);
            System.out.println("Username: " + username);
        } else {
            JOptionPane.showMessageDialog(frame, "Invalid username. Disconnecting...");
            socket.close();
            return;
        }

        // Start Diffie-Hellman key exchange after username is validated
        try {
            dh = new DiffieHellman();
            // Read the server's response which should be the server's public key (as a hex string)
            String serverResponse = in.readLine();
            if (serverResponse == null || !serverResponse.matches("[0-9a-fA-F]+")) {
                throw new IllegalArgumentException("Invalid response from server. Expected a valid hex string.");
            }

            BigInteger serverPublicKey = new BigInteger(serverResponse, 16);
            System.out.println("Received server's public key: " + serverPublicKey.toString(16));

            // Generate and send the client's public key
            BigInteger clientPublicKey = dh.getPublicKey();
            out.println(clientPublicKey.toString(16));

            // Generate shared secret
            BigInteger sharedSecret = dh.generateSharedSecret(serverPublicKey);
            byte[] sharedSecretBytes = sharedSecret.toByteArray();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hashedKey = sha256.digest(sharedSecretBytes);
            sharedKey = new SecretKeySpec(hashedKey, 0, 32, "AES");

            // Confirm key exchange
            String keyExchangeSuccessMessage = in.readLine();
            System.out.println(keyExchangeSuccessMessage);
            chatArea.append("Connected to server and key exchange completed.\n");

        } catch (GeneralSecurityException e) {
            showError("Error during key exchange: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            showError("Invalid server response: " + e.getMessage());
        }
    }

    // Start receiving messages and interacting with the server
    private static void startCommunication() {
        try {
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                if (serverMessage.equals("Key exchange successful. Shared key established.")) {
                    // Server confirmed the key exchange was successful
                    chatArea.append("Key exchange successful. Ready to chat!\n");
                } else if (serverMessage.equals("Goodbye " + username + "!")) {
                    // Server says goodbye
                    chatArea.append("Server has disconnected. Goodbye!\n");
                    break;
                } else {
                    // Decrypt and display server's message
                    String decryptedMessage = decryptMessage(serverMessage);
                    chatArea.append("Server: " + decryptedMessage + "\n");
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            showError("Error during communication: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    // Send message to the server
    private static void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        // If the message is the exit command, disconnect
        if (message.equalsIgnoreCase(EXIT_COMMAND)) {
            try {
                out.println(EXIT_COMMAND);
                chatArea.append("You have left the chat.\n");
                socket.close();
            } catch (IOException e) {
                showError("Error sending exit command: " + e.getMessage());
            }
            return;
        }

        // Encrypt and send the message
        try {
            String encryptedMessage = encryptMessage(message);
            out.println(encryptedMessage);
            chatArea.append("You: " + message + "\n");
        } catch (GeneralSecurityException e) {
            showError("Error encrypting message: " + e.getMessage());
        }

        inputField.setText("");
    }

    // Encrypt the message using AES
    private static String encryptMessage(String message) throws GeneralSecurityException {
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
    private static String decryptMessage(String encryptedMessage) throws GeneralSecurityException {
        byte[] data = Base64.getDecoder().decode(encryptedMessage);
        byte[] iv = Arrays.copyOfRange(data, 0, 16);
        byte[] encryptedData = Arrays.copyOfRange(data, 16, data.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, ivSpec);

        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData);
    }

    // Display error message in the chat window
    private static void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Close the connection and cleanup
    private static void closeConnection() {
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
}
