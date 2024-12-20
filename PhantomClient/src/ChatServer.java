import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int SERVER_PORT = 12345;
    private static final Map<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    private static final Set<String> usernames = new HashSet<>();
    private static final Map<String, String> users = new HashMap<>();
    private static final Map<String, Set<String>> activeDMs = new HashMap<>();
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

                out.println("Do you want to register? (yes/no)");
                String response = in.readLine().trim().toLowerCase();
                if ("yes".equals(response)) {
                    registerUser();
                } else {
                    loginUser();
                }

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("/exit")) {
                        break;
                    } else if (message.startsWith("/dm ")) {
                        String[] parts = message.split(" ", 3);
                        if (parts.length < 3) {
                            out.println("Usage: /dm <username> <message>");
                        } else {
                            String targetUser = parts[1];
                            String dmMessage = parts[2];
                            sendDirectMessage(targetUser, dmMessage);
                        }
                    } else if (message.startsWith("/close ")) {
                        String[] parts = message.split(" ", 2);
                        if (parts.length < 2) {
                            out.println("Usage: /close <username>");
                        } else {
                            closeDM(parts[1]);
                        }
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
                }
            }
        }

        private void registerUser() throws IOException {
            out.println("Enter your desired username: ");
            String username = in.readLine().trim();

            synchronized (usernames) {
                if (usernames.contains(username)) {
                    out.println("Username is already taken. Try another one.");
                    return;
                }
                usernames.add(username);
            }

            out.println("Enter your password: ");
            String password = in.readLine().trim();

            synchronized (users) {
                users.put(username, password);
            }

            saveUsers();
            out.println("Registration successful. You can now log in.");
        }

        private void loginUser() throws IOException {
            out.println("Enter your username: ");
            String enteredUsername = in.readLine().trim();

            out.println("Enter your password: ");
            String enteredPassword = in.readLine().trim();

            if (users.containsKey(enteredUsername) && users.get(enteredUsername).equals(enteredPassword)) {
                username = enteredUsername;
            } else {
                out.println("Invalid username or password. Disconnecting...");
                clientSocket.close();
                return;
            }

            synchronized (clientWriters) {
                clientWriters.put(username, out);
            }
            out.println("You are logged in as " + username);
            broadcast(username + " has joined the chat.");
        }

        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters.values()) {
                    writer.println(message);
                }
            }
        }

        private void sendDirectMessage(String targetUser, String message) {
            PrintWriter targetWriter = clientWriters.get(targetUser);
            if (targetWriter != null) {
                targetWriter.println("[DM from " + username + "]: " + message);
                activeDMs.computeIfAbsent(username, k -> new HashSet<>()).add(targetUser);
                activeDMs.computeIfAbsent(targetUser, k -> new HashSet<>()).add(username);
            } else {
                out.println("User " + targetUser + " is not online.");
            }
        }

        private void closeDM(String targetUser) {
            if (activeDMs.containsKey(username) && activeDMs.get(username).contains(targetUser)) {
                activeDMs.get(username).remove(targetUser);
                activeDMs.get(targetUser).remove(username);
                out.println("Closed DM with " + targetUser);
            } else {
                out.println("No active DM session with " + targetUser);
            }
        }
    }
}
