# video2ndi
A Java demonstration of sending a video file over NDI(tm) using Devolay and JavaCpp-FFmpeg

This project almost certainly is not the best way to transmit a video file over NDI.
It mostly serves just as a demonstration for how such a program would be constructed.
An ideal solution would use C to directly interface with FFmpeg and NDI,
and perhaps even preemtively encode the video files to be more friendly to NDI.

For more context, see [https://github.com/WalkerKnapp/devolay/issues/18](https://github.com/WalkerKnapp/devolay/issues/18).

## Usage
```
./gradlew run --args="[path to video file]"
```