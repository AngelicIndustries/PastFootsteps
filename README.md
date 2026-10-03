# Past Footsteps

A Forge 1.7.10 port of Presence Footsteps, with material steps, foliage rustling, equipment accents and footprints for players and supported mobs.

[Configure it](docs/Configuration.md) from the mod list. When Dynamic Surroundings is installed, PastFootsteps takes over its footsteps while enabled.

Configure [entity and gait profiles](docs/Resource-packs.md#entity-and-gait-profiles), [equipment](docs/Equipment.md) and [footprints](docs/Footprints.md). Unmatched creatures keep their native sounds. Works on any server, with [optional server support](docs/Server-support.md).

Walking features formerly in AET now belong here; see the [migration guide](docs/AET-migration.md).

[![hub](images/badges/github.png)](https://github.com/JackOfNoneTrades/PastFootsteps/releases)
![forge](images/badges/forge.png)
[![cord](images/badges/cord.png)](https://discord.gg/xAWCqGrguG)

<!-- [![curse](images/badges/curse.png)](https://www.curseforge.com/minecraft/mc-mods/pastfootsteps) -->
<!-- [![modrinth](images/badges/modrinth.png)](https://modrinth.com/mod/pastfootsteps) -->
<!-- [![67](images/badges/67.png)](https://67.fentanylsolutions.org/mod/pastfootsteps) -->
<!-- [![maven](images/badges/maven.png)](https://maven.fentanylsolutions.org/#/releases/org/fentanylsolutions/pastfootsteps/PastFootsteps) -->
<!-- [![mcmod.cn](images/badges/mcmodcn.png)](https://www.mcmod.cn/class/PROJECT_ID.html) -->

## Dependencies

* [UniMixins](https://modrinth.com/mod/unimixins) [![curse](images/icons/curse.png)](https://www.curseforge.com/minecraft/mc-mods/unimixins) [![modrinth](images/icons/modrinth.png)](https://modrinth.com/mod/unimixins/versions) [![git](images/icons/git.png)](https://github.com/LegacyModdingMC/UniMixins/releases)

## Building

```sh
VERSION=myversion ./gradlew build
```

To test alongside a local An Extra Touch development build, explicitly supply its jar:

```sh
VERSION=0.0.1 ./gradlew runClient -PanExtraTouchJar=../AnExtraTouch/build/libs/anextratouch-0.0.13-dev.jar
```

Use a development (`-dev.jar`) artifact. This is an optional local test dependency; it is not bundled or
required by the published mod. See [release preparation](RELEASE_CHECKLIST.md) for the current cleanup status.

## Credits

* Hurricaaane, Sollace, and the [Presence Footsteps contributors](https://github.com/Sollace/Presence-Footsteps)
* [GT:NH Example Mod](https://github.com/GTNewHorizons/ExampleMod1.7.10)

## License

The code is [MIT-licensed](LICENSE), derived from [MIT-licensed code](https://github.com/Sollace/Presence-Footsteps/blob/d0d860591287656244e7236a9131004d1d0ddee6/LICENSE).

## Buy me some creatine

* [ko-fi.com](https://ko-fi.com/jackisasubtlejoke)
* Monero: `893tQ56jWt7czBsqAGPq8J5BDnYVCg2tvKpvwTcMY1LS79iDabopdxoUzNLEZtRTH4ewAcKLJ4DM4V41fvrJGHgeKArxwmJ`

<br>

![license](images/license_small.png)
