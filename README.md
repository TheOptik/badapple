# Bad Apple Pixelflut

This is a pet project I started after the last Mayflower Barcamp.
I found the project interesting and fun, so I wrote a blog post about it.
The finished blogpost you can read [here](https://blog.mayflower.de/18288-performance-tuning-pixelflut.html).

## Features

- Streams pixel data to a Pixelflut server at high frame rates.
- Implements performance optimizations, including delta encoding, multi-threading, and efficient buffer management.
  - See commit history for step-by-step optimizations.
- Benchmarking support using [JMH](https://github.com/openjdk/jmh).
- Uses [JavaCV](https://github.com/bytedeco/javacv) for multimedia frame extraction.

## Requirements

- **Java 21** or later.
- **Maven 3.8+**
- A Pixelflut server (e.g., [pixelpwnr-server](https://github.com/timvisee/pixelpwnr-server)).

## Build the Project

Use Maven to compile and package the application:

```bash
mvn clean package
```

## Running the Application

Clone the repository, download the mp4 from [the internet archive](https://archive.org/details/TouhouBadApple) and place it in the root directory of the project named `Touhou_Bad_Apple.mp4`.

Run the main class via

```bash
mvn exec:java -Dexec.mainClass="de.theoptik.badapple.Launcher"
```

Alternatively, you can open the project in your favorite IDE and run the `Launcher` class directly.

By default the client will connect to a server running on `localhost:1337`.

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

## Contribution

Contributions and feedback are welcome! Feel free to fork the repository, open issues, or submit pull requests.
