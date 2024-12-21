import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.border.*;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 4670;
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static String username;
    private static String currentChannel = "#global";  // Default channel is #global


    private static final Color DARK_BG = new Color(41, 41, 41);
    private static final Color DARKER_BG = new Color(30, 30, 30);
    private static final Color SELECTION_BG = new Color(62, 81, 181);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color BORDER_COLOR = new Color(70, 70, 70);
    private static final Color INPUT_BG = new Color(50, 50, 50);

    private JFrame loginFrame;
    private JFrame chatFrame;
    private JTextArea chatArea;
    private JTextField messageField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JList<String> channelList;
    private DefaultListModel<String> channelModel;
    private Map<String, StringBuilder> channelMessages;
    private Set<String> onlineUsers;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatClient().createLoginUI();
        });
    }

    private void applyDarkTheme() {
        UIManager.put("Panel.background", DARK_BG);
        UIManager.put("TextField.background", INPUT_BG);
        UIManager.put("TextField.foreground", TEXT_COLOR);
        UIManager.put("TextField.caretForeground", TEXT_COLOR);
        UIManager.put("TextArea.background", DARKER_BG);
        UIManager.put("TextArea.foreground", TEXT_COLOR);
        UIManager.put("List.background", DARKER_BG);
        UIManager.put("List.foreground", TEXT_COLOR);
        UIManager.put("Label.foreground", TEXT_COLOR);
        UIManager.put("Button.background", DARK_BG);
        UIManager.put("Button.foreground", TEXT_COLOR);
        UIManager.put("OptionPane.background", DARK_BG);
        UIManager.put("OptionPane.messageForeground", TEXT_COLOR);
    }

    private void createLoginUI() {
        applyDarkTheme();

        loginFrame = new JFrame("Chat Login");
        loginFrame.setSize(300, 250);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLayout(new BorderLayout());
        loginFrame.getContentPane().setBackground(DARK_BG);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(DARK_BG);

        JPanel choicePanel = new JPanel();
        choicePanel.setBackground(DARK_BG);
        ButtonGroup group = new ButtonGroup();
        JRadioButton loginRadio = createRadioButton("Login", true);
        JRadioButton registerRadio = createRadioButton("Register", false);
        group.add(loginRadio);
        group.add(registerRadio);
        choicePanel.add(loginRadio);
        choicePanel.add(registerRadio);
        mainPanel.add(choicePanel);
        mainPanel.add(Box.createVerticalStrut(10));

        usernameField = createStyledTextField();
        passwordField = createStyledPasswordField();
        JPasswordField confirmPasswordField = createStyledPasswordField();

        mainPanel.add(createLabeledPanel("Username:", usernameField));
        mainPanel.add(createLabeledPanel("Password:", passwordField));
        JPanel confirmPanel = createLabeledPanel("Confirm:", confirmPasswordField);
        confirmPanel.setVisible(false);
        mainPanel.add(confirmPanel);

        JButton submitButton = createStyledButton("Login");
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(DARK_BG);
        buttonPanel.add(submitButton);
        mainPanel.add(buttonPanel);

        loginRadio.addActionListener(e -> {
            submitButton.setText("Login");
            confirmPanel.setVisible(false);
            loginFrame.pack();
        });

        registerRadio.addActionListener(e -> {
            submitButton.setText("Register");
            confirmPanel.setVisible(true);
            loginFrame.pack();
        });

        submitButton.addActionListener(e -> {
            if (loginRadio.isSelected()) {
                attemptLogin(usernameField.getText().trim(), new String(passwordField.getPassword()).trim());
            } else {
                if (!new String(passwordField.getPassword()).equals(new String(confirmPasswordField.getPassword()))) {
                    showErrorMessage("Passwords don't match!");
                    return;
                }
                attemptRegistration(usernameField.getText().trim(), new String(passwordField.getPassword()).trim());
            }
        });

        loginFrame.add(mainPanel);
        loginFrame.pack();
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);
    }

    private JRadioButton createRadioButton(String text, boolean selected) {
        JRadioButton radio = new JRadioButton(text, selected);
        radio.setForeground(TEXT_COLOR);
        radio.setBackground(DARK_BG);
        return radio;
    }

    private JTextField createStyledTextField() {
        JTextField field = new JTextField(20);
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT_COLOR);
        field.setCaretColor(TEXT_COLOR);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        return field;
    }

    private JPasswordField createStyledPasswordField() {
        JPasswordField field = new JPasswordField(20);
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT_COLOR);
        field.setCaretColor(TEXT_COLOR);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        return field;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(DARK_BG);
        button.setForeground(TEXT_COLOR);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
        button.setFocusPainted(false);
        return button;
    }

    private JPanel createLabeledPanel(String labelText, JComponent component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBackground(DARK_BG);
        JLabel label = new JLabel(labelText);
        label.setForeground(TEXT_COLOR);
        panel.add(label);
        panel.add(component);
        return panel;
    }

    private void showErrorMessage(String message) {
        JOptionPane.showMessageDialog(loginFrame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void attemptLogin(String username, String password) {
        try {
            connectToServer();
            out.println("1"); // Choice for login
            out.println(username);
            out.println(password);
            handleAuthenticationResponse();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(loginFrame, "Error connecting to server: " + ex.getMessage());
        }
    }

    private void attemptRegistration(String username, String password) {
        try {
            connectToServer();
            out.println("2"); // Choice for registration
            String response = in.readLine();
            out.println(username);
            response = in.readLine();
            out.println(password);
            response = in.readLine();
            out.println(password);
            handleAuthenticationResponse();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(loginFrame, "Error connecting to server: " + ex.getMessage());
        }
    }

    private void connectToServer() throws IOException {
        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    private void handleAuthenticationResponse() throws IOException {
        String response;
        while ((response = in.readLine()) != null) {
            if (response.contains("successful")) {
                this.username = usernameField.getText().trim();
                createChatUI();
                loginFrame.dispose();
                break;
            } else if (response.startsWith("Error") || response.contains("wrong")) {
                showErrorMessage(response);
                break;
            }
        }
    }

    private void createChatUI() {
        chatFrame = new JFrame("Chat - " + username);
        chatFrame.setSize(600, 400);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(DARKER_BG);
        chatArea.setForeground(TEXT_COLOR);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        chatFrame.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        messageField = new JTextField();
        messageField.setBackground(INPUT_BG);
        messageField.setForeground(TEXT_COLOR);
        messageField.setCaretColor(TEXT_COLOR);
        bottomPanel.add(messageField, BorderLayout.CENTER);
        JButton sendButton = createStyledButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        bottomPanel.add(sendButton, BorderLayout.EAST);
        chatFrame.add(bottomPanel, BorderLayout.SOUTH);

        channelModel = new DefaultListModel<>();
        channelModel.addElement(currentChannel);
        channelList = new JList<>(channelModel);
        channelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        channelList.setBackground(DARKER_BG);
        channelList.setForeground(TEXT_COLOR);
        channelList.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        JScrollPane channelScroll = new JScrollPane(channelList);
        chatFrame.add(channelScroll, BorderLayout.WEST);

        chatFrame.setVisible(true);

        channelMessages = new HashMap<>();
        onlineUsers = new HashSet<>();
        startMessageListener();
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(currentChannel);  // Send current channel
            out.println(message);
            messageField.setText("");
        }
    }

    private void startMessageListener() {
        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    String channel = in.readLine();
                    if (!channelMessages.containsKey(channel)) {
                        channelMessages.put(channel, new StringBuilder());
                    }
                    channelMessages.get(channel).append(message).append("\n");
                    chatArea.setText(channelMessages.get(currentChannel).toString());
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();
    }
}
