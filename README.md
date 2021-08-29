_Tools for recording mixed reality footage on the Oculus Quest._

> **Warning:** This project is still in early stages so most things aren't done yet.

## Goal

To easily and quickly produce high-quality mixed reality videos using just a Quest and an Android phone (no green screen needed).

## Motivation

I play a lot of Beat Saber on my Quest 2 and I'd like to record it in mixed reality but I don't have any special equipment for this, so I'm trying to use create software to facilitate this instead. Without these tools it is a long and painful process which takes about an hour to produce a video of a single song in mixed reality.

## Overview

- Beat Saber mod
  - Automatically starts the phone video recording when a song starts.
  - Activated and configured from the mod settings menu.
- Android app
  - Starts a web server which listens for recording start/stop requests for video recording.
  - Records video when requested from Beat Saber mod. After recording finishes, a ten second warning sounds for the player to leave the camera frame then a picture is taken of just the background for use in background removal.
  - (TODO) Remove background from recording and receive Oculus MRC feed so PC app is not needed.
  - (TODO) Remove background in real-time or stream to PC for real-time background removal there.
- (TODO) PC app
  - Automatically removes background from video recording and inserts recording into Oculus MRC stream.
- (TODO) Quest app
  - Automatically calibrates MRC.
  - Receives live video from phone and does object recognition on the image to find where the Quest controllers are, then maps this against the position of the controllers in VR and saves the calculated MRC camera position on the headset.

## Installation

No easy way for now. You'll need to set up the dev environments (Android Studio, Beat Saber modding, etc.) for each app and build them from source for now. See the source code to figure out how to use them.
