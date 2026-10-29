/*
Author: Henry Rhoades
Date: 10/18/2026
Title: Client/Server Chat Service
 */


import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

public class ChatClient {

    private volatile Socket socket;
    private volatile PrintWriter out;
    private volatile boolean connected = false;
    private volatile boolean registered = false;
    private volatile boolean registrationPending = false;
    private volatile String username;

    private enum ViewMode {
        USER,
        DEBUG
    }

    private volatile ViewMode viewMode = ViewMode.USER;

    // lock to prevent response from prompting when not needed (e.g. waiting on server response)
    private final Object consoleLock = new Object();
    private volatile boolean waitingForRegistrationResponse = false;
    private volatile CountDownLatch registrationLatch = new CountDownLatch(0);

    public static void main(String[] args){
        new ChatClient().run();
    }

    private void run(){
        try (Scanner console = new Scanner(System.in)) {
            System.out.println("Chat Client");
            System.out.println("Commands: CONNECT <IP> <PORT>, VIEW <USER|DEBUG>, STOP");

            while(true){
                // prompt user if appropriate
                synchronized(consoleLock){
                    printPrompt();
                }

                if(!console.hasNextLine()){
                    disconnect();
                    return;
                }

                String in = console.nextLine().trim();
                if(in.isEmpty()) continue;
                // divide command into max three tokens, first is command
                String[] parts = in.split("\\s+", 3);
                String command = parts[0].toUpperCase();

                if(command.equals("STOP")){
                    disconnect();
                    System.out.println("Client stopped.");
                    return;
                }

                // VIEW command is local and works with either connection state
                if (command.equals("VIEW")){
                    handleView(parts);
                    continue;
                }
                // Disconnected state command handling
                if(!connected){
                    if(command.equals("CONNECT")){
                        handleConnect(parts);
                    }
                    else {
                        System.out.println("Not yet connected. Use CONNECT <IP> <PORT>, VIEW <USER|DEBUG>, or STOP");
                    }
                    continue;
                }
                // Handle EXIT command, log user out
                if(command.equals("EXIT")){
                    // if user had registered, send exit command to server, otherwise just disconnect
                    if (registered){
                        sendMessage("EXIT");
                    } else {
                        // disconnect();
                        System.out.println("Disconnected from server.");
                    }
                    disconnect();
                    continue;
                }

                // enforce registration before allowing other commands
                if(!registered){
                    if (command.equals("REG")){
                        if(parts.length != 2){
                            System.out.println("Usage: REG <username>");
                            continue;
                        }

                        // record registration attempt
                        synchronized(consoleLock){
                            registrationPending = true;
                            waitingForRegistrationResponse = true;
                            username = parts[1];
                            registrationLatch = new CountDownLatch(1);
                        }
                        // send REG command
                        sendMessage("REG " + username);

                        // give time for REG to process, time out if it fails
                        try {
                            boolean received = registrationLatch.await(10, TimeUnit.SECONDS);

                            synchronized (consoleLock){
                                waitingForRegistrationResponse = false;

                                if(!received && registrationPending){
                                    registrationPending = false;
                                    username = null;
                                    System.out.println("Registration timed out.");
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            synchronized (consoleLock){
                                waitingForRegistrationResponse = false;
                                registrationPending = false;
                                registered = false;
                                username = null;
                            }

                            return;
                        }
                    } else {
                        synchronized (consoleLock){
                            System.out.println("Not yet registered. Use REG <username>");
                        }
                    }

                    continue;
                }
                // send command
                sendMessage(in);
            }
        }
    }

    // Allow user to switch between user and debug view
    private void handleView(String[] parts){
        if(parts.length != 2){
            System.out.println("Usage: VIEW <USER|DEBUG>");
            return;
        }
        String requestedMode = parts[1];
        if(requestedMode.equalsIgnoreCase("USER")){
            viewMode = ViewMode.USER;
            System.out.println("Switching to user mode.");
        }
        else if(requestedMode.equalsIgnoreCase("DEBUG")){
            viewMode = ViewMode.DEBUG;
            System.out.println("Switching to debug mode.");
        } else {
            System.out.println("Invalid requested mode. Use USER or DEBUG.");
        }
    }

    // Connect user to server and create socket
    void handleConnect(String[] parts){
        if(parts.length != 3){
            System.out.println("Usage: CONNECT <IP> <PORT>");
            return;
        }

        final int port;

        // ensure port is valid
        try {
            port = Integer.parseInt(parts[2]);

            if(port < 1 || port > 65535){
                System.out.println("Invalid port number.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number.");
            return;
        }

        // create socket and connect print writer
        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(parts[1], port), 5000);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true);

            socket = newSocket;
            out = writer;
            connected = true;

            System.out.println("Connected to " + parts[1] + ":" + port);

            Thread listener = new Thread( () -> listenForMessages(newSocket), "ChatClient-Receiver");

            listener.setDaemon(true);
            listener.start();
        } catch (IOException e) {
            try {
                newSocket.close();
            } catch (IOException ignored) {}

            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    // Handle messages from the server
    private void listenForMessages(Socket connection){
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))
        ){
            String line;

            while((line = in.readLine()) != null){
                displayMessage(line);
            }

            if(socket == connection) System.out.println("\nServer closed the connection.");
        }catch (IOException ex){
            if (connected && socket == connection) {
                System.out.println("\nConnection lost: " + ex.getMessage());
            }
        } finally{
            if (socket == connection) {
                connected = false;
                out = null;
                socket = null;
            }

            try{
                connection.close();
            } catch (IOException ignored) {}
        }
    }

    // Handle server message formatting
    private void displayMessage(String message){
        synchronized (consoleLock){
            String[] parts = message.split("\\s+", 3);

            // Determine if response is for initial registration
            boolean registrationResponse = registrationPending && (parts[0].equals("ACK") || parts[0].equals("ERR"));
            // Record or reject registration attempt
            if(registrationResponse){
                if (parts[0].equals("ACK") && parts.length >= 2){
                        try {
                            Integer.parseInt(parts[1]);
                            registered = true;
                            registrationPending = false;
                        }catch (NumberFormatException e){
                            registered = false;
                            registrationPending = false;
                            username = null;
                        }

                } else {
                    registered = false;
                    registrationPending = false;
                    username = null;
                }
                registrationLatch.countDown();
            }

            System.out.println();

            // debug mode prints complete server protocol
            if (viewMode == ViewMode.DEBUG) {
                System.out.println(message);
            }
            else{
                // user mode formats known server messages for readability
                switch(parts[0]){
                    case "MSG":
                        if (parts.length == 3){
                            System.out.println("[" + parts[1] + "] " + parts[2]);
                        } else {
                            System.out.println(message);
                        }
                        break;
                    case "ACK":
                        displayAcknowledgement(parts, message);
                        break;
                    case "ERR":
                        displayError(parts, message);
                        break;
                    // keep unexpected messages not covered by protocol visible
                    default:
                        System.out.println(message);
                        break;
                }
            }
            // prompt the user for input once user registration processes
            if (connected && !waitingForRegistrationResponse) {
                printPrompt();
            }
        }
    }

    // Handle user mode ACK printing
    private void displayAcknowledgement(String[] parts, String message){
        if (parts.length < 2){
            System.out.println(message);
            return;
        }

        try{
            int userCount = Integer.parseInt(parts[1]);

            if (parts.length == 3 && !parts[2].isEmpty()) {
                System.out.println("\nUsers online (" + userCount + "): " + parts[2]);
            } else {
                System.out.println("\nUsers online: " + userCount);
            }
        } catch (NumberFormatException e){
            System.out.println(message);
        }
    }

    // Handle usermode error printing
    private void displayError(String[] parts, String message){
        if (parts.length < 2){
            System.out.println(message);
            return;
        }
        String errorMessage;
        switch (parts[1]) {
            case "0":
                errorMessage = "That username is already taken.";
                break;
            case "1":
                errorMessage = "Invalid username.";
                break;
            case "2":
                errorMessage = "Invalid private-message recipient.";
                break;
            case "3":
                errorMessage = "Invalid or unexpected command.";
                break;
            case "4":
                errorMessage = "Invalid protocol message or message format.";
                break;
            default:
                System.out.println(message);
                return;
        }
        System.out.println("Error: " + errorMessage);
    }

    // Handle sending message to server
    private void sendMessage(String message){
        PrintWriter writer = out;

        if (writer == null || !connected) {
            System.out.println("Not yet connected. Use CONNECT <IP> <PORT>");
            return;
        }

        writer.print(message + "\n");
        writer.flush();

        if (writer.checkError()) {
            System.out.println("Failed to send message.");
        }
    }

    // Handle user disconnecting
    private void disconnect(){
        registrationPending = false;
        registered = false;
        username = null;
        connected = false;
        PrintWriter writer = out;
        Socket currentSocket = socket;
        out = null;
        socket = null;

        if (writer != null) writer.close();

        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {}
        }
    }

    // Format the prompt depending on connection/registration status
    private void printPrompt() {
        String prompt;

        if (!connected) prompt = "[DISCONNECTED] > ";
        else if (registrationPending) prompt = "[REGISTERING...] > ";
        else if (!registered) prompt = "[CONNECTED] > ";
        else prompt = "[" + username + "] > ";

        System.out.print(prompt);
        System.out.flush();
    }

}
