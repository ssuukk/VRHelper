# VR Helper for Google Gear VR

>You don't have to buy a brewery to drink bear

...my father used to say, and although he didn't mean serving video streams, but something completely different
(if you know what I mean...), I do believe that you shouldn't be required to install Apache, MySQL and PHP (whatever it
is...) to just serve some files.

So in this repository you can find a simple self-contained, stand-alone HTTP server that has just one purpose: serving
video streams for Google Gear VR.

# What can it do?

- quickly and easily browswe your movie collection

- launch movie by taping its name in Android browser

- download *.mvrl file to your device (to be visible in Gear VR, you have to move it to proper directory yourself, though)

- guess video and audio type by hints present in video name

- use images with name similar to video file as thumbnail

# Configuration

VR Helper looks for config file (and all other optional files, by that matter) in current directory.

## config.txt

There are just three parameters:

```
server = storage.lan # (IP or resolvable name)
port = some_port # (80 by default)
dirs = /path1/videos ; /path2/othervideos (server root directories, browsing outside of them is forbidden)
```

Note 1: hash comments are not actually supported

Note 2: this is not a secure server, it should be run only in your own, private network. It doesn't require authentication,
you can only limit it's browsing capabilities to directories specified by `dirs` keyword.

## optional files

`folder.png` - an icon for folder

`video.png` - default video icon

`mainstyles.css` - if present will override internal css

# Running the server

Just build it and start with

`java -jar built_file.jar`