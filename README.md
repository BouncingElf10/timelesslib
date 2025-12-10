<div align="center">

# TimelessLib
A lightweight time & animation utility library for Fabric (and maybe forge to come)

</div>

## Features
- Animation System
  - Double channels
  - Vec3 channels
  - Easings
  - Interpolation system
- Cooldowns
- Countdowns
- Scheduler
- Time Utils
  - Time Anchors
  - Time Conversions
  - Time Formatter
  - Time Parser
- Real time / Game time system
  - Respects pausing in singleplayer
- Duration class that includes ticks

## Installation (for developers)
Add these to your project (no dependencies needed!)
### repositories (build.gradle)
```gradle
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}
```

### dependencies

```gradle
dependencies {
    modImplementation "maven.modrinth:timelesslib:${project.timelesslib_version}"
}
```

### gradle.properties

```
timelesslib_version=VERSION
```
You can check the version of timelesslib on the versions tab or in the dropdown below:
<details>
<summary><strong>Click to expand version list</strong></summary>

| TimelessLib Version              | Minecraft Versions           | Loader |
| -------------------------------- | ---------------------------- | ------ |
| **1.0.16-fabric-1.20.5** | 1.21–1.21.10 / 1.20.5–1.20.6 | Fabric |
| **1.0.16-fabric-1.19.3-1.20.4**  | 1.20–1.20.4 / 1.19.3–1.19.4  | Fabric |
| **1.0.16-fabric-1.19-1.19.2**    | 1.19–1.19.2                  | Fabric |
| **1.0.16-fabric-1.17-1.18.2**    | 1.18.x / 1.17.x              | Fabric |
| **1.0.16-fabric-1.14-1.16.5**    | 1.16.x / 1.15.x / 1.14.x     | Fabric |

</details>

## Documentation

While there is no official wiki (yet) the libary does include Javadoc and an example mod found [here](https://github.com/BouncingElf10/timelesslib-example-mod-1.21.1).
