# Applied Apotheosis — 开发文档

面向开发者/维护者。玩家使用说明请看 [README.md](README.md)。

> 这是 **1.21.1 / NeoForge** 分支（`1.21.1-neoforge`），1.20.1 / Forge 版本在 `main` 上。
> 本分支用 **ModDevGradle**，需要 **JDK 21**。

## 1.21.1 移植要点（踩过的坑）

- **网络**：Gradle/JVM **不读 Windows 的系统代理**，只认 `HTTP(S)_PROXY` 或 JVM 的
  `-Dhttps.proxyHost`。本机因此在 `gradle.properties`（Gradle 全局那份，不在仓库里）里写了
  `systemProp.https.proxyHost=127.0.0.1` / `systemProp.https.proxyPort=7897`。没有代理会卡在
  `maven.neoforged.net` 握手失败。
  ⚠️ 但代理**只能给 neoforged 用**：把 `libraries.minecraft.net` 也代理掉会让 ForgeGradle 报
  `Failed to validate certificate for host 'https://libraries.minecraft.net/'`（它的证书校验不认
  代理的证书），**1.20.1 那条分支就构建不了了**。所以 `nonProxyHosts` 里要排除
  `libraries.minecraft.net`、`*.minecraft.net`、`*.mojang.com`、`maven.minecraftforge.net`、
  `*.forgecdn.net` 和 gradle 插件门户。
- **幽灵过滤槽"取消不掉"**：AE2 用 `FakeSlot.canSetFilterTo(新值)` 校验要写入的值，**清空时新值是空栈**，
  所以重写这个方法时**必须放行空栈**，否则标记上去就删不掉了（自检里有一条断言钉住这点）。
- **AE2 19 把"网格成员"改成了 NeoForge 方块能力**（`AECapabilities.IN_WORLD_GRID_NODE_HOST`），
  而且只为 AE2 自己的方块实体类型注册。**附属模组必须自己注册**，否则机器的节点永远连不上邻居：
  表现是机器单独待在一个网格里、`powered=false`、什么都不拆（自检的 3 条网络断言会失败）。
  修复见 `registry/ModCapabilities`（顺带注册 `Capabilities.ItemHandler.BLOCK`，
  否则管道/漏斗也推不进来）。
- **AE2 19 的接口签名**：`AENetworkInvBlockEntity` → `AENetworkedInvBlockEntity`；
  `MenuLocator` → `MenuHostLocator`；`TickingRequest` 去掉 `canBeAlerted`；存档方法都加
  `HolderLookup.Provider`；网络同步改用 `RegistryFriendlyByteBuf`；界面注册改事件式
  （`RegisterMenuScreensEvent` + `InitScreens.register(event, ...)`）；方块交互从
  `onActivated()` 变成 `useItemOn()` 且返回 `ItemInteractionResult`。
- **神化 8.x**：包名去掉 `adventure.` 段（`affix.*` / `loot.*` / `socket.gem.*`）；
  `AffixHelper.hasAffixes` → `getAffixes`；`SalvagingMenu.salvageItem` → `getSalvageResults`
  （`findMatch` 返回列表）；`LootRarity.isAtLeast` → 比较 `sortIndex()`；
  `getColor()/getMaterial()` → `color()/material()`；造战利品需要 `GenContext`。
  **宝石不再带稀有度**（改用纯度 purity，配方按纯度分档），所以稀有度门槛现在只管词缀装备，
  宝石一律放行、按纯度产出宝石粉。
- **数据包**：1.21 的目录名是单数 —— `data/<ns>/recipe/`、`data/<ns>/loot_table/`。
- 神化的 `apotheosis:book` 配方需要 **Patchouli**，Curios/Patchouli 都是可选前置，
  开发环境里两个都装上了（`build.gradle` 里有对应 maven），否则日志会刷配方解析错误。
