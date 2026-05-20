JAVA_HOME    := $(HOME)/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
ANDROID_HOME := $(HOME)/Android/Sdk
export JAVA_HOME
export ANDROID_HOME

GRADLEW    := ./gradlew
SDKMANAGER := $(ANDROID_HOME)/cmdline-tools/latest/bin/sdkmanager
AVDMANAGER := $(ANDROID_HOME)/cmdline-tools/latest/bin/avdmanager
ADB        := $(ANDROID_HOME)/platform-tools/adb
EMULATOR   := $(ANDROID_HOME)/emulator/emulator

SYSIMG   := system-images;android-36.1;google_apis_playstore;x86_64
AVD_NAME := navink
APK      := app/build/outputs/apk/debug/app-debug.apk

.PHONY: help build deploy avd emulator install run

help:
	@echo "make build      build debug APK"
	@echo "make deploy     build + print sideload path"
	@echo "make avd        create '$(AVD_NAME)' AVD (idempotent)"
	@echo "make emulator   start emulator, wait for boot"
	@echo "make install    build + adb install to running emulator/device"
	@echo "make run        avd + emulator + install in one shot"

build:
	$(GRADLEW) assembleDebug

deploy: build
	@echo "APK: $$(pwd)/$(APK)"

avd:
	@$(AVDMANAGER) list avd | grep -q "Name: $(AVD_NAME)" \
	  && echo "AVD '$(AVD_NAME)' already exists" \
	  || (echo no | $(AVDMANAGER) create avd \
	        --name "$(AVD_NAME)" \
	        --package "$(SYSIMG)" \
	        --device "pixel_6")

emulator:
	@$(ADB) devices | grep -q "emulator" \
	  && echo "Emulator already running" \
	  || { $(EMULATOR) -avd $(AVD_NAME) -no-snapshot-save -no-audio & \
	       echo "Waiting for boot (~60s)..."; \
	       $(ADB) wait-for-device; \
	       $(ADB) shell 'until [ $$(getprop sys.boot_completed) = 1 ]; do sleep 2; done'; \
	       echo "Emulator ready"; }

install: build
	$(ADB) install -r $(APK)

run: avd emulator install
