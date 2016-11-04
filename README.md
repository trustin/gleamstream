## GleamStream

GleamStream is an open source implementation of NVIDIA's GameStream, as used by the NVIDIA Shield, but built for
Linux, MacOS X and Microsoft Windows.

It has been forked from [Moonlight](http://moonlight-stream.com/) because Moonlight is moving away from Java-based
[moonlight-pc](https://github.com/moonlight-streaming/moonlight-pc) to Chrome-based
[moonlight-chrome](https://github.com/moonlight-streaming/moonlight-chrome). I'm still interested in making the
Java-based version better-performing and well-maintained, especially because I use it for my Linux machine everyday.

Like Moonlight, GleamStream is licensed under
[GPL (GNU General Public License) 3.0](https://www.gnu.org/licenses/gpl-3.0.en.html).

### Supported platforms

- Linux x86_64
- MacOS X x86_64
- Windows x86 and x86_64

### How to run

- On your Windows PC:
  - Download [GeForce Experience](http://www.geforce.com/geforce-experience) and install it.
- On your streaming client machine:  
  - Download [Java 8](http://java.oracle.com/) and install it.
  - Download the ZIP or tarball distribution of GleamStream at [GitHub releases](https://github.com/trustin/gleamstream/releases)
  - Unzip or untar the distribution you downloaded.
  - Pair with the streaming server:

    ```bash
    cd gleamstream-X.Y.Z/bin
    ./gleamstream -pair <IP address or hostname of the server>
    ```
  - Connect to the streaming server:
  
    ```bash
    cd gleamstream-X.Y.Z/bin
    ./gleamstream -connect <IP address or hostname of the server>
    ```

### GUI usage

Press <kbd>`</kbd> key (backquote) twice within 1 second to toggle the on-screen display
that shows the recent log messages and FPS.

Press <kbd>ESC</kbd> key when on-screen display is on to quit.

### Command-line usage

```
Usage: gleamstream [options]
  Options:
    -appid
       The ID of the application to launch
    -appname
       The name of the application to launch
       Default: Steam
    -bitrate
       The desired bitrate in Mbps
       Default: 30
    -connect
       Connects to the specified IP address or host (e.g. -c 192.168.0.100)
    -fps
       The frame rate of the video stream (must be 60 or 30)
       Default: 60
    -help, -h
       Prints the usage
    -hevc
       Use HEVC video codec
    -localaudio
       Makes the audio stay in the server
    -pair
       Pairs with the specified IP address or host (e.g. -p 192.168.0.100)
    -res
       The resolution of the video stream (must be 1080 or 720)
       Default: 1080
```

For example, I use the following command to start a Steam session:

```bash
./gleamstream -connect 192.168.0.100 -res 1080 -fps 60 -bitrate 30 -appname Steam
```

To pair with the server, enter the following command and type the four-digit
PIN on your server as instructed:

```bash
./gleamstream -pair 192.168.0.100
```

### How to build

```bash
git clone git@github.com:trustin/gleamstream.git
cd gleamstream
./gradlew build
```

The distribution files will be placed at `build/distributions`. You will find the
startup script at `bin` directory of the ZIP file or the tarball.

You can also launch the app without building the distributions:

```bash
./gradlew run -PappArgs='-connect 192.168.0.100'
```

### Authors (GleamStream)

* [Trustin Lee](https://github.com/trustin)

### Authors (Moonlight)

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).

