import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int SERVER_PORT = 12345;
    private static final Map<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    private static final Set<String> usernames = new HashSet<>();
    private static final Map<String, String> users = new HashMap<>();
    private static final String USERS_FILE = "users.txt";

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

    private static void createUsersFileIfNotExist() {
        File usersFile = new File(USERS_FILE);
        if (!usersFile.exists()) {
            try {
                if (usersFile.createNewFile()) {
                    System.out.println("users.txt file created.");
                }
            } catch (IOException e) {
                System.err.println("Error creating users.txt file: " + e.getMessage());
            }
        }
    }

    private static void loadUsers() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String username = parts[0].trim();
                    String password = parts[1].trim();
                    users.put(username, password);
                    usernames.add(username);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading user data: " + e.getMessage());
        }
    }

    private static void saveUsers() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE))) {
            for (Map.Entry<String, String> entry : users.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving user data: " + e.getMessage());
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

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Handle login and registration
                String enteredUsername = in.readLine().trim();
                String enteredPassword = in.readLine().trim();

                if (users.containsKey(enteredUsername) && users.get(enteredUsername).equals(enteredPassword)) {
                    username = enteredUsername;
                    out.println("Login successful!");
                } else {
                    out.println("Invalid username or password. Registering your account...");
                    handleUserRegistration(enteredUsername, enteredPassword);
                    username = enteredUsername;
                    out.println("Registration successful! You are now logged in.");
                }

                synchronized (clientWriters) {
                    clientWriters.put(username, out);
                }
                broadcast(username + " has joined the chat.");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("/exit")) {
                        break;
                    } else {
                        broadcast(username + ": " + message);
                    }
                }

                synchronized (clientWriters) {
                    clientWriters.remove(username);
                }
                synchronized (usernames) {
                    usernames.remove(username);
                }
                broadcast(username + " has left the chat.");

            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (clientSocket != null) clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing resources: " + e.getMessage());
                }
            }
        }

        private void handleUserRegistration(String username, String password) {
            synchronized (users) {
                users.put(username, password);
            }
            saveUsers();
        }

        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters.values()) {
                    writer.println(message);
                }
            }
        }
    }
}
