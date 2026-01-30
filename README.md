# MIDI Player

A modern JavaFX application for accessing and playing any MIDI instrument available on your device. This application demonstrates how to build JavaFX applications with a custom modern theme (AtlantaFX) that can be compiled into native executables using GraalVM and Gluon.

## Features

- **Access All MIDI Instruments**: Browse and play any of the 128 standard MIDI instruments available on your system
- **Interactive Controls**: 
  - Play individual notes on specific instruments
  - Loop through all instruments on a single note
  - Loop through all notes on a single instrument
  - Loop through all combinations of instruments and notes
  - Play random instrument and note combinations
- **Modern UI**: Built with AtlantaFX's PrimerDark theme for a sleek, modern appearance
- **Native Compilation**: Can be compiled to native executables using GraalVM and Gluon for improved startup time and reduced memory footprint

## Prerequisites

- **Java 21** or higher
- **Maven** 3.6+
- **GraalVM** (optional, for native compilation)

## Getting Started

### Clone the Repository

```bash
git clone https://github.com/UnknownCoder56/music.git
cd music
```

### Build and Run

#### Running with Maven

```bash
mvn clean javafx:run
```

This will compile the application and launch it using the JavaFX Maven plugin.

#### Building a JAR

```bash
mvn clean package
```

## Native Compilation with GraalVM

This application can be compiled into a native executable using GraalVM and Gluon's GluonFX plugin.

### Prerequisites for Native Compilation

1. **Install GraalVM**: Download and install GraalVM from [GraalVM website](https://www.graalvm.org/)
2. **Set GRAALVM_HOME**: Update the `graalvmHome` path in `pom.xml` to point to your GraalVM installation:

```xml
<graalvmHome>/path/to/your/graalvm-installation</graalvmHome>
```

### Build Native Image

```bash
mvn clean gluonfx:build
```

### Run Native Image

```bash
mvn gluonfx:run
```

The native executable will provide faster startup times and lower memory consumption compared to running on the JVM.

## Usage

1. **Select an Instrument**: Enter a number between 0-127 in the "Instrument" field. The list of available instruments is displayed in the text area.

2. **Select a Note**: Enter a MIDI note number between 0-127 in the "Note" field (Middle C is 60).

3. **Set Duration**: Enter the tick duration in the "Tick" field to control how long the note plays.

4. **Play Options**:
   - **Play**: Play the selected instrument and note once
   - **Loop Instruments**: Cycle through all 128 instruments playing the current note
   - **Loop Notes**: Cycle through all 128 notes on the current instrument
   - **Loop All**: Play all combinations of instruments and notes
   - **Loop Random**: Play 5 random instrument/note combinations

5. **Wait Checkbox**: When enabled, waits for each sound to finish before playing the next one. Disable for simultaneous playback.

## Technology Stack

- **JavaFX 21**: Modern UI framework for Java
- **AtlantaFX**: Modern theme library providing the PrimerDark theme
- **Java MIDI API**: For synthesizing and playing MIDI sounds
- **GraalVM**: For native compilation support
- **Gluon**: For building native JavaFX applications

## Project Structure

```
music/
├── src/
│   └── main/
│       ├── java/
│       │   ├── module-info.java
│       │   └── com/uniqueapps/music/
│       │       ├── MusicApplication.java    # Main application class
│       │       ├── HomeController.java      # UI controller
│       │       └── Player.java              # MIDI player implementation
│       └── resources/
│           └── com/uniqueapps/music/
│               └── home.fxml                # UI layout definition
├── pom.xml                                  # Maven configuration
└── README.md
```

## Building with Modern JavaFX Themes

This application demonstrates how to integrate modern themes into JavaFX applications:

1. **Add AtlantaFX dependency** in `pom.xml`:
```xml
<dependency>
    <groupId>io.github.mkpaz</groupId>
    <artifactId>atlantafx-base</artifactId>
    <version>2.0.1</version>
</dependency>
```

2. **Apply the theme** in your application:
```java
Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

Arpan Chatterjee

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
