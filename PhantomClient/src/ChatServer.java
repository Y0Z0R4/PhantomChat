import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class ChatServer {
    private static final int SERVER_PORT = 4670;
    private static final Map<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    private static final Set<String> usernames = new HashSet<>();
    private static final Map<String, String> users = new HashMap<>();
    private static final String USERS_FILE = "users.txt";
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MIN_PASSWORD_LENGTH = 4;

    public static void main(String[] args) {
        System.out.println("Chat server started...");
        createUsersFileIfNotExist();
        loadUsers();

        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error hashing password: " + e.getMessage());
            return null;
        }
    }

    private static synchronized void saveUsers() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE))) {
            for (Map.Entry<String, String> entry : users.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving user data: " + e.getMessage());
        }
    }

    private static void loadUsers() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    users.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading user data: " + e.getMessage());
        }
    }

    private static void createUsersFileIfNotExist() {
        File usersFile = new File(USERS_FILE);
        if (!usersFile.exists()) {
            try {
                usersFile.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating users file: " + e.getMessage());
            }
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        private boolean isValidUsername(String username) {
            return username != null &&
                    username.length() >= MIN_USERNAME_LENGTH &&
                    username.matches("^[a-zA-Z0-9_]+$");
        }

        private boolean isValidPassword(String password) {
            return password != null &&
                    password.length() >= MIN_PASSWORD_LENGTH;
        }

        private boolean handleRegistration() throws IOException {
            while (true) {
                out.println("Enter desired username (min " + MIN_USERNAME_LENGTH + " chars, alphanumeric and underscore only): ");
                String newUsername = in.readLine();
                if (newUsername == null) return false;
                newUsername = newUsername.trim();

                if (!isValidUsername(newUsername)) {
                    out.println("Invalid username format!");
                    continue;
                }

                synchronized (users) {
                    if (users.containsKey(newUsername)) {
                        out.println("Username already exists!");
                        continue;
                    }

                    out.println("Enter password (min " + MIN_PASSWORD_LENGTH + " chars): ");
                    String password = in.readLine();
                    if (password == null) return false;
                    password = password.trim();

                    if (!isValidPassword(password)) {
                        out.println("Invalid password format!");
                        continue;
                    }

                    out.println("Confirm password: ");
                    String confirmPassword = in.readLine();
                    if (confirmPassword == null) return false;
                    confirmPassword = confirmPassword.trim();

                    if (!password.equals(confirmPassword)) {
                        out.println("Passwords don't match!");
                        continue;
                    }

                    String hashedPassword = hashPassword(password);
                    if (hashedPassword == null) {
                        out.println("Error during registration. Please try again.");
                        return false;
                    }

                    users.put(newUsername, hashedPassword);
                    saveUsers();
                    username = newUsername;
                    out.println("Registration successful!");
                    return true;
                }
            }
        }

        private boolean handleLogin() throws IOException {
            int attempts = 0;
            final int MAX_ATTEMPTS = 3;

            while (attempts < MAX_ATTEMPTS) {
                out.println("Enter username: ");
                String loginUsername = in.readLine();
                if (loginUsername == null) return false;
                loginUsername = loginUsername.trim();

                out.println("Enter password: ");
                String password = in.readLine();
                if (password == null) return false;
                password = password.trim();

                String hashedPassword = hashPassword(password);
                if (hashedPassword == null) {
                    out.println("Error during login. Please try again.");
                    return false;
                }

                synchronized (users) {
                    if (users.containsKey(loginUsername) &&
                            users.get(loginUsername).equals(hashedPassword)) {

                        synchronized (clientWriters) {
                            if (clientWriters.containsKey(loginUsername)) {
                                out.println("User already logged in!");
                                return false;
                            }
                            username = loginUsername;
                            out.println("Login successful!");
                            return true;
                        }
                    }
                }

                attempts++;
                out.println("Invalid credentials! Attempts remaining: " + (MAX_ATTEMPTS - attempts));
            }

            out.println("Too many failed attempts. Please try again later.");
            return false;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                boolean authenticated = false;
                out.println("1. Login\n2. Register\nChoice: ");
                String choice = in.readLine();

                if (choice != null) {
                    switch (choice.trim()) {
                        case "1":
                            authenticated = handleLogin();
                            break;
                        case "2":
                            authenticated = handleRegistration();
                            break;
                        default:
                            out.println("Invalid choice!");
                            break;
                    }
                }

                if (!authenticated) {
                    clientSocket.close();
                    return;
                }

                synchronized (clientWriters) {
                    clientWriters.put(username, out);
                }
                synchronized (usernames) {
                    usernames.add(username);
                }

                broadcast(username + " has joined the chat.");
                showOnlineUsers();

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("/exit")) {
                        break;
                    } else if (message.equalsIgnoreCase("/users")) {
                        showOnlineUsers();
                    } else {
                        broadcast(username + ": " + message);
                    }
                }

            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void cleanup() {
            if (username != null) {
                synchronized (clientWriters) {
                    clientWriters.remove(username);
                }
                synchronized (usernames) {
                    usernames.remove(username);
                }
                broadcast(username + " has left the chat.");
            }

            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }

        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters.values()) {
                    writer.println(message);
                }
            }
        }

        private void showOnlineUsers() {
            StringBuilder onlineUsers = new StringBuilder("Online users: ");
            synchronized (usernames) {
                for (String user : usernames) {
                    onlineUsers.append(user).append(" ");
                }
            }
            out.println(onlineUsers.toString());
        }
    }
}
