# BlueMap Banners

[![Modrinth Game Versions](https://img.shields.io/modrinth/game-versions/rx2aSILw?logo=modrinth&style=for-the-badge)](https://modrinth.com/mod/bluemap-banners)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/rx2aSILw?color=blue&logo=modrinth&style=for-the-badge)](https://modrinth.com/mod/bluemap-banners)

![](src/main/resources/assets/bluemap-banners/icon.png)

A Minecraft Fabric mod that supports displaying Minecraft banners as markers on [BlueMap](https://github.com/BlueMap-Minecraft/BlueMap).  
This is a continuation of [Banners4BM](https://github.com/Nincodedo/Banners4BM) originally by Nincodedo.

## Player usage

Players can use map items on banners to create the marker on BlueMap.  
Players can use map items on banners to remove the marker.  
Players can break the banner by hand to remove the marker.  
External sources can break banners to remove the marker. (TNT, support block broken)

## Config file
``` properties
# Wheter players should be notified and introduced how to add markers to BlueMap
notify_player_on_banner_place=true
# Wheter players should be notified if they add a marker
notify_player_on_marker_add=true
# Wheter players should be notified if they remove a marker
notify_player_on_marker_remove=true
# Wheter all players on the server should be notified a banner has been removed
notify_global_on_marker_remove=false
# Your url on which players can click in notifications, to your BlueMap instance 
bluemap_url=https://your-url-to-bluemap.com/
```


