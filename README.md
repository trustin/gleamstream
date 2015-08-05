#Moonlight

[Moonlight](https://github.com/moonlight-stream) is an open source implementation of NVIDIA's GameStream, as used by the NVIDIA Shield, but built for Linux/OS X/Windows.

Moonlight-pc allows you to stream your full collection of Steam games from your powerful desktop to another PC or laptop running Linux/OS X/Windows.

Moonlight also has an [Android implementation](https://github.com/moonlight-stream/moonlight-android). Versions for [iOS](https://github.com/moonlight-stream/moonlight-ios) and [Windows and Windows Phone](https://github.com/moonlight-stream/moonlight-windows) are currently in development.

##Features

* Streams Steam and all of your games from your PC to your Linux/OS X/Windows machine
* Keyboard and Mouse support
* Full support for Xbox 360 controllers, PS3 and PS4 controllers, and other HID gamepads
* Full Windows, OS X, and Linux Support* 
* Use mDNS to scan for compatible GeForce Experience (GFE) machines on the network
* Choose from your list of available games instead of just launching Steam

##Features to come
* Hardware accelerated video decoding

##Installation

* Download [GeForce Experience](http://www.geforce.com/geforce-experience) and install on your Windows PC
* Download the appropriate jar from the [GitHub releases page](https://github.com/moonlight-stream/moonlight-pc/releases)

##Requirements

* [GFE compatible](http://shield.nvidia.com/play-pc-games/) computer with GTX 600/700 series GPU (for the PC from which you're streaming)
* High-end wireless router (802.11n dual-band recommended) or wired network

##Usage

* Ensure your machines are on the same network
* Turn on Shield Streaming in the GFE settings
* In Moonlight, enter your PC's IP or Hostname and click "Pair".
* Accept the pairing confirmation on your PC
* For gamepad support, make sure you've mapped your controller in the Options -> Gamepad Settings menu.
* In Moonlight, click "Start Streaming"
* Play games!

To launch Moonlight from a command line:
* `java -jar limelight-[os].jar -host address [options]`
* `-host` [address] the address to connect to. This can be a hostname or ip
  address.
* `-pair` [address] the address to pair to. This can be a hostname or ip address.
* `-fs` launch in full screen
* `-720` use 1280x720 resolution (default)
* `-1080` use 1920x1080 resolution
* `-30fps` use 30 fps stream (default)
* `-60fps` use 60 fps stream


##Contribute

This project is being actively developed at [XDA Developers](http://forum.xda-developers.com/showthread.php?t=2505510)

1. Fork us
2. Write code
3. Send Pull Requests

Check out our [website](http://moonlight-stream.com) for project links and information.

##Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
