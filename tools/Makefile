jpath := java
jpkg := tts/smartvoice/markup
classpath := $(jpath):../src/main/java:../libs/gson-2.8.5.jar

ifdef JAVA_HOME
JAVA := $(JAVA_HOME)/bin/java
JAVAC := $(JAVA_HOME)/bin/javac
JAR := $(JAVA_HOME)/bin/jar
else
JAVA := java
JAVAC := javac
JAR := jar
endif

all: emoji.jar

emoji: emoji.jar
	mkdir $@
	$(JAVA) -jar emoji.jar cldr $@

emoji.jar: $(addprefix $(jpath)/,manifest.mf $(jpkg)/ExtractCldrAnnotations.class)
	$(JAR) cfm $@ $< -C $(jpath) .

%.class: %.java
	$(JAVAC) -cp $(classpath) -d $(jpath) $<

clean:
	rm -f $(jpath)/$(jpkg)/*.class

distclean: clean
	rm -f emoji.jar
