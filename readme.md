# Fast RTP

A fast random teleport command that doesn't cause lagspikes.

### Commands
- `/rtp` - Teleports the player to a random location.
- `/rtpback` - Teleports the player back to their last random teleport location.
- `/rtp <player> <args>` - Teleports the specified player to a random location. Requires permissions.
- `/rtp reload` - Reloads the configuration.

### Permissions
- `fast-rtp.command.root` - Allows the player to use the `/rtp` command when `requirePermission` is set to `true`.
- `fast-rtp.command.back` - Allows the player to use the `/rtpback` command.
- `fast-rtp.command.advanced` - Allows you to rtp other players, specify dimension, radius, etc.
- `fast-rtp.command.reload` - Reloads the configuration.
- `fast-rtp.bypass.cooldown` - Allows the player to bypass the RTP cooldown.

### Configuration
The configuration file can be found in `/config/fast-rtp.json`

```json5
{
  // Enables logging for RTP requests. Useful for checking RTP performance or to figure out why it is failing.
  "debugLogging": false,
  // Whether using /rtp requires the player to have the permission "fast-rtp.command.root"
  "requirePermission": false,
  // Whether the /rtpback command should be registered.
  "rtpBackEnabled": true,
  // Whether /rtp should use the current world of the player. 
  // If false, the default world will be used if the dimension is not specified in the command.
  "useCurrentWorld": false,
  // Adds strict targeting / movement checks to prevent players from using the command to escape dangerous situations.
  "useStrictTeleportCheck": false,
  // The default dimension to use for /rtp.
  "defaultDimension": "minecraft:overworld",
  // Redirects RTP's from the "key" dimension to the "value" dimension.
  // In this example, RTP's triggered in the nether will teleport the player to a random location in the end.
  "dimensionRedirects": {
    "minecraft:the_nether": "minecraft:the_end"
  },
  // The maximum radius from the world center to search for a safe location.
  // If set to -1, the radius will go as far as the worldborder.
  "radius": 25000,
  // The minimum radius from the world center to search for a safe location.
  "minRadius": 0,
  // The radius of blocks to check for dangerous surrounding blocks. This doesn't go higher than 4.
  "safetyCheckRadius": 1,
  // Cooldown in seconds applied after using /rtp.
  "cooldown": 30,
  // Specific biome identifiers that can be skipped by the location finder.
  // These can also be used to skip expensive chunk loads, significantly speeding up location finding.
  "blackListedBiomes": [
    "minecraft:small_end_islands",
    "minecraft:the_end",
    "minecraft:the_void"
  ],
  // Biome tags that can be skipped by the location finder.
  "blackListedBiomeTags": [
    "minecraft:is_beach",
    "minecraft:is_ocean",
    "minecraft:is_deep_ocean",
    "minecraft:is_river"
  ],
  // Command messages. Text formatting is done using https://placeholders.pb4.eu/user/text-format/.
  "messages": {
    "rtpStartSearch": "<yellow>Searching for a safe location...",
    "rtpLocFound": "<yellow>Found a safe location in ${seconds} seconds",
    "rtpTeleportPlayer": "<dark_aqua>Teleported to <green>${x} ${y} ${z} <dark_aqua>in <green>${world}",
    "rtpLocNotFound": "<red>[✖] Could not find a safe location!",
    "rtpOnCooldown": "<red>[✖] <gold>Please wait <yellow>${seconds} <gold>seconds before using the RTP again!",
    "preventedRtp": "<red>[✖] Could not start random teleport.\nReason: ${reason}",
    "rtpBackLocNotFound": "<red>[✖] You don't have any recent random teleports.",
    "preventedRtpBack": "<red>[✖] Unable to teleport back to your last RTP location.\nReason: ${reason}"
  }
}
```