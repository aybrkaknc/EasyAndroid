<a id="readme-top"></a>

<p align="center">
  <a href="README.tr.md">Türkçe</a>
</p>

[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![License][license-shield]][license-url]

<br />
<div align="center">
  <img src="icons/icon.png" alt="EasyAndroid" width="96" height="96">
  <h3 align="center">EasyAndroid</h3>

  <p align="center">
    A root app that backs up, restores and debloats Android phones on the phone itself. The companion of <a href="https://github.com/aybrkaknc/EasyADB">EasyADB</a>.
  </p>
</div>



<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#features">Features</a></li>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#development">Development</a></li>
    <li><a href="#roadmap">Roadmap</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
    <li><a href="#acknowledgments">Acknowledgments</a></li>
  </ol>
</details>



## About The Project

Changing custom ROMs often means downloading every app again and setting everything up from scratch. EasyAndroid saves apps with their data, permissions and settings on the phone and brings them back after a flash.

### Features

* **Backup and restore:** APKs and split APKs, app data, device protected data, OBB and, optionally, `Android/data`. Select many apps at once. Every file has a SHA-256 checksum and is verified before anything on the phone changes.
* **Permissions:** Runtime permissions, special access and battery exemptions come back with the app.
* **Works with EasyADB:** Backups use EasyADB's format in both directions, zstd ones too. Export and import single `.easybckp` files.
* **Straight to a PC:** Choose PC instead of this phone in any backup sheet. EasyADB on the PC receives apps and folders as they are read, and copies messages, call logs, wallpapers and system settings once they are made. It can also ask this phone for them.
* **Debloat:** Descriptions and safety levels from the UAD-NG list, batch disable or uninstall, bring back, and profiles that can be shared with EasyADB. Packages marked unsafe are locked.
* **System:** Saves system settings, default apps and Wi-Fi networks, and lists only what differs before restoring.
* **Wireless debugging:** Turn it on or off, see the connect address and open the QR or pairing code screen with one tap, to pair with EasyADB on a PC.
* **Comfort:** English and Turkish, Material You, progress, cancel and a report at the end.

### Built With

* [![Kotlin][Kotlin-shield]][Kotlin-url]
* [![Jetpack Compose][Compose-shield]][Compose-url]
* [![libsu][libsu-shield]][libsu-url]
* [![zstd-jni][zstd-shield]][zstd-url]

<p align="right">(<a href="#readme-top">back to top</a>)</p>



## Getting Started

### Prerequisites

* Android 10 or newer
* Root with Magisk, KernelSU or one of their forks

### Installation

1. Build the APK
   ```sh
   ./gradlew assembleDebug
   ```
2. Install it
   ```sh
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
3. Open the app and allow root access.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



## Usage

Before flashing:

See [How to use](docs/HOW_TO_USE.md) for every screen. In short:

1. **Apps:** select the apps and back them up.
2. **System:** create a system backup.
3. **Debloat:** save the removed apps as a profile.
4. Copy `EasyAndroid/Backups` from the internal storage to a PC if the storage will be formatted.

After flashing, copy the folder back, install EasyAndroid and restore from the same tabs.

Things to know:

* Apps that tie their data to the phone with the Android Keystore, such as banking apps and authenticators, may ask you to sign in again.
* Wi-Fi passwords are stored unencrypted in the backup folder. They are off by default. Restoring them needs Android 11 or newer. Enterprise and WEP networks are not saved.
* A custom backup folder has to be on internal storage or an SD card. Cloud folders have no path that root commands could use.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



## Development

```text
app/src/main/java/com/aybrkaknc/easyandroid/
  core/   backup, restore, debloat and system logic; reaches the device only through Root
  job/    running jobs, notification
  ui/     Compose screens
```

Unit tests:
```sh
./gradlew testDebugUnitTest
```

The engine tests run against an emulator when these are set, and are skipped otherwise:
```sh
EA_SERIAL=emulator-5554 EA_PACKAGE=com.aybrkaknc.easyandroid ./gradlew testDebugUnitTest --rerun
```

<p align="right">(<a href="#readme-top">back to top</a>)</p>



## Roadmap

- [ ] Scheduled backups

<p align="right">(<a href="#readme-top">back to top</a>)</p>



## Contributing

Contributions are welcome. Open an issue first for larger changes, then send a pull request.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



## License

Distributed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for more information.

EasyAndroid bundles package data from [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation), GPL-3.0.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



## Contact

Ayberk - [@aybrkaknc](https://github.com/aybrkaknc)

<p align="right">(<a href="#readme-top">back to top</a>)</p>



## Acknowledgments

* [Best-README-Template](https://github.com/aybrkaknc/Best-README-Template)
* [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)
* [EasyADB](https://github.com/aybrkaknc/EasyADB)

<p align="right">(<a href="#readme-top">back to top</a>)</p>


<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[contributors-shield]: https://img.shields.io/github/contributors/aybrkaknc/EasyAndroid.svg?style=for-the-badge
[contributors-url]: https://github.com/aybrkaknc/EasyAndroid/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/aybrkaknc/EasyAndroid.svg?style=for-the-badge
[forks-url]: https://github.com/aybrkaknc/EasyAndroid/network/members
[stars-shield]: https://img.shields.io/github/stars/aybrkaknc/EasyAndroid.svg?style=for-the-badge
[stars-url]: https://github.com/aybrkaknc/EasyAndroid/stargazers
[issues-shield]: https://img.shields.io/github/issues/aybrkaknc/EasyAndroid.svg?style=for-the-badge
[issues-url]: https://github.com/aybrkaknc/EasyAndroid/issues
[license-shield]: https://img.shields.io/github/license/aybrkaknc/EasyAndroid.svg?style=for-the-badge
[license-url]: https://github.com/aybrkaknc/EasyAndroid/blob/main/LICENSE
[Kotlin-shield]: https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white
[Kotlin-url]: https://kotlinlang.org/
[Compose-shield]: https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white
[Compose-url]: https://developer.android.com/compose
[libsu-shield]: https://img.shields.io/badge/libsu-000000?style=for-the-badge
[libsu-url]: https://github.com/topjohnwu/libsu
[zstd-shield]: https://img.shields.io/badge/zstd--jni-FF4F00?style=for-the-badge
[zstd-url]: https://github.com/luben/zstd-jni
