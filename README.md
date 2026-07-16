# EcuTweaker

[![Android CI](https://github.com/cedricp/ecutweaker/actions/workflows/android.yml/badge.svg)](https://github.com/cedricp/ecutweaker/actions/workflows/android.yml)
[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=cedricpaille%40gmail%2ecom&lc=CY&item_name=codetronic&currency_code=EUR&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted)
[![Discord](https://img.shields.io/discord/1117970325267820675?label=Discord&style=flat-square)](https://discord.gg/cBqDh9bTHP)

EcuTweaker is a tool to create your own ECU parameters screens and connect to a CAN network with a cheap ELM327 interface.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Supported Protocols](#supported-protocols)
- [Database Setup](#database-setup)
- [Building from Source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Multi-protocol support**: Bluetooth, WiFi, and USB serial communication
- **OBD-II/CAN protocols**: Support for CAN, KWP2000, and ISO8 protocols
- **ECU Identification**: Automatic ECU detection and identification
- **Custom Screens**: Create and customize parameter display screens
- **DTC Reading/Clearing**: Read and clear diagnostic trouble codes
- **Data Logging**: Session logging for debugging and analysis

## Requirements

- Android 5.0 (API 21) or higher
- An ELM327-compatible OBD-II adapter (Bluetooth, WiFi, or USB)
- ECU database file (`ecu.zip`) - not included in the application

## Installation

1. Download the latest APK from the [Releases](https://github.com/cedricp/ecutweaker/releases) page
2. Install the APK on your Android device
3. Place the `ecu.zip` database file in your device's storage (see [Database Setup](#database-setup))

## Usage

### Initial Setup

1. Launch EcuTweaker
2. The app will automatically search for the `ecu.zip` database file
3. If not found, you will be prompted to provide the file location

### Connecting to an ECU

1. Select your connection type (Bluetooth, WiFi, or USB)
2. Choose your device from the list
3. The app will automatically initialize the connection and identify the ECU

### Reading Parameters

1. After successful connection, select a screen from the available categories
2. Parameters will be displayed in real-time
3. Use the refresh button to update values manually

### Clearing DTCs

1. Navigate to the DTC screen
2. Press the "Clear DTC" button
3. Confirm the action in the dialog

## Project Structure

```
ecutweaker/
├── EcuTweaker/           # Main application module
│   ├── src/main/java/
│   │   └── org/quark/dr/canapp/
│   │       ├── MainActivity.java        # Main UI and connection handling
│   │       ├── ScreenActivity.java      # Screen display and parameter updates
│   │       ├── ElmBase.java             # Abstract base for ELM327 communication
│   │       ├── ElmBluetooth.java        # Bluetooth implementation
│   │       ├── ElmWifi.java             # WiFi implementation
│   │       ├── ElmUsbSerial.java        # USB serial implementation
│   │       ├── DeviceListActivity.java  # Device selection UI
│   │       ├── UsbDeviceActivity.java   # USB device selection UI
│   │       └── CustomAdapter.java       # Custom spinner adapter
│   ├── proguard-rules.pro
│   ├── debug.keystore
│   └── release.keystore
├── ecu/                   # ECU data model library
│   ├── src/main/java/
│   │   └── org/quark/dr/ecu/
│   │       ├── Ecu.java                 # ECU data model and parsing
│   │       ├── EcuDatabase.java         # Database management
│   │       ├── IsoTPEncode.java         # ISO-TP frame encoding
│   │       ├── IsoTPDecode.java         # ISO-TP frame decoding
│   │       ├── ZipFileSystem.java       # Optimized ZIP file access
│   │       ├── Layout.java              # Screen layout model
│   │       └── ProjectData.java         # Project configuration data
│   ├── proguard-rules.pro
│   ├── debug.keystore
│   └── release.keystore
├── usbserial/             # USB serial driver library
│   ├── src/main/java/
│   │   └── org/quark/dr/usbserial/
│   │       ├── BuildInfo.java           # Library version info
│   │       ├── driver/
│   │       │   ├── UsbSerialDriver.java # Driver interface
│   │       │   ├── UsbId.java           # USB vendor/product IDs
│   │       │   ├── CdcAcmSerialDriver.java # CDC/ACM driver
│   │       │   └── FtdiSerialDriver.java   # FTDI driver
│   │       └── util/
│   │           ├── SerialInputOutputManager.java # Async I/O manager
│   │           └── HexDump.java         # Hex conversion utilities
│   ├── proguard-rules.pro
│   ├── debug.keystore
│   └── release.keystore
├── build.gradle           # Root Gradle configuration
├── settings.gradle        # Project settings
├── gradle.properties      # Gradle properties
├── gradlew                # Gradle wrapper (Unix)
├── gradlew.bat            # Gradle wrapper (Windows)
├── gen_keys.bat           # Key generation script
├── license.txt            # License file
└── .github/workflows/     # CI/CD workflows
```

## Configuration

### Connection Types

| Type | Description | Default Port |
|------|-------------|--------------|
| Bluetooth | Standard SPP Bluetooth connection | N/A |
| WiFi | TCP/IP connection to WiFi OBD adapter | 35000 |
| USB | Direct USB serial connection | 38400 baud |

### Permissions

The application requires the following permissions:

- `BLUETOOTH_CONNECT` - For Bluetooth device communication
- `BLUETOOTH_SCAN` - For discovering Bluetooth devices
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` - For WiFi network detection
- `WRITE_EXTERNAL_STORAGE` - For log file export (deprecated, will be migrated)

## Supported Protocols

| Protocol | Description |
|----------|-------------|
| CAN | Controller Area Network (ISO 11898) |
| KWP2000 | Keyword Protocol 2000 (ISO 14230-4) |
| ISO8 | ISO 9141-2 protocol |

## Database Setup

**Important**: The ECU database (`ecu.zip`) is not included with the application.

### Database Location

The app searches for `ecu.zip` in the following locations:

1. Application's internal storage directory
2. External storage root
3. `/storage` directory
4. `/mnt` directory

### Database Format

The database is a ZIP file containing:
- `db.json` - Main database with ECU definitions
- Individual ECU JSON files referenced in `db.json`

## Building from Source

### Prerequisites

- Android Studio Flamingo or later
- Android SDK 34
- Java 17

### Build Commands

```bash
# Clone the repository
git clone https://github.com/cedricp/ecutweaker.git
cd ecutweaker

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test
```

### Gradle Configuration

The project uses the following SDK versions (defined in `gradle.properties`):

- `compileSdkVersion`: 34
- `minSdkVersion`: 21
- `targetSdkVersion`: 34

## Troubleshooting

### Common Issues

1. **Cannot find ecu.zip**
   - Ensure the database file is placed in the correct location
   - Check file permissions on the storage

2. **Bluetooth connection fails**
   - Verify Bluetooth is enabled
   - Check that the device is paired
   - Ensure `BLUETOOTH_CONNECT` permission is granted

3. **WiFi connection fails**
   - Verify the OBD adapter is connected to the same network
   - Check that the SSID contains "OBD", "ELM", "ECU", or "LINK"
   - Ensure location permissions are granted

4. **No data received**
   - Check the baud rate settings
   - Verify the correct protocol is selected
   - Ensure the ECU is powered on

### Log Files

Log files are stored in:
- Internal storage: `/Android/data/org.quark.dr.canapp/files/log.txt`
- Exported logs: `Downloads/logs_[timestamp].txt`

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Bug Reports

When reporting bugs, please include:
- Device model and Android version
- Connection type (Bluetooth/WiFi/USB)
- Log file from the app
- Steps to reproduce the issue

Add `[Bug]` to the issue title for quick identification.

## License

This project is licensed under the GNU Lesser General Public License v2.1 - see the [LICENSE](LICENSE) file for details.

**Legal Disclaimer**: This project is in no way affiliated with, authorized, maintained, sponsored, or endorsed by any vehicle manufacturer. This is an independent and unofficial project for educational use ONLY. Do not use for any other purpose than education, testing, and research.

## Acknowledgments

- Based on [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) by Mike Wakerly
- Inspired by [ddt4all](https://github.com/cedricp/ddt4all) project

## **🎯 Final Notes**

**Happy CAN-Hacking!** 🚗💻

### **🤝 Support the Project**
To make this application more reliable and add support for new devices, hardware donations are needed. Please consider contributing:
- **💰 Financial donations** via [PayPal](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=cedricpaille%40gmail%2ecom&lc=CY&item_name=codetronic&currency_code=EUR&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted)
- **🔧 Hardware donations** (OBD-II adapters, cables, ECU devices)
- **🐛 Bug reports** and patches
- **📖 Documentation** improvements
- **🌍 Translation** contributions

### **📞 Community & Support**
- **Discord**: [Join our community](https://discord.gg/cBqDh9bTHP) for real-time support
- **GitHub Issues**: Technical problems and bug reports
- **GitHub Discussions**: Feature requests and general discussions