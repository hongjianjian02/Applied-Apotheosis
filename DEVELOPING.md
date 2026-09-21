# Applied Apotheosis — 开发文档

面向开发者/维护者。玩家使用说明请看 [README.md](README.md)。

## 构建

需要 **JDK 17**。

```bash
./gradlew build          # 产物在 build/libs/applied_apotheosis-<version>.jar
./gradlew runClient      # 启动开发环境客户端（依赖已由 Gradle 接好，无需手动装 mod）
./gradlew runServer      # 启动开发环境服务端（首次需在 run/eula.txt 写 eula=true）
```

## 开发者自检

两个自检都只在对应 Gradle 参数下开启（内部靠系统属性
`applied_apotheosis.selftest` / `applied_apotheosis.clientselftest` 控制），正常游戏不会触发。

### 服务端自检：`gradlew runServer -Pselftest`

自动在服务端世界搭一个微型 ME 网络（ME 分解器 + 创造能源元件 + ME 箱子 + 1k 元件），
用 Apotheosis 的 API 现场随机生成各稀有度装备与宝石，验证注册、卡牌并行度、稀有度门槛、
宝石接收、黑白名单、拆解与入网（材料与宝石粉都要进网络），然后自动关服：

```
[selftest] parallelism with 1 salvage card  = 1 item(s) per tick (expect 1)
[selftest] parallelism with 3 salvage cards = 3 item(s) per tick (expect 3)
[selftest] lowest-rarity gear (common) into input -> leftover = 0 (0 = correctly accepted)
[selftest] common gear into filter list  -> valid = true    (true = correctly accepted)
[selftest] epic gear into input          -> leftover = 0    (0 = correctly accepted)
[selftest] mythic gem = 1 gem | rarity = apotheosis:mythic | hasAffixes = false | isApotheosisLoot = true | accepted = true
[selftest] mythic gem into input       -> leftover = 0 (0 = correctly accepted)
[selftest] mythic gem into filter list -> valid = true (true = correctly accepted)
[selftest] Apotheosis salvaging produced from the gem: [3 gem_dust] (expect gem dust)
[selftest] mythic rarity but empty affix list -> hasAffixes = false | accepted = false
[selftest] BLACKLIST + listed sword -> sword leftover = 1 (1 = correctly blocked)
[selftest] WHITELIST + unlisted chestplate -> leftover = 1 (1 = correctly blocked)
[selftest] WHITELIST + only mythic ticked -> mythic gear leftover = 0 (0 = correctly accepted)
[selftest] WHITELIST + only mythic ticked -> epic gear leftover = 1 (1 = correctly blocked)
[selftest] WHITELIST + only mythic ticked -> common gear leftover = 1 (1 = correctly blocked)
[selftest] BLACKLIST + mythic ticked -> mythic gear leftover = 1 (1 = correctly blocked)
[selftest] BLACKLIST + mythic ticked -> common gear leftover = 0 (0 = correctly accepted)
[selftest] WHITELIST + nothing ticked + empty list -> common gear leftover = 0 (0 = no restriction)
[selftest] input buffer now holds 2 item(s) (expect 2: one affix item + one gem)
[selftest] everything consumed at tick 21 (machine salvaged the queued loot)
[selftest] ME network storage now holds = [2 x apotheosis:mythic_material, 3 x apotheosis:gem_dust]
[selftest] gem dust from the gem reached the network = true (expect true)
```

### 客户端自检：`gradlew runClient -Pclientselftest`

以 quick-play 打开存档 `run/saves/selftest`，自动摆放机器、填入过滤条目并**打开 GUI**，
然后在 `run/screenshots/` 留下一张界面截图（用于检查 AE2 样式表与槽位布局）：

```
[clientselftest] menu type = applied_apotheosis:me_salvager | block entity = ...MeSalvagerBlockEntity@...
[clientselftest] MenuOpener.open returned true
[clientselftest] open screen is com.jianjian.appliedapotheosis.client.MeSalvagerScreen@...
```

> `-Pclientselftest` 需要 `run/saves/selftest` 存在；删掉后随便建一个同名世界即可。

## IDE（IntelliJ IDEA）

工程用 ForgeGradle 6 + JDK 17。装了 **Minecraft Development** 插件时，IDE 会把 ForgeGradle
解压出的 Minecraft 反编译源码（`build/tmp/.cache/expanded/...`）挂进模块；如果按 IDE 自己的
**Build**（Ctrl+F9）或对这类文件按"编译当前文件"，就会满屏"找不到符号"——那不是模组的问题。

