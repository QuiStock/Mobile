PHONY: prepare-environment

prepare-environment:
	lefthook install

unit-tests:
	gradlew testDebugUnitTest

instrumented-tests:
	gradlew connectedDebugAndroidTest

lint-and-formatting-check:
	gradlew spotlessCheck

lint-and-format:
	gradlew spotlessApply

code-smells:
	gradlew detekt

build:
	gradlew assembleDebug

test-coverage:
	gradlew jacocoTestCoverageVerification

check: build code-smells lint-and-formatting-check test-coverage
