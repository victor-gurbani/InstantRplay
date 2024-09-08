# Instant Rplay

#### **Instant Rplay** is an Android app that captures audio in the background, allowing quick access to previously recorded audio moments.
*Inspired by Nvidia's Instant Replay* 

## Features

- **Continuous Background Audio Capture**: Continuously record audio in the background, ensuring you always have access to what happened moments before.
  
- **Customizable Audio Settings**: 
  - Adjust audio quality by selecting different sample rates.
  - Configure how long audio should be stored.
  - Display the expected storage size based on chosen parameters, helping you manage storage usage effectively.

- **Quick Save Button**: Save the last (available) 30 minutes of audio with a single click, or hold the button to select a specific time range to save.

- **Auto-Restart on Boot**: Ensures the service is automatically restarted after a device reboot, so recording continues without interruption.

- **Error Handling and Recovery**: Displays a non-intrusive notification to rapidly restart the service in case of errors, ensuring continuous recording.

- **Material UI**: For a modern look and feel.

## Getting Started

### **Download the Latest Release**: 
   Head over to this repository's [Releases](https://github.com/victor-gurbani/InstantRplay/releases) section to download the latest stable version of **Instant Rplay**.

*or*

1. **Clone the Repository** *(for developers)*:
   If you'd like to customize the app or contribute to development, clone the repository:
   ```bash
   git clone https://github.com/victor-gurbani/InstantRplay.git
   cd InstantRplay
   ```

2. **Build and Install**:
   Open the project in Android Studio and build it, or use Gradle to build the APK.

## Permissions:
   The app requires the following permissions:
   - Microphone access: To record audio.
   - Foreground service: To keep capturing audio even when the app is in the background.
   - Storage: To save the recorded audio files.

** Permissions have been used according to best practices**

## Usage

- **Start Recording**: The service starts automatically on boot showing a notification to start capturing audio in the background. You can also start the service manually opening the app.
  
- **Quick Save**: Open the app and tap the save button to store the last 30 minutes of audio. For custom time ranges, long-press the button to open the selection menu.

- **View Audio Size**: In the settings menu, view the storage size based on your current recording settings. Adjust the sample rate and storage duration as needed.

## Contributing

Contributions are welcome! If you have any ideas or bug fixes, please feel free to submit a pull request or open an issue.

### Steps to Contribute:
1. Fork the repository.
2. Create a new branch (`git checkout -b feature-branch`).
3. Commit your changes (`git commit -m 'Add some feature'`).
4. Push to the branch (`git push origin feature-branch`).
5. Open a pull request.

## License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.