正确做法（二选一）：

1. `Settings → Build Tools → Gradle`：`Build and run using` 与 `Run tests using` 都设为 **Gradle**，
   `Gradle JVM` 设为 **17**；之后 Ctrl+F9 会委托给 Gradle。
2. 直接用 Gradle 面板 `Tasks → forgegradle runs → runClient`，或终端执行 `gradlew runClient`。

## 受限环境：Gradle 家目录不可写

某些环境无法写入默认的 `~/.gradle`（表现为 Gradle 报 native 库加载失败）。
把 Gradle 家目录指到工程内即可：

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-home"   # 已被 .gitignore 忽略
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat build
```

## 工程结构

```
src/main/java/com/jianjian/appliedapotheosis/
├── AppliedApotheosis.java                 模组入口
├── block/MeSalvagerBlock.java             方块（右键开界面 / 投放装备）
├── blockentity/MeSalvagerBlockEntity.java 机器逻辑（ME 网络节点 / 拆解 / 耗电 / 黑白名单 / 稀有度门槛）
├── filter/FilterMode.java                 不过滤 / 白名单 / 黑名单
├── filter/RarityFilter.java               稀有度筛选（普通~神话五档的位掩码 + 颜色回退值）
├── item/SalvageCardItem.java              分解卡（AE2 UpgradeCardItem）
├── menu/MeSalvagerMenu.java               容器菜单（输入 / 过滤 / 升级 / 玩家背包槽）
├── client/MeSalvagerScreen.java           AE2 风格界面（过滤按钮进 addToLeftToolbar 左侧工具栏，升级卡进 UpgradesPanel 右侧面板）
├── client/FilterModeButton.java           三态过滤按钮（WHITELIST / BLACKLIST 图标，走 AE2 工具栏）
├── client/RarityFilterWidget.java         5 个稀有度格子（画成 AE2 槽位：灰底 + 细边框，勾选时按稀有度混色、底边亮色；图标 = 该稀有度拆出的材料；点击 = 客户端动作，掩码同步回来）
├── registry/                              方块 / 物品 / 方块实体 / 菜单 / 创造标签页注册
└── dev/                                   开发者自检（仅系统属性开启时生效）
src/main/resources/
├── META-INF/mods.toml                     模组元数据与依赖声明
├── assets/ae2/screens/me_salvager.json    GUI 样式表（AE2 只从 ae2 命名空间读取样式）
├── assets/applied_apotheosis/             模型、方块状态、贴图（含 GUI 背景）、语言文件
└── data/applied_apotheosis/               战利品表、合成配方
```

## 想改数值时看哪里

| 想改什么 | 位置 |
|---|---|
| 稀有度门槛（现在 = 全部收） | `MeSalvagerBlockEntity.MINIMUM_RARITY`（`apotheosis:common`；改成 `mythic` 即只收神话及以上） |
| 接受什么（词缀装备 / 宝石） | `MeSalvagerBlockEntity.isApotheosisLoot`（词缀列表 or `GemItem.getGem(stack).isBound()`）与 `hasRequiredRarity` / `isAccepted` |
| 过滤列表的标记规则 | `MeSalvagerMenu.SalvageableFakeSlot`（AE2 `FakeSlot` 子类，重写 `canSetFilterTo` 限定为神化战利品）——`mayPlace` 恒为 false，所以正常点击永远不会消耗物品；JEI 拖入由 AE2 的 `GhostIngredientHandler` 负责，它同样走 `canSetFilterTo` |
| 哪些物品会被跳过 | `findSalvageableSlot()`：稀有度合格但神化没有拆解配方的（远古装备）直接跳过，不堵住队列 |
| 稀有度筛选的五档与配色 | `RarityFilter.TIERS`（顺序 = 位掩码位序）/ `FALLBACK_COLORS`；图标直接取 `LootRarity#getMaterial()`（即 神秘废金属~神铸珍珠 那套材料），数据包改了材料会自动跟着变 |
| 界面里那排图标的位置 | `assets/ae2/screens/me_salvager.json` 的 `rarityFilter` 条目：格子 18×18、间距 0（= 18 的列距，与槽位网格同拍），5 格共 90 px，起点 (80,16)，正好压在第 5~9 列槽位上方、右边缘与 9 格槽位行（8..170）齐平 |
| 黑白名单与稀有度的组合规则 | `MeSalvagerBlockEntity.isAllowedByFilter`（白名单要求两部分都通过，黑名单命中任一即拦；任一部分为空 = 该部分不限制） |
| 输入 / 过滤 / 升级槽数量 | `MeSalvagerBlockEntity.INPUT_SLOTS` / `FILTER_SLOTS` / `UPGRADE_SLOTS`（**注意**：改槽数还要同步改 `assets/ae2/screens/me_salvager.json` 里的槽位坐标与贴图 `textures/guis/me_salvager.png`；升级槽的位置由 AE2 的 `UpgradesPanel` 自己算，实测在 176 宽对话框上是 (186,8) 起、竖向排） |
| 耗电与处理速度 | `POWER_PER_OPERATION`、`IDLE_POWER`、`BASE_TICK_RATE`，以及 `getOperationsPerCycle()` / `getTickingRequest()` |
| 卡牌上限 | `ModBlocks.MAX_SALVAGE_CARDS` / `MAX_SPEED_CARDS` |
| 合成配方 | `src/main/resources/data/applied_apotheosis/recipes/*.json` |
| 物品/界面文本 | `src/main/resources/assets/applied_apotheosis/lang/{en_us,zh_cn}.json` |

