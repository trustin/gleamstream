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

- Linux x86_32 and x86_64
- Windows x86 and x86_64
- MacOS X x86_64

### How to run

- On your Windows PC:
  - Download [GeForce Experience](http://www.geforce.com/geforce-experience) and install it.
- On your streaming client machine:  
  - Download [Java 8](http://java.oracle.com/) and install it.
  - Download the ZIP or tarball distribution of GleamStream at [GitHub releases](https://github.com/trustin/gleamstream/releases)
  - Unzip or untar the distribution you downloaded and run the launch script:

    ```bash
    cd gleamstream-X.Y.Z/bin
    ./gleamstream
    ```

### Command-line options

- `-host [address]` the address to connect to. This can be a hostname or ip address.
- `-pair [address]` the address to pair to. This can be a hostname or ip address.
- `-fs` launch in full screen
- `-720` use 1280x720 resolution (default)
- `-1080` use 1920x1080 resolution
- `-30fps` use 30 fps stream (default)
- `-60fps` use 60 fps stream
- `-bitrate [value]` the desired bitrate in Mbps
- `-app [name]` the application to launch

For example, I use the following command to start a Steam session:

```bash
./gleamstream -host 192.168.0.16 -1080 -60fps -bitrate 30 -app Steam
```

### How to build

```bash
git clone git@github.com:trustin/gleamstream.git
cd gleamstream
./gradlew build
```

The distribution files will be placed at `build/distributions`

You can also launch the app without building the distributions:

```bash
./gradlew run
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

