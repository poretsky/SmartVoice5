srcdir := data
destdir := results
languages := Croatian English French German Italian Polish Portuguese Spanish Turkish
length := 7

wordlists := $(addprefix $(srcdir)/,$(languages))
topwords := $(addsuffix .frequent,$(wordlists))
seqs := .heads .middles .tails .left .right .dual
lists := .unique .unique.frequent
resitems := $(lists) $(addsuffix .ranks,$(lists)) $(seqs) $(addsuffix .top,$(seqs)) .stat .frequent.stat

jpath := java
jpkg := tts/smartvoice/markup
classpath := $(jpath):../src/main/java:../libs/base64-2.3.8.jar:../libs/gson-2.8.5.jar

ifdef JAVA_HOME
JAVA := $(JAVA_HOME)/bin/java
JAVAC := $(JAVA_HOME)/bin/javac
JAR := $(JAVA_HOME)/bin/jar
else
JAVA := java
JAVAC := javac
JAR := jar
endif

CXX := g++

UNIQUES := ./research
STATS := ./research -s
RESEARCH := ./research -r $(length)
TEST := $(JAVA) -jar test.jar

ifneq ($(language),)
langopt := -lang $(language)
endif

all: research test.jar

everything: $(languages)

ranks: $(addprefix $(destdir)/,$(addsuffix .unique.ranks,$(languages)) $(addsuffix .unique.frequent.ranks,$(languages)))

English: $(addprefix $(destdir)/English,$(resitems))

German: $(addprefix $(destdir)/German,$(resitems))

French: $(addprefix $(destdir)/French,$(resitems))

Italian: $(addprefix $(destdir)/Italian,$(resitems))

Spanish: $(addprefix $(destdir)/Spanish,$(resitems))

Portuguese: $(addprefix $(destdir)/Portuguese,$(resitems))

Polish: $(addprefix $(destdir)/Polish,$(resitems))

Croatian: $(addprefix $(destdir)/Croatian,$(resitems))

Turkish: $(addprefix $(destdir)/Turkish,$(resitems))

test: test.jar
	$(TEST) $(langopt) $(sort $(wordlists) $(topwords))

check: test.jar
	if [ -z "$(language)" ]; \
	then echo "Language must be specified" ; \
	else $(TEST) $(langopt) $(addprefix $(srcdir)/,$(language) $(language).frequent $(language).freqs) ; \
	fi

audit: test.jar
	if [ -z "$(language)" ]; \
	then echo "Language must be specified" ; \
	else $(TEST) -audit $(language).json $(destdir)/$(language).unique.ranks $(addprefix $(srcdir)/,$(language).freqs $(language).frequent $(language)) ; \
	fi

research: research.cpp
	g++ -s -O2 -o $@ $< -ldb_cxx

test.jar: $(addprefix $(jpath)/,manifest.mf $(jpkg)/Test.class)
	$(JAR) cfm $@ $< -C $(jpath) .

$(destdir):
	mkdir -p $@

clean:
	rm -rf duplications research.db *.hits *.refutes *.misses
	cd $(srcdir) ; rm -rf *.heads.frequent *.middles.frequent *.tails.frequent *.left *.right *.dual *.stat *.top
	cd $(jpath) ; rm -rf backport orestes
	cd $(jpath)/$(jpkg) ; rm -f *.class

distclean: clean
	rm -rf research test.jar $(destdir)
	cd $(srcdir) ; rm -rf *.unique* *.heads *.middles *.tails

$(destdir)/%.unique: $(srcdir)/%.unique | $(destdir)
	cp $< $@

$(destdir)/%.unique.frequent: $(srcdir)/%.unique.frequent | $(destdir)
	cp $< $@

$(destdir)/%.ranks: $(srcdir)/%.ranks | $(destdir)
	cp $< $@

$(destdir)/%: $(srcdir)/% | $(destdir)
	sort -gr $< > $@

$(srcdir)/%.heads.top: research $(srcdir)/%.heads $(srcdir)/%.heads.frequent
	$(UNIQUES) -B $(srcdir)/$*

$(srcdir)/%.middles.top: research $(srcdir)/%.middles $(srcdir)/%.middles.frequent
	$(UNIQUES) -M $(srcdir)/$*

$(srcdir)/%.tails.top: research $(srcdir)/%.tails $(srcdir)/%.tails.frequent
	$(UNIQUES) -E $(srcdir)/$*

$(srcdir)/%.heads.frequent: research $(wordlists)
	$(UNIQUES) -f $* -b $(length) $(wordlists)

$(srcdir)/%.middles.frequent: research $(wordlists)
	$(UNIQUES) -f $* -m $(length) $(wordlists)

$(srcdir)/%.tails.frequent: research $(wordlists)
	$(UNIQUES) -f $* -e $(length) $(wordlists)

$(srcdir)/%.heads: research $(wordlists)
	$(UNIQUES) -b $(length) $(wordlists)

$(srcdir)/%.middles: research $(wordlists)
	$(UNIQUES) -m $(length) $(wordlists)

$(srcdir)/%.tails: research $(wordlists)
	$(UNIQUES) -e $(length) $(wordlists)

$(srcdir)/%.stat: $(srcdir)/% research
	$(STATS) $(length) $<

$(srcdir)/%.unique.frequent $(srcdir)/%.unique.frequent.ranks: research $(srcdir)/%.frequent $(srcdir)/%.freqs $(wordlists)
	$(UNIQUES) -f $* $(wordlists)

$(srcdir)/%.unique $(srcdir)/%.unique.ranks: research $(srcdir)/%.freqs $(wordlists)
	$(UNIQUES) $(wordlists)

$(srcdir)/%.left $(srcdir)/%.right $(srcdir)/%.dual: research $(srcdir)/%.heads $(srcdir)/%.middles $(srcdir)/%.tails $(srcdir)/%.unique
	$(UNIQUES) -p $(srcdir)/$*

$(srcdir)/%.left.top $(srcdir)/%.right.top $(srcdir)/%.dual.top: research $(srcdir)/%.heads $(srcdir)/%.middles $(srcdir)/%.tails $(srcdir)/%.unique.frequent
	$(UNIQUES) -P $(srcdir)/$*

%.class: %.java
	$(JAVAC) -cp $(classpath) -d $(jpath) $<

.PRECIOUS: $(srcdir)/%.heads $(srcdir)/%.middles $(srcdir)/%.tails $(srcdir)/%.unique $(srcdir)/%.unique.frequent
