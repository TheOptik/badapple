# Bad Apple Pixelflut

- Read bad apple and extract frames
- Read pixel data from frame
  - hard-code color depth and number of channels for now
  - man, never knew strides were a thing (show image without stride?)
  - holy cow its slow... lets measure fps
  - extracting things from within the loop (barely any result at all.... 3.1 -> 3.2 fps)
  - remove reader (big jump! 3 -> 9 fps!)
  - still kinda slow though, i guess its time to bust out the virtualvm and see why
    - oh... the string format thing is kind of slow....
    - using a lookup table lets us get somewhat stable 30 fps!
  - but.... that can't be it, right? how far can we go?