- **GUI 配色跟 AE2 版本走**：AE2 19 换了一整套冷色调（面板 `#CBCCD4` + `#413F54` 外框 +
  `#F2F2F2` 亮线 + 底部 `#878FA5` 2px 投影、直角；槽位凹槽画在**槽位坐标 − 1** 处，
  `#F2F2F2` 边 + 顶部内侧 `#9A9FB4` 暗带 + `#ADB0C4` 填充），而 AE2 15（1.20.1）是
  黑框 + 白/灰立体斜面 + `#C6C6C6` + 圆角。这些值都是从 AE2 19 自己的
  `guis/molecular_assembler.png`、`inscriber.png`、`vibchamber.png` 逐像素量出来的，
  所以 **1.21.1 分支的贴图和 `RarityFilterWidget` 用的是 19 的色板，1.20.1 分支用 15 的**，
  两个分支不要互相同步这两处。
- **AE2 19 的 `Icon.WHITELIST` / `Icon.BLACKLIST` 是坏条目**：贴图已从它的图标集里删掉，
  AE2 自己也不再使用（只有 `Icon` 类还留着这两个常量），画出来是 Minecraft 的**品红缺贴图色** ✗。
  1.21.1 分支改用 AE2 19 自己 `SettingToggleButton` 在用的
  `Icon.STORAGE_FILTER_EXTRACTABLE_ONLY` / `_NONE`（语义同样是"只放行这些 / 这些绝不放行"）。
  客户端自检会把过滤模式依次切一遍并各截一张图，所以这类"图标消失"能被一眼看出来。
- **AE2 19 的界面里，widget 的 `getX()/getY()` 是含 GUI 原点的屏幕坐标**（样式表里的
  `left/top` 是相对坐标）：日志里看到 177,33 而 JSON 写 52,16 是正常的，别当成偏移 ✗。
- 开发环境窗口失焦会自动暂停、把界面关掉 —— 客户端自检里设了
  `options.pauseOnLostFocus = false`，否则截图拍到的是暂停菜单。
- **GUI 背景贴图里的槽位凹槽必须和 AE2 运行时的槽位位置一致**：玩家背包是由 AE2 自己的
  `common/player_inventory.json` include 定位的，而它的偏移**按 AE2 版本变**——
  AE2 15（1.20.1）是 `bottom 82 / 24`，AE2 19（1.21.1）是 `84 / 26`，在这块 223 高的面板上就是
  背包 `123/141/159` + 快捷栏 `181` → `121/139/157` + `179`，**整体上移 2 像素**。
  1.21.1 分支的贴图已按新基准重烘焙；客户端自检里的
  `GUI layout: player inventory at ... (texture bakes ...)` 会在两者不一致时报
  `MISALIGNED`，AE2 再改基准时能立刻发现。

## 构建

