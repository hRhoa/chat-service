JAVAC = javac
JAVA = java

MAIN_CLASS = ChatServer
SOURCES = ChatServer.java ClientHandler.java
PORT ?= 23794

.PHONY: all run clean

all:
	$(JAVAC) $(SOURCES)

run: all
	$(JAVA) $(MAIN_CLASS) $(PORT)

clean:
	rm -f *.class
