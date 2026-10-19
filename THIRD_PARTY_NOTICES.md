# Third-party notices and credits

Graydient's Touhou Little Maid Tweaks is an independent addon for
[Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid), by
tartaric_acid and contributors. The original mod must be installed separately.
No official endorsement by TLM, Mojang, Microsoft or Team Shanghai Alice is claimed.

## Addon license

The addon is GPL-3.0-or-later. [COPYRIGHT](COPYRIGHT) supplies the project grant;
[LICENSE](LICENSE) contains the complete GNU GPL v3 text. Third-party portions
retain their applicable original notices and permissions below.

## Touhou Little Maid code and assets

TLM's [Modrinth listing](https://modrinth.com/mod/touhou-little-maid/version/tXG1TkGx)
shows CC BY-NC-SA 4.0. Its repository's
[license statement](https://github.com/TartaricAcid/TouhouLittleMaid#license)
distinguishes **MIT code** from **CC BY-NC-SA 4.0 assets**. Specific components,
including the engines below, have their own notices. The listing's single license
label does not replace those file-level grants.

The addon adapts upstream board interaction flow for player sessions, turns and
results. Relevant code is `board/ChessCoPlay.java` and the Chess/Xiangqi/Gomoku
mixins under `src/main/java/io/github/graydients/tlmtweaks/`.
The following upstream MIT notice is preserved for adapted code:

```text
MIT License

Copyright (c) 2019-2024 tartaric_acid, for the code part

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Chess and Xiangqi engines

The separately installed TLM supplies these engines:

- **Mobile Chess**, designed by Morning Yellow. `Position.java` credits
  Copyright (C) 2008 mobilechess.sourceforge.net; `Search.java` credits
  Copyright (C) 2008-2013 www.chess-wizard.com. The source grants GPL-2.0-or-later.
  [Original repository](https://github.com/xqbase/chess).
- **XiangQi Wizard Light**, designed by Morning Yellow. `Position.java` credits
  Copyright (C) 2004-2008 www.elephantbase.net; `Search.java` credits
  Copyright (C) 2004-2013 www.xqbase.com. The source grants GPL-2.0-or-later.
  [Original repository](https://github.com/xqbase/xqwlight).

The addon uses their position state and move methods. No engine implementation
classes are copied into the addon JAR. TLM includes their GPL texts at
`licenses/chess` and `licenses/xqwlight`. GPL-2.0-or-later permits use under GPL v3;
the addon uses GPL-3.0-or-later. These credits do not replace the engines' grants.

## Gomoku

TLM's Gomoku `Point` and AI implementation credit **anlingyi / XEChat-Idea**
under Apache-2.0. The implementation is supplied by TLM, not embedded in this
addon. [Original license](https://github.com/anlingyi/xechat-idea/blob/ab0a70f3e995c3b32092dffda8b131ed31ec9799/LICENSE).
Apache-2.0 code can be combined under GPL v3 subject to its applicable conditions.

## Exact upstream source

The [official TLM 1.5.3 NeoForge release](https://modrinth.com/mod/touhou-little-maid/version/tXG1TkGx)
provides the [companion source archive](https://cdn.modrinth.com/data/R0bDWFAW/versions/tXG1TkGx/touhou-little-maid-1.5.3-neoforge%2Bmc1.21.1-sources.jar).
Its Java/resource files match
[commit 5a6e81debd90bb8b1fd7182a1b970de5eccfd217](https://github.com/TartaricAcid/TouhouLittleMaid/tree/5a6e81debd90bb8b1fd7182a1b970de5eccfd217),
which also provides the upstream build scripts. Each addon release supplies a
matching versioned source archive beside its binary.

## Carry On

Optional hooks call the separately installed Carry On mod. No Carry On classes
or assets are copied into the addon.

## Resources and other dependencies

No TLM artwork, models, texture images, voices, music or resource-pack ZIPs are
included in the addon source or JAR. Station JSON uses addon geometry and vanilla
texture identifiers; the game supplies the actual texture images. Minecraft,
NeoForge, Cloth Config, Sponge Mixin and their supporting libraries remain
external dependencies with their own licenses. No dependency or game JAR is embedded.

Pack Sync provides transport, not a license to redistribute a pack. Share packs
only when their authors' terms allow the intended use. Consent and public
catalogue availability do not replace those terms.

Minecraft is created by Mojang Studios / Microsoft. Touhou Project is created by
ZUN / Team Shanghai Alice. Their names and independent works are not relicensed
by this addon. Minecraft use remains subject to its
[EULA](https://www.minecraft.net/en-us/eula).
