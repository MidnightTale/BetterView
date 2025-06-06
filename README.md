# BetterView

## Description

BetterView is a paper plugin and fabric mod for Minecraft servers. It extends the normal view distance
to extreme lengths using asynchronous player processing, chunk caching and direct chunk reading.

## Features

- Asynchronous fast player ticking on networking threads
- Reads chunks directly from disk without loading them
- Optimized chunk state tracking and chunk iteration
- Configurable chunk data caching for fast access
- Configurable limitations for chunk sending and chunk generation
- Per-dimension configuration options
- Support for integrated or dedicated fabric servers

<details>
<summary><strong>Example: Loading speed for a view distance of 127 chunks</strong></summary>

The screenshot below was taken at `0 2250 0` in the end dimension with a view distance of 127 chunks
(with fog disabled). On an AMD Ryzen 7 5800X, all visible chunks were transmitted to the client in about
12 seconds after having joined the server with a cold cache and in about 9 seconds with a warm cache.

<img src="https://i.imgur.com/HWspPzj.png" alt="A top-down view of the main end island and parts of the outer end island" width="600" loading="lazy">

</details>

### Supported Software

| Minecraft Version | Paper | Fabric |
|-------------------|-------|--------|
| 1.21.5            | ✅     | ✅      |
| 1.21.4            | ✅     | ✅      |
| 1.21.3            | ✅     | ✅      |
| 1.21.1            | ✅     | ✅      |

On fabric, this mod depends on [Moonrise](https://github.com/Tuinity/Moonrise) to work.

## Usage

On Paper servers, place the jar file in your `plugins` directory and restart your server. On Fabric servers/clients,
place the jar file in your `mods` directory and restart your server or client. By default, BetterView will configure a
view distance of 32 chunks for each dimension.

### Configuration

On the first start, this plugin will automatically create a configuration file. If using Paper it will be created at
`plugins/BetterView/config.yml`, if using Fabric it will be created at `config/betterview.yml`. In there, you are able
to configure the following options:

- `config-version`: Don't touch this
- `integrated-server-render-distance`: Only relevant for singleplayer worlds, this allows replacing the render distance
  of the integrated server as BetterView would otherwise have no effect (default: `-1`, disabled)
- `global`:
    - `enabled`: Whether to enable or disable the entire plugin/mod (default: `true`)
    - `chunk-generation-limit`: How many new chunks can be generated globally in one tick (default: `3`)
    - `chunk-send-limit`: The maximum amount of chunks sent to a player in a tick (default: `3`)
- `dimensions`:
    - `<dimension>` (e.g. `minecraft:overworld`):
        - `enabled`: Whether to enable or disable the plugin/mod for this dimension (default: `true`)
        - `chunk-generation-limit`: How many new chunks can be generated for this level in one tick (default: `2`)
        - `chunk-queue-size`: How many chunks can be queued per player at once (default: `16`)
        - `view-distance`: The maximum extended view distance for this dimension (default: `32`)
        - `cache-duration`: The cache duration for how long extended chunks should be kept in memory (default: `PT5M`,
          5 minutes)

Feel free to play around with the chunk generations and chunk sending limits.

Make sure to adjust the cache duration based on what you use your server for;
for e.g. static lobby servers, you can use a longer cache duration than for dynamic SMP servers.
If the cache duration is too high, chunks will display outdated content after e.g. a rejoin.

## Building

1. Clone the project (`git clone https://github.com/MinceraftMC/BetterView.git`)
2. Go to the cloned directory (`cd BetterView`)
3. Build the jar files (`./gradlew build` on Linux/MacOS, just `gradlew build` on Windows)

The jar files can be found in the `build` → `libs` directory.

### Contributing

If you want to contribute to BetterView, feel free to fork the repository and create a pull request. Please make sure to
follow the code style and conventions used in the project. If you have any questions or need help, feel free to ask in
our [Discord](https://discord.gg/zC8xjtSPKC).

You can test your changes by running `./gradlew :paper:runServer` or
`./gradlew :fabric:prodServer`/`./gradlew :fabric:prodClient` respectively. This will start a local server with the
compiled plugin or mod automatically installed, ready for debugging in your IDE.