需要 **JDK 21**。

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
├── AppliedApotheosis.java                 模组入口（注册内容 + 配置文件）
├── AppliedApotheosisConfig.java           Forge 配置（耗电 / 周期 / 每张加速卡缩短多少 / 稀有度门槛）
├── block/MeSalvagerBlock.java             方块（右键开界面 / 手持战利品右键投料）
├── blockentity/MeSalvagerBlockEntity.java 机器逻辑（ME 网络节点 / 拆解 / 耗电 / 黑白名单 / 稀有度门槛）
├── filter/FilterMode.java                 不过滤 / 白名单 / 黑名单
├── filter/RarityFilter.java               稀有度筛选（普通~神话五档的位掩码 + 颜色回退值）
├── item/SalvageCardItem.java              分解卡（AE2 UpgradeCardItem）
├── menu/MeSalvagerMenu.java               容器菜单（输入 / 过滤 / 升级 / 玩家背包槽 + 进度条同步）
├── client/MeSalvagerScreen.java           AE2 风格界面（过滤按钮在面板内、过滤列表标题右侧；升级卡进 UpgradesPanel 右侧面板；进度条走 AE2 ProgressBar）
├── client/FilterModeButton.java           三态过滤按钮（WHITELIST / BLACKLIST 图标）
├── client/RarityFilterWidget.java         5 个稀有度格子（画成 AE2 槽位：灰底 + 细边框，勾选时按稀有度混色、底边亮色；图标 = 该稀有度拆出的材料；点击 = 客户端动作，掩码同步回来）
├── registry/                              方块 / 物品 / 方块实体 / 菜单 / 创造标签页注册
└── dev/                                   开发者自检（仅系统属性开启时生效）
src/main/resources/
├── META-INF/mods.toml                     模组元数据与依赖声明
├── assets/ae2/screens/me_salvager.json    GUI 样式表（AE2 只从 ae2 命名空间读取样式）
├── assets/applied_apotheosis/             模型、方块状态、贴图（含 GUI 背景）、语言文件
└── data/applied_apotheosis/               战利品表、合成配方
.github/workflows/
├── build.yml                              每次 push/PR：编译 + 跑服务端自检（自检失败即 CI 失败）
└── release.yml                            打 v* tag：构建并把 jar 挂到 Release
updates.json                               游戏内更新检查用的版本清单（改了版本号记得同步）
```

## 想改数值时看哪里

| 想改什么 | 位置 |
|---|---|
| 稀有度门槛 | **配置文件** `machine.minimumRarity`（默认 `apotheosis:common`；改 `mythic` 即只收神话及以上） |
| 接受什么（词缀装备 / 宝石） | `MeSalvagerBlockEntity.isApotheosisLoot`（词缀列表 or `GemItem.getGem(stack).isBound()`）与 `hasRequiredRarity` / `isAccepted` |
| 过滤列表的标记规则 | `MeSalvagerMenu.SalvageableFakeSlot`（AE2 `FakeSlot` 子类，重写 `canSetFilterTo` 限定为神化战利品）——`mayPlace` 恒为 false，所以正常点击永远不会消耗物品；JEI 拖入由 AE2 的 `GhostIngredientHandler` 负责，它同样走 `canSetFilterTo` |
| 方块朝向 | `MeSalvagerBlock.getOrientationStrategy()` 返回 AE2 的 `OrientationStrategies.horizontalFacing()`——属性、放置朝向、扳手旋转全由它接管；`blockstates/me_salvager.json` 里按 `facing=north→y0 / east→y90 / south→y180 / west→y270`（模型正面烘在 `north` 面，与 AE2 的 spatial_anchor 等一致） |
| 方块朝向（AE2 语义） | 非潜行右键扳手 = 旋转（`WrenchHook` + `allowsPlayerRotation`），潜行右键扳手 = 拆下来 |
| 哪些物品会被跳过 | `findSalvageableSlot()`：稀有度合格但神化没有拆解配方的（远古装备）直接跳过，不堵住队列 |
| 稀有度筛选的五档与配色 | `RarityFilter.TIERS`（顺序 = 位掩码位序）/ `FALLBACK_COLORS`；图标直接取 `LootRarity#getMaterial()`（即 神秘废金属~神铸珍珠 那套材料），数据包改了材料会自动跟着变 |
| 界面里那排图标的位置 | `assets/ae2/screens/me_salvager.json` 的 `rarityFilter` 条目：格子 18×18、间距 0（= 18 的列距，与槽位网格同拍），5 格共 90 px，起点 (80,16)，正好压在第 5~9 列槽位上方、右边缘与 9 格槽位行（8..170）齐平；过滤列表是**两行 18 格**（`FILTER_SLOTS = 18`），第二行在 y=58 |
| 黑白名单与稀有度的组合规则 | `MeSalvagerBlockEntity.isAllowedByFilter`：**两道独立筛选**——稀有度行（勾了就只拆那几档，空 = 不限制，与模式无关）+ 物品列表（受模式控制：不过滤 / 白名单空列表=不限制 / 黑名单永不拆），物品要都通过 |
| 输入 / 过滤 / 升级槽数量 | `MeSalvagerBlockEntity.INPUT_SLOTS` / `FILTER_SLOTS` / `UPGRADE_SLOTS`（升级槽由 AE2 的 `UpgradesPanel` 自己定位，实测 176 宽对话框上从 (186,8) 起、竖向排；6 格时向下占到 y≈116，仍在面板内） |
| 耗电与处理速度 | **配置文件** `idlePower` / `powerPerOperation` / `baseTickRate` / `ticksPerSpeedCard`（见 `AppliedApotheosisConfig`）；周期算法在 `getCycleTicks()` / `getTickingRequest()` |
| 卡牌上限 | `ModBlocks.MAX_SALVAGE_CARDS` / `MAX_SPEED_CARDS`（各 3 张）；**注意** `MeSalvagerBlockEntity.UPGRADE_SLOTS` 必须 ≥ 两者之和（现在 6），否则"3 分解卡 + 3 加速卡"的满配摆不出来——README 的吞吐表就是这么写错的 |
| 合成配方 | `src/main/resources/data/applied_apotheosis/recipes/*.json` |
| 物品/界面文本 | `src/main/resources/assets/applied_apotheosis/lang/{en_us,zh_cn}.json` |

