<a id="readme-top"></a>

<p align="center">
  <a href="HOW_TO_USE.tr.md">Türkçe</a> | <a href="../README.md">Back to README</a>
</p>

# How to use EasyAndroid

EasyAndroid backs up, restores and debloats apps on a rooted Android phone, without a PC. This page walks through every screen.

## Contents

1. [Before you start](#before-you-start)
2. [Home](#home)
3. [Back up apps](#back-up-apps)
4. [Restore apps](#restore-apps)
5. [System backup](#system-backup)
6. [Folders, messages, call logs and wallpapers](#folders-messages-call-logs-and-wallpapers)
7. [Debloat](#debloat)
8. [Wireless debugging and EasyADB](#wireless-debugging-and-easyadb)
9. [Moving backups to a PC](#moving-backups-to-a-pc)
10. [Settings](#settings)
11. [Good to know](#good-to-know)

## Before you start

* Android 10 or newer.
* Root with Magisk, KernelSU or one of their forks. Allow root for EasyAndroid when your root manager asks. Without root, Home opens but every action is dimmed.
* Backups go to `EasyAndroid/Backups` on the internal storage unless you choose another folder in Settings.

## Home

* **Overview:** free internal storage (the arrow opens the backup folder settings), root status, and Wireless Debugging on one line. Tap that line for the details.
* **Quick actions:** one line for each kind (Apps, Messages, Call Logs, Folders, Wallpapers, System) with how many backups there are and how long ago the last one was made, in orange once it is older than 30 days. A tap opens a sheet with everything you can do with that kind: back up, restore the newest backup, **Show backups** (opens the list in **Restore**) and **Connect with EasyADB**. Choices that would do nothing are dimmed.

## Back up apps

* **Back up apps** opens the app list with nothing ticked. Tick the apps you want, or use the select-all button to tick every app except the ones the phone cannot do without (System UI, Settings, Shell, EasyAndroid itself and packages the UAD-NG list marks as unsafe). Choose what to include in the dialog: app data, OBB files, `Android/data` and compression. The dialog tells you how many system apps are included. Data is only consistent when an app is closed, so the selected apps are closed while they are backed up.
* The **Apps** sheet also has **Back up pending apps**, **updated apps** and **Refresh existing backups**, which pick the apps for you: apps without a backup, apps whose installed version is newer than their backup, and apps that already have a backup.
* Every file is stored with a SHA-256 checksum and the manifest is written last, so a backup that was cut short is marked as incomplete.

## Restore apps

Open **Restore** (or tap **Apps** on Home and use the restore choices of its sheet).

* **Restore all apps**, **Restore missing apps** and **Restore new versions** in the **Apps** sheet open Restore with the matching backups already ticked. Press **Restore** to continue.
* Choose what to restore: the app, its data, OBB files and permissions. If an app is already installed you can overwrite it or skip it, for one app or for all.
* Every checksum is verified before anything on the phone changes.
* Restore your apps first and the system backup after them, because default apps need their packages to exist.

## System backup

Tap **System** on Home and choose **Back up system settings**.

* Pick settings, default apps and Wi-Fi networks one by one, or a whole group at once. Wi-Fi networks are not selected by default because their passwords are saved without encryption in the backup folder.
* Saved system backups are listed in **Restore**. **Restore** shows only what differs from the phone. Settings that mean the same on every ROM are ticked, the rest may belong to the old ROM and are left for you to choose.
* Identity, security and state settings (such as device IDs, lock screen and SIM settings) are never saved or restored.

## Folders, messages, call logs and wallpapers

These are saved as copies of files, one backup for each time, and listed under their own chip in **Restore**. Their sheets on Home do the same in fewer taps.

* **Folders:** tap **Choose folders and back up** in its sheet on Home or in **Folders** under **Restore**. The Android folder and the backup folder are never included.
* **Messages**, **Call logs** and **Wallpapers:** tap **Back up now** in their sheet on Home or under their chip in **Restore**. It asks first. The database or wallpaper files are copied as they are, so single messages or calls cannot be picked.
* **Restore** replaces the current files with the ones in the backup. Restore messages and call logs only on the same Android version and ROM, and expect to restart the phone before a restored wallpaper shows.

## Debloat

* The list comes from the UAD-NG project with a safety level for each package, shown with a color: **Recommended** (green), **Advanced** (yellow), **Expert** (orange), **Unsafe** (red) and **Not listed** (grey).
* Select packages, then **Disable** or **Uninstall**. All data of a package is deleted before it is disabled or uninstalled, so that nothing is left behind. This cannot be undone. The package itself can be brought back with **Removed** and **Bring back**, but its data cannot. Packages marked unsafe are locked.
* The three-dot menu next to the search box exports and imports a **Debloat profile**. A profile is a file with your removed apps, so the same cleanup can be applied on a new ROM or in EasyADB.

## Wireless debugging and EasyADB

* Turn on **Wireless Debugging** with the switch on the Overview card (Android 11 or newer). Its line shows the connect address and whether a PC is connected, green once one is. Tap the line for the address, the pairing buttons and more. Turning it off while a PC is connected asks first.
* **Pair with QR** and **Pair with code** open the matching screens of Android, so you do not have to dig through Developer options.
* In EasyADB on the PC, open the Wireless tab, pair, and connect to the address shown on the phone.
* **Connect with EasyADB** at the end of every sheet on Home opens the same details and turns Wireless Debugging on if it was off.

## Moving backups to a PC

* Copy `EasyAndroid/Backups` from the internal storage to the PC before you format the phone, and copy it back after the new ROM is installed. EasyADB can open the folder directly.
* A single backup can be exported as one `.easybckp` file from **Restore** and imported again with **Import .easybckp**.
* Uncompressed backups use EasyADB's format in both directions. zstd compressed backups are smaller but EasyADB cannot read them yet.

## Settings

* **Appearance and language:** theme (System, Light, Dark, Pure black) and the app language (English or Turkish, Android 13 or newer).
* **Storage and security:** backup folder (internal storage or SD card), how much space the backups use, and **Delete all backups**, which asks first and cannot be undone.
* **Advanced tools:** **Recent job reports** lists the results of your last 50 jobs, and **Restart app** reloads EasyAndroid.
* **Notifications:** opens the notification settings of Android for EasyAndroid.
* **Help and info:** this page, contact, the repository and the about dialog.

## Good to know

* Apps that tie their data to the phone with the Android Keystore, such as banking apps and authenticators, may ask you to sign in again after a restore.
* Restoring Wi-Fi networks needs Android 11 or newer. Enterprise and WEP networks are not saved.
* A custom backup folder has to be on internal storage or an SD card. Cloud folders have no path that root commands could use.

<p align="right">(<a href="#readme-top">back to top</a>)</p>
