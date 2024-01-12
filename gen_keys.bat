@echo off
keytool -genkey -v -keystore EcuTweaker/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
keytool -genkey -v -keystore EcuTweaker/release.keystore -storepass android -alias androidreleasekey -keypass android -keyalg RSA -keysize 2048 -validity 10000
keytool -genkey -v -keystore ecu/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
keytool -genkey -v -keystore ecu/release.keystore -storepass android -alias androidreleasekey -keypass android -keyalg RSA -keysize 2048 -validity 10000
keytool -genkey -v -keystore usbserial/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
keytool -genkey -v -keystore usbserial/release.keystore -storepass android -alias androidreleasekey -keypass android -keyalg RSA -keysize 2048 -validity 10000