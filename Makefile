DIR=mba210

all:
	javac */*.java

clean:
	rm -f */*.class

$(DIR).zip: all
	zip -r $@ $(DIR)

run:
	java pacworld.PackageWorld $(DIR)
