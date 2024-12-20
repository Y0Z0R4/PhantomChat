import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import java.util.*;

public class ChatClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final String EXIT_COMMAND = "/exit";
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static String username;
    private static JFrame frame;
    private static JTextArea chatArea;
    private static JTextField inputField;
    private static boolean isConnected = false;
    private static Map<String, JFrame> dmWindows = new HashMap<>();

    public static void main(String[] args) {
        setupGUI();
        new Thread(ChatClient::startConnection).start();
    }

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

    private static void startConnection() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            isConnected = true;
            startCommunication();
        } catch (IOException e) {
            showError("Error connecting to server: " + e.getMessage());
        }
    }

    private static void login() throws IOException {
        String inputUsername = JOptionPane.showInputDialog(frame, "Enter your username: ");
        if (inputUsername == null || inputUsername.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Invalid username. Disconnecting...");
            socket.close();
            return;
        }

        String inputPassword = JOptionPane.showInputDialog(frame, "Enter your password: ");
        if (inputPassword == null || inputPassword.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Invalid password. Disconnecting...");
            socket.close();
            return;
        }

        out.println(inputUsername);
        out.println(inputPassword);

        String response = in.readLine();
        if (response.equals("Invalid username or password. Disconnecting...")) {
            JOptionPane.showMessageDialog(frame, "Invalid credentials. Closing connection.");
            socket.close();
            return;
        }

        username = inputUsername;
        chatArea.append("You are connected to the chat server!\n");
    }

    private static void startCommunication() {
        try {
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                chatArea.append(serverMessage + "\n");
                if (serverMessage.startsWith("[DM from")) {
                    String sender = serverMessage.split(" ")[2].replace(":", "");
                    openDMWindow(sender, serverMessage);
                }
            }
        } catch (IOException e) {
            showError("Error during communication: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private static void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

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

        out.println(message);
        chatArea.append("You: " + message + "\n");
        inputField.setText("");
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

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

    private static void openDMWindow(String sender, String message) {
        if (!dmWindows.containsKey(sender)) {
            JFrame dmFrame = new JFrame("DM with " + sender);
            JTextArea dmArea = new JTextArea();
            dmArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(dmArea);
            dmFrame.add(scrollPane, BorderLayout.CENTER);

            JTextField dmInputField = new JTextField();
            dmInputField.addActionListener(e -> sendDMMessage(sender, dmArea, dmInputField));
            dmFrame.add(dmInputField, BorderLayout.SOUTH);

            dmFrame.setSize(400, 300);
            dmFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            dmFrame.setVisible(true);

            dmWindows.put(sender, dmFrame);
            dmArea.append(message + "\n");
        } else {
            JTextArea dmArea = (JTextArea) ((JScrollPane) dmWindows.get(sender).getContentPane().getComponent(0)).getViewport().getView();
            dmArea.append(message + "\n");
        }
    }

    private static void sendDMMessage(String receiver, JTextArea dmArea, JTextField dmInputField) {
        String message = dmInputField.getText().trim();
        if (!message.isEmpty()) {
            out.println("/dm " + receiver + " " + message);
            dmArea.append("You: " + message + "\n");
            dmInputField.setText("");
        }
    }
}