## 已知限制

- 过滤列表只按物品种类匹配，同一物品种类的不同词缀无法区分。
- 过滤列表里的条目是**幽灵标记**，不是真物品：机器被破坏时不会掉落它们（否则等于凭空产出）。
- 稀有度筛选只有那 5 档（普通~神话）；远古不在其中，白名单勾了任意档就会把远古一起拦掉，
  黑名单则永远不因稀有度拦远古。
- 宝石只看稀有度，纯度（purity）不影响宝石粉数量；这条跟着神化自己的配方走。
- 待机耗电是建机器时读一次配置，改配置后已放置的机器要重新加载区块才生效；
  耗电 / 周期 / 稀有度门槛都是每次用到时读，改完立刻生效。
- 进度条显示的是"机器的工作节奏"（按世界时间在一个周期内循环），不是某个长任务的进度——
  机器是瞬间完成拆解的，做成假进度反而误导。

## 踩过的坑

- **不要往 `gradle.properties` 里写非 ASCII 字符**。Gradle 用 ISO-8859-1 读这个文件，
  中文会变成乱码写进 `mods.toml`（游戏 Mods 列表里就会显示 `?????¨??????`）。
  中文名放在 `README.md`、语言文件 `lang/zh_cn.json` 和 GUI 文本里即可（那些都是按 UTF-8 读的）。
- `gradle.properties` 的改动会经 `processResources` 的 `expand` 注入 `mods.toml`，所以改完要重新 `build` 才会进 jar。
- **`dev/` 包不能从 jar 里排除**：`AppliedApotheosis` 的构造函数直接调用 `SelfTest.isEnabled()` / `SelfTest::onServerStarted`，
  把 dev 排除掉会让发布包在构造阶段就 `NoClassDefFoundError`。自检只在 `-Dapplied_apotheosis.selftest=true` 时生效，
  留在包里没有副作用。
- **AE2 界面样式 JSON 的 `widgets` 里不能塞 `"$comment": "字符串"`**：widget 的值必须是对象，Gson 解析失败会导致
  整张界面加载不出来；症状是客户端静默不开界面（`minecraft.screen` 为 null、日志里几乎没有报错）。
  服务端/客户端自检里那句 `GUI did NOT open` 就是为这种情况加的。
- 升级槽的坐标由 AE2 的 `UpgradesPanel` 自己算（176 宽对话框上实测从 **(186,8)** 起、竖排），
  JSON 里 `UPGRADE` 的值只是兜底；挪动或增减升级槽后，贴图 `textures/guis/me_salvager.png` 里烘死的槽位底纹要跟着改。
