# 自动汉化更新模组Ⅲ

[![Version](https://img.shields.io/github/v/release/CFPAOrg/I18nUpdateMod3?label=&logo=V&labelColor=E1F5FE&color=5D87BF&style=for-the-badge)](https://github.com/CFPAOrg/I18nUpdateMod3/tags)
[![CurseForge](https://cf.way2muchnoise.eu/short_I18nUpdateMod.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/i18nupdatemod)
[![Modrinth](https://img.shields.io/modrinth/dt/PWERr14M?label=&logo=Modrinth&labelColor=white&color=00AF5C&style=for-the-badge)](https://modrinth.com/mod/i18nupdatemod)
[![License](https://img.shields.io/github/license/CFPAOrg/I18nUpdateMod3?label=&logo=c&style=for-the-badge&color=A8B9CC&labelColor=455A64)](https://github.com/CFPAOrg/I18nUpdateMod3/blob/main/LICENSE)
[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/CFPAOrg/I18nUpdateMod3/beta.yml?style=for-the-badge&label=&logo=Gradle&labelColor=388E3C)](https://github.com/CFPAOrg/I18nUpdateMod3/actions)
[![Star](https://img.shields.io/github/stars/CFPAOrg/I18nUpdateMod3?label=&logo=GitHub&labelColor=black&color=FAFAFA&style=for-the-badge)](https://github.com/CFPAOrg/I18nUpdateMod3/stargazers)

[![Minecraft](https://cf.way2muchnoise.eu/versions/Minecraft_I18nUpdateMod_all.svg?badge_style=for_the_badge)](https://github.com/CFPAOrg/I18nUpdateMod3)

更现代的自动汉化更新模组。

「[简体中文资源包（Minecraft Mod Language Package）](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package)」是由「[CFPAOrg](http://cfpa.team/)」团队维护的「自动汉化资源包」，可以将一些Mod中的文本翻译为中文。  
本Mod用于自动下载、更新、应用「简体中文资源包」。

## 下载

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/i18nupdatemod)
- [Modrinth](https://modrinth.com/mod/i18nupdatemod)

## 支持的版本

- Minecraft：1.6.1~1.20.5 都支持
- Mod加载器：MinecraftForge、NeoForge、Fabric、Quilt 都支持
- Java：8~21 都支持

仅仅需要在mods文件夹中放置本Mod的jar文件即可，Mod本身与各主流Minecraft版本、Mod Loader、Java版本均兼容，Mod本身不需要进行任何版本隔离。

## 支持的「简体中文资源包」

为了尽可能实用，目前本Mod会根据游戏版本自动下载、合并、转换「简体中文资源包」。

- 官方资源：1.10.2、1.12.2、1.16、1.18、1.19、1.20
- 合并转换：会合并加转换最近版本的一些资源包，尽可能做最大化的支持
- 特别说明：1.13开始将语言文件变化为json格式，所以不能将1.12.2的资源包用于1.13以上，反之同理

## 开发环境

请使用Java 8及以上的JDK构建。
```shell
gradle clean build
```