## 已知限制

- 方块没有朝向属性，正面固定为北面。
- 稀有度门槛是写死的常量，不能在游戏里调。
- 过滤列表只按物品种类匹配，同一物品种类的不同词缀无法区分。
- 过滤列表里的条目是**幽灵标记**，不是真物品：机器被破坏时不会掉落它们（否则等于凭空产出）。
- 稀有度筛选只有那 5 档（普通~神话）；远古不在其中，白名单勾了任意档就会把远古一起拦掉，
  黑名单则永远不因稀有度拦远古。
- 宝石只看稀有度，纯度（purity）不影响宝石粉数量；这条跟着神化自己的配方走。

## 踩过的坑

- **不要往 `gradle.properties` 里写非 ASCII 字符**。Gradle 用 ISO-8859-1 读这个文件，
  中文会变成乱码写进 `mods.toml`（游戏 Mods 列表里就会显示 `?????¨??????`）。
  中文名放在 `README.md`、语言文件 `lang/zh_cn.json` 和 GUI 文本里即可（那些都是按 UTF-8 读的）。
- `gradle.properties` 的改动会经 `processResources` 的 `expand` 注入 `mods.toml`，所以改完要重新 `build` 才会进 jar。
- **AE2 界面样式 JSON 的 `widgets` 里不能塞 `"$comment": "字符串"`**：widget 的值必须是对象，Gson 解析失败会导致
  整张界面加载不出来；症状是客户端静默不开界面（`minecraft.screen` 为 null、日志里几乎没有报错）。
  服务端/客户端自检里那句 `GUI did NOT open` 就是为这种情况加的。
- 升级槽的坐标由 AE2 的 `UpgradesPanel` 自己算（176 宽对话框上实测从 **(186,8)** 起、竖排），
  JSON 里 `UPGRADE` 的值只是兜底；挪动或增减升级槽后，贴图 `textures/guis/me_salvager.png` 里烘死的槽位底纹要跟着改。
- 界面贴图是烘焙式的：槽位底纹（`#8B8B8B` + `#373737` 边）直接画在 `me_salvager.png` 上，AE2 不会另外画槽底。
  面板现在是 **176×236**，结构极简：第 0 行/列是 `#FFFFFF` 高光、最后一行/列是 `#555555` 阴影、内部 `#C6C6C6`。
  要挪槽位就按"整块搬移"改贴图（把旧图对应行区间 `DrawImage` 到新 y），再同步 JSON 里的坐标与 `srcRect` 高度；
  物品栏那几行是**按对话框底边对齐**的（`common/player_inventory.json` 用 `bottom`），所以面板加高多少它们就自动下移多少。

## 许可与第三方声明

本模组以 **MIT** 发布，全文见 [LICENSE.txt](LICENSE.txt)。

需要保留的第三方声明：

- Gradle 构建脚手架（`gradlew`、`gradlew.bat`、`gradle/wrapper/**`，以及 `build.gradle` /
  `gradle.properties` / `settings.gradle` 的模板部分）来自 Minecraft Forge MDK，
  仍适用 **LGPL-2.1**（https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt）。
- 本项目**依赖但不打包**下列模组，它们各自的许可照旧适用：
  Applied Energistics 2（LGPL 3.0）、GuideME、Apotheosis / Placebo / ApothicAttributes（MIT）。
- "Minecraft" 是 Mojang Synergies AB 的商标；本项目与 Mojang / Microsoft 无关联、未获其背书。
