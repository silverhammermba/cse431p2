DIR=mba210
AGENTS=4
PACKS=20
DESTS=3
SIZE=50

all:
	javac */*.java

clean:
	rm -f */*.class

$(DIR).zip: all
	zip -r $@ $(DIR)

run:
	java pacworld.PackageWorld $(DIR) $(AGENTS) $(PACKS) $(DESTS) $(SIZE)

crowdrun: SIZE=12
crowdrun: run

swarmrun: AGENTS=50
swarmrun: DESTS=1
swarmrun: PACKS=100
swarmrun: run

uniqrun: PACKS=50
uniqrun: DESTS=8
uniqrun: run
