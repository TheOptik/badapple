- Read bad apple and extract frames

  - Read pixel data from frame

        - hard-code color depth and number of channels for now
        - man, never knew strides were a thing (show image without stride?)
        - holy cow its slow... lets measure fps
        - extracting things from within the loop (barely any result at all.... 3.1 ->
          3.2 fps)
        - remove reader (big jump! 3 -> 9 fps!)
        - still kinda slow though, i guess its time to bust out the virtualvm and see
          why
          - oh... the string format thing is kind of slow....
            - ![](./visualvm_1.jpg)
          - using a lookup table lets us get somewhat stable 30 fps!
        - but.... that can't be it, right? how far can we go?
        - ~120fps by only sending diffs!
        - Let's see where the bottleneck is now
          - Looks like the println statement takes quite some time
            - ![](./visualvm_2.jpg)
          - doing byte array manipulation instead of using/creating Strings seems to
            have helped somewhat.
        - it is starting to become hard to measure performance just using the crude
          fps measurments, maybe it's time to improve that
        - implemented jmh

          - Current score:

          | Benchmark       | Mode | Cnt | Score  | Error   | Units |
          | --------------- | ---- | --- | ------ | ------- | ----- |
          | Benchmarks.init | avgt | 25  | 21,780 | ± 0,415 | s/op  |

        - i think its time to switch to nio, since the write operation on the buffer still takes the majority of the frame time
        - ![](./visualvm_3.jpg)

          - score with nio:

          | Benchmark       | Mode | Cnt | Score  | Error   | Units |
          | --------------- | ---- | --- | ------ | ------- | ----- |
          | Benchmarks.init | avgt | 25  | 16,305 | ± 0,112 | s/op  |

        - and write times are looking much better
        - ![](./visualvm_4.jpg)
        - changing to non-blocking nio, doesn't make a big difference. let's try double buffering!
        - ![](./visualvm_5.jpg)

          | Benchmark       | Mode | Cnt | Score  | Error   | Units |
          | --------------- | ---- | --- | ------ | ------- | ----- |
          | Benchmarks.init | avgt | 25  | 15,337 | ± 0,320 | s/op  |

        - it's better, but writing the pixels to the buffer takes the majority of the time, let's do something about that!
        - Hm... writing in parallel did make it a bit faster, but the screen-tearing is awful and it's not as fast as it can be, since i have to synchronize the write to the buffer...

          | Benchmark       | Mode | Cnt | Score  | Error   | Units |
          | --------------- | ---- | --- | ------ | ------- | ----- |
          | Benchmarks.init | avgt | 25  | 14,788 | ± 0,352 | s/op  |

        - well... introducing local buffers _did_ make the screen tearing go away, but the performance is virtually the same
        - oh.....
        - ![](./visualvm_6.jpg)
        - looks like we wait half the frame time to aquire the next frame buffer...
        - replace allocate with allocateDirect (heap vs. off heap?), but that just makes the percentage of acquire time worse...

          | Benchmark       | Mode | Cnt | Score  | Error   | Units |
          | --------------- | ---- | --- | ------ | ------- | ----- |
          | Benchmarks.init | avgt | 25  | 14,698 | ± 0,102 | s/op  |

        - running on a stronger machine (8 cores instead of 4) improves things again, but... throwing hardware at the problem is only half as fun

        | Benchmark       | Mode | Cnt | Score  | Error   | Units |
        | --------------- | ---- | --- | ------ | ------- | ----- |
        | Benchmarks.init | avgt | 25  | 10.256 | ± 0.319 | s/op  |

        - .... or is it the stronger single cores??
          - Why is the pixelpwnr-server using exactly 1 core to the max?
          - ![](./usage_pixelpwn-server.jpg)
          - Okay, i should have guessed as much, but the server i am running against is bound to 1 thread per connection....
            - That explains the long wait times for the buffer to be released...
            - Should i change the server code?
            - Should i update my code to spawn multiple connection?
            - ..... changing the server would be quite fun... but maybe not in the spirit of this endeavour?
            - Decisions decisions...
          - After a _very crude_ test; results of multiple connections are a lot better but... the screen tearing.... eww, not worth it
            Benchmark Mode Cnt Score Error Units
            Benchmarks.init avgt 25 5.578 ± 0.154 s/op
