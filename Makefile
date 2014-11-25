default: test

.PHONY: test default js-dev js-prod

test:
	lein with-profile test cljsbuild test

js-dev:
	lein cljsbuild once

js-prod:
	lein cljsbuild once prod

clean:
	lein clean
