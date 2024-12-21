import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static String username;

    private JFrame loginFrame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatClient().createLoginUI();
        });
    }

    private void createLoginUI() {
        loginFrame = new JFrame("Login");
        loginFrame.setSize(300, 200);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLayout(new FlowLayout());

        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);
        loginButton = new JButton("Login");
        registerButton = new JButton("Register");

        loginFrame.add(new JLabel("Username:"));
        loginFrame.add(usernameField);
        loginFrame.add(new JLabel("Password:"));
        loginFrame.add(passwordField);
        loginFrame.add(loginButton);
        loginFrame.add(registerButton);

        loginButton.addActionListener(e -> attemptLogin());
        registerButton.addActionListener(e -> attemptRegistration());

        loginFrame.setVisible(true);
    }

    private void attemptLogin() {
        String enteredUsername = usernameField.getText().trim();
        String enteredPassword = new String(passwordField.getPassword()).trim();

        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(enteredUsername);
            out.println(enteredPassword);

            String response = in.readLine(); // Response from server
            if (response.equals("Login successful!")) {
                username = enteredUsername;
                loginFrame.dispose();
                createChatUI();
            } else {
                JOptionPane.showMessageDialog(loginFrame, "Login failed! " + response);
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(loginFrame, "Error connecting to server.");
            ex.printStackTrace();
        }
    }

    private void attemptRegistration() {
        String enteredUsername = usernameField.getText().trim();
        String enteredPassword = new String(passwordField.getPassword()).trim();

        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(enteredUsername);
            out.println(enteredPassword);

            String response = in.readLine(); // Response from server
            if (response.equals("Registration successful!")) {
                username = enteredUsername;
                loginFrame.dispose();
                createChatUI();
            } else {
                JOptionPane.showMessageDialog(loginFrame, "Error registering: " + response);
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(loginFrame, "Error connecting to server.");
            ex.printStackTrace();
        }
    }

    private void createChatUI() {
        JFrame chatFrame = new JFrame("Chat - " + username);
        chatFrame.setSize(500, 500);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setLayout(new BorderLayout());

        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatFrame.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JTextField messageField = new JTextField();
        chatFrame.add(messageField, BorderLayout.SOUTH);

        messageField.addActionListener(e -> {
            String message = messageField.getText();
            out.println(message);
            messageField.setText("");
        });

        chatFrame.setVisible(true);

        // Listen for incoming messages
        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    chatArea.append(message + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
