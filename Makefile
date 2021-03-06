default: test

.PHONY: test default install

test:
	lein test
	lein install
	make -C rill-psql test

install:
	lein install

clean:
	lein clean

jar:
	lein jar
	lein install
	cd rill-psql && lein jar
