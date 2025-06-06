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
notifyPlayerOnBannerPlace=true

# Wheter players should be notified if they add a marker
notifyPlayerOnMarkerAdd=true

# Wheter players should be notified if they remove a marker
notifyPlayerOnMarkerRemove=true

# Wheter all players on the server should be notified a banner has been removed
notifyGlobalOnMarkerRemove=false

# For how far you want to be able to see these banners on BlueMap
markerMaxViewDistance=10000000

# Your url on which players can click in notifications, to your BlueMap instance 
bluemapUrl=https://your-url-to-bluemap.com/
```