- 界面贴图是烘焙式的：槽位底纹（`#8B8B8B` 底、上/左 `#373737`、下/右 `#FFFFFF` 的凹槽）直接画在 `me_salvager.png` 上，
  AE2 不会另外画槽底。面板尺寸 **176×223**（比 AE2 自己的接口高 18px，多出来的一行给第二个过滤行；再高就放不进 GUI scale 2 的 240 单位窗口了）。
  面板画法与 AE2 一致：外圈 1 px `#000000` 描边，**四角按 45° 斜切 2 px**（切掉的像素保持透明，所以角是圆的），
  里圈 2 px 斜面（上/左 `#FFFFFF`、下/右 `#555555`），内部 `#C6C6C6`；判断方式是算到四条边的对角距离
  （`x+y`、`W-1-x+y`、`x+H-1-y`、`W-1-x+H-1-y`），小于 2 透明、等于 2 描边。
  生成脚本 `GenGui.java` 按固定坐标把面板和槽位一次画出来（过滤行 y=40、输入行 y=76、物品栏 y=123、快捷栏 y=181），
  挪槽位时改脚本里的坐标 + JSON 里的槽位/标签坐标 + `srcRect` 高度即可；
  物品栏那几行是**按对话框底边对齐**的（`common/player_inventory.json` 用 `bottom`，AC=82/24、标题 93），
  所以面板高度一变它们自动跟着走，AE2 自己的界面算出来正好也是它自己 include 里的值。
- **方块贴图走 AE2 机器外壳的调色板**（取样自 `ae2:block/charger_side`、`inscriber`、`energy_acceptor`）：
  壳体 `#B0B0B0`／高光 `#BEBEBE`／阴影 `#8A8A8A`／描边 `#4D4D4D`／凹槽 `#666666`／内嵌 `#393939`、`#2E2E2E`，
  再配本模组的神话橙 `#ED7014`（亮 `#FFB35C`、暗 `#A34A08`）。画法是 16×16 逐像素：外壳一圈描边 + 左上高光/右下阴影、
  四角铆钉、顶面内嵌方槽、正面深色屏幕 + 橙色"箭头入口"图形（`front_active` 只把图形提亮 + 周围点几颗光晕像素）。
- **分解卡沿用 AE2 卡的版式**（对照 `ae2:item/basic_card`、`card_speed`）：浅灰卡身（高光 `#ADA8A8`／本体 `#A09C9C`／
  阴影 `#918E8E`／边缘 `#797979`~`#515050`）、右下角切掉一块的轮廓、右侧 5 px 金手指排线（明暗相间）、
  深色功能图标（`#534A3E`／`#433C30`）、底部三条红色横条（AE2 卡的标志）。我们的卡把金手指与图标托盘换成神话橙。
- **写贴图生成器时注意透明度**：未填充的像素要么补满（方块贴图必须整张不透明），要么保持全透明（物品贴图靠透明背景成形）。
  我曾把未填充像素写成品红占位色，结果游戏里整张卡周围一圈品红——这类问题自检截图一眼能看出来。

## 许可与第三方声明

本模组以 **MIT** 发布，全文见 [LICENSE.txt](LICENSE.txt)。

需要保留的第三方声明：

- Gradle 构建脚手架（`gradlew`、`gradlew.bat`、`gradle/wrapper/**`，以及 `build.gradle` /
  `gradle.properties` / `settings.gradle` 的模板部分）来自 Minecraft Forge MDK，
  仍适用 **LGPL-2.1**（https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt）。
- 本项目**依赖但不打包**下列模组，它们各自的许可照旧适用：
  Applied Energistics 2（LGPL 3.0）、GuideME、Apotheosis / Placebo / ApothicAttributes（MIT）。
- "Minecraft" 是 Mojang Synergies AB 的商标；本项目与 Mojang / Microsoft 无关联、未获其背书。
