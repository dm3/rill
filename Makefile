default: test

.PHONY: test build deploy install clean  publishing rill components css js

clean:
	cd lib/rill && lein clean
	lein do clean, cljsbuild clean

rill:
	make -C lib/rill install

publishing:
	make -C publishing build

uberjars: css js-prod rill
	lein with-profile "login:learning:school-administration:teaching" uberjar

build: publishing uberjars

test:
	make -C lib/rill test
	make -C publishing test
	lein test

upload: build
	SHA=`git rev-parse HEAD` DATE=`date +'%Y%m%d-%H%M%S'`; for PROJECT in "teaching" "learning" "login" "school-administration"; do s3cmd --multipart-chunk-size-mb=5 put "target/$$PROJECT-standalone.jar" "s3://studyflow-server-images/$$SHA/$$DATE-studyflow_$$PROJECT.jar"; done
	make -C publishing upload

css:
	make -C learning css
	make -C login css

js-dev:
	lein cljsbuild once

js-prod:
	lein cljsbuild once prod
