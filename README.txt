Author: Henry Rhoades
Date: 10/22/2026
Title: Client/Server Chat Service

How to Run:
1. Ensure at Java 8 or higher is installed (java -version)
2. To start the server
	2.1. Navigate to server directory
	2.2. Run the following commands
		2.2.1. make clean
		2.2.2.  make run (auto specified port)
		2.2.3. make run PORT=<port> (manually specified port)
	2.3. To stop the server, use crtl+C
3. To start a client
	3.1. Navigate to client directory
	3.2. Run the following commands
		3.2.1. make clean
		3.2.1. make run
4. Client Commands
	4.1. STOP - Stop the client from running
	4.2. VIEW <DEBUG|USER> - Switch between user view and debug view
	4.3. CONNECT <IP Address> <Port> - Connect to a server
	4.4. REG <username> - Register once connected
	4.5. EXIT - Disconnect the client from the server
	4.6. MESG <content> - Send a global message
	4.7. PMSG <username> <content> - Send a private message to <username>
