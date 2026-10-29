JAVAC = javac
JAVA = java

MAIN_CLASS = ChatClient
SOURCES = ChatClient.java

.PHONY: all run clean

all:
	$(JAVAC) $(SOURCES)

run: all
	$(JAVA) $(MAIN_CLASS)

clean:
	rm -f *.class

