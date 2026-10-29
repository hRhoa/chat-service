/*
Author: Henry Rhoades
Date: 10/18/2026
Title: Client/Server Chat Service
 */


import java.util.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class ChatServer {

    private final int port;
    // Set of connected clients
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    // Map of clients to usernames
    private final Map<String, ClientHandler> registeredUsers = new HashMap<>();

    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    private volatile boolean running;
    private ServerSocket serverSocket;

    public ChatServer(){port = -1;}
    public ChatServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        // create server socket
        serverSocket = new ServerSocket(port);
        running = true;

        System.out.println("Server started on port " + port);

        try {
            while (running) {
                // allow clients to join
                Socket clientSocket = serverSocket.accept();
                // create ClientHandler for each joining client
                ClientHandler handler = new ClientHandler(clientSocket, this);
                // add handler to execution pool
                clientPool.submit(handler);

                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
            }
        } catch (IOException e) {
            if (running) {
                throw e;
            }
        } finally {
            stop();
        }
    }

    // send a message to every connected client
    public synchronized void broadcast(String message) {
        for (ClientHandler client : registeredUsers.values()) {
            client.sendMessage(message);
        }
    }

    // send a message to everyone except the user so user broadcasts are not echoed to the sender
    public synchronized void broadcastExcept(ClientHandler sender, String message) {
        for (ClientHandler client : registeredUsers.values()) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    // handle private messages
    public synchronized boolean sendPrivateMessage(String recipient, String message) {
        ClientHandler recipientClient = registeredUsers.get(recipient);
        if (recipientClient == null) {
            return false;
        }
        recipientClient.sendMessage(message);
        return true;
    }

    // process a user disconnecting
    public synchronized String exitClient(ClientHandler client, String username) {
        // Remove client's username
        registeredUsers.entrySet().removeIf(entry -> entry.getValue() == client);

        // remove client from connected list
        clients.remove(client);

        // Build updated user list
        String userlist = String.join(" ", registeredUsers.keySet());

        // ACK exit
        if (userlist.isEmpty()) {
            return "ACK " + registeredUsers.size();
        }
        return "ACK " + registeredUsers.size() + " " + userlist;
    }

    public void addClient(ClientHandler client) {
        clients.add(client);
    }

    /* Testing method
    public void removeClient(ClientHandler client) {
        clients.remove(client);
    }
     */

    // stop the server
    public void stop() throws IOException {
        running = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        clientPool.shutdownNow();
    }

    // process user registration
    public synchronized String registerUser(ClientHandler client, String username) {
        // username is invalid
        if (username == null || username.isEmpty() || username.chars().anyMatch(Character::isWhitespace) || username.length() > 32) return "ERR 1";
        // username is taken
        if (registeredUsers.containsKey(username)) return "ERR 0";
        registeredUsers.put(username, client);
        String userlist = String.join(", ", registeredUsers.keySet());
        String response = "ACK " + registeredUsers.size();
        if (!userlist.isEmpty()) response += " " + userlist;

        // Moved join message to client handler so it is sent after receiving ACK
        // broadcast(username + " has joined the chat");
        return response;
    }

    // remove user from registered user list and report if registered
    public synchronized boolean unregisterUser(ClientHandler client) {
        boolean wasRegistered = registeredUsers.entrySet().removeIf(entry -> entry.getValue() == client);
        clients.remove(client);
        return wasRegistered;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java ChatServer <port>");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + args[0]);
            return;
        }
        if (port < 1 || port > 65535) {
            System.err.println("Invalid port: " + port);
            return;
        }
        ChatServer server = new ChatServer(port);
        server.start();
    }
}
