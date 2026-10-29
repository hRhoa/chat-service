/*
Author: Henry Rhoades
Date: 10/18/2026
Title: Client/Server Chat Service
 */


import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private String username;

    private PrintWriter out;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        // assign socket (received from server class) IO
        try (
                Socket clientSocket = socket;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                clientSocket.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            out = writer;
            server.addClient(this);
            String message;

            // process messages while running
            while ((message = in.readLine()) != null) {
                processMessage(message);
            }
        }
        catch (IOException e) {
            System.err.println("Client connection ended: " + e.getMessage());
        }
        finally {
            // unregister user if they disconnect
            boolean wasRegistered = server.unregisterUser(this);

            if (wasRegistered && username != null) {
                server.broadcast("MSG SERVER " + username + " has left the chat");
            }
        }
    }

    // handle messages from the client
    private void processMessage(String message) {
        // malformed registration command
        if (message.equals("REG")){
            sendMessage("ERR 4");
            return;
        }

        // handle registration request
        if (message.startsWith("REG ")){
            String requestedUsername = message.substring(4);
            // Missing <username> field
            if (requestedUsername.isEmpty()) {
                sendMessage("ERR 4");
                return;
            }
            // User trys to register twice
            if (username != null) {
                sendMessage("ERR 3");
                return;
            }
            // send to server class to register user
            String response = server.registerUser(this, requestedUsername);

            // Record success
            if (response.startsWith("ACK ")) {
                username = requestedUsername;
                sendMessage(response);

                server.broadcastExcept(this, "MSG SERVER " + username + " has joined the chat");
            } else sendMessage(response);
            return;
        }

        // process broadcast requests
        if (message.equals("MESG")) {
            sendMessage("ERR 4");
            return;
        }
        if (message.startsWith("MESG ")) {
            String content = message.substring(5);

            if (content.isEmpty() || content.length() > 512) {
                sendMessage("ERR 4");
                return;
            }

            if (username == null) {
                sendMessage("ERR 3");
                return;
            }

            server.broadcastExcept(this, "MSG " + username + " " + content);
            return;
        }
        // process private message requests
        if (message.equals("PMSG")) {
            sendMessage("ERR 4");
            return;
        }
        if (message.startsWith("PMSG ")){
            String[] parts = message.split(" ", 3);
            if (parts.length != 3 || parts[1].isEmpty()) {
                sendMessage("ERR 2");
                return;
            }

            if (parts[2].isEmpty() || parts[2].length() > 512) {
                sendMessage("ERR 4");
                return;
            }

            if (username == null) {
                sendMessage("ERR 3");
                return;
            }

            String recipient = parts[1];
            String content = parts[2];
            boolean sent = server.sendPrivateMessage(recipient, "MSG " + username + " " + content);
            if (!sent) sendMessage("ERR 2");
            return;
        }
        // process disconnect requests
        if (message.equals("EXIT")){
            // unregistered client must stay connected
            if (username == null){
                sendMessage("ERR 3");
                return;
            }

            // send updated list to exiting client
            String response = server.exitClient(this, username);
            sendMessage(response);

            // Notify everyone except the client leaving
            server.broadcast("MSG SERVER " + username + " has left the chat");

            // close connection
            try {
                socket.close();
            }
            catch (IOException ex) {
                System.err.println("Error closing client connection: " + ex.getMessage());
            }
        }
        // unknown command
        else sendMessage("ERR 3");
    }

    // send message to client
    public void sendMessage(String message) {
        PrintWriter writer = out;
        if (writer != null) {
            writer.print(message + "\n");
            writer.flush();
        }
    }
}
