# Applied Apotheosis（应用神化）

一个 Minecraft **Forge 1.20.1** 模组，作为 **Applied Energistics 2 (AE2)** 的附属，
把 **Apotheosis（神化）** 的词缀装备自动拆解成材料，并直接把产物送回 **ME 网络**。

- mod id：`applied_apotheosis`
- 显示名：Applied Apotheosis ／ 应用神化
- 作者：jianjian_

## 内容

### ME 分解器（`applied_apotheosis:me_salvager`）

一台 AE2 风格的机器（方块 + 方块实体），接入 ME 网络后工作：

- **稀有度门槛**：接受 **普通（Common）及以上**的 Apotheosis 装备，也就是所有带词缀的神化装备。
  决定门槛的常量是 `MINIMUM_RARITY`，想收窄（例如只拆神话）改这一个值即可。
- **输入缓冲 9 格**：放入待拆解的装备（`affix_data.affixes` 非空且稀有度不低于门槛）。
  可以通过 AE2 输入总线 / 接口 / 漏斗 / 任意管道推入，也可以**手持装备右键机器**直接放入。
- **升级槽 3 格**：安装 3 张 `分解卡` + 3 张 AE2 加速卡。
- **拆解**：完全复用 Apotheosis 自己的拆解配方（`apotheosis:salvaging` 数据包配方），
  调用 `SalvagingMenu.salvageItem(Level, ItemStack)`，产物、数量、耐久折扣与回收台一致。
- **输出**：产物先进入内部 9 格缓冲，然后插入 ME 网络；网络暂时收不下时会停下来等待，
  **不会吞物品**（机器被破坏时输入/输出缓冲、过滤列表与升级卡都会掉落）。
- **耗电**：待机 1 AE/t，每拆解 1 件消耗 20 AE。

> 判定条件是「**带词缀** 且 **稀有度 ≥ `MINIMUM_RARITY`**」两条同时成立。当前门槛是
> `apotheosis:common`（最低档），所以实际效果是"所有神化词缀装备都能拆"：
> - 普通 / 罕见 / 稀有 / 史诗 / 神话 / 远古 词缀装备 ✅
> - 普通物品（钻石、剑…）❌
> - **宝石 ❌** —— 宝石只有稀有度标记、没有词缀列表（`GemRegistry` 只写 `affix_data.rarity`），
>   所以不在机器接收范围内；宝石请继续用 Apotheosis 的**回收台**拆成宝石粉。
> - 词缀被清空、只剩稀有度标记的装备 ❌
>
> 比较用的是 `LootRarity#isAtLeast`，把 `MINIMUM_RARITY` 改成 `apotheosis:mythic` 就变回
> "只收神话及以上"。

吞吐由两种卡共同决定：

```
每 tick 处理件数 = 分解卡数（1~3，未装卡机器不工作）
工作周期        = max(1, 10 - 3 × 加速卡数) tick
```

| 分解卡 | 加速卡 | 周期 | 吞吐 |
|---|---|---|---|
| 1 | 0 | 10 tick | 2 件/秒 |
| 3 | 0 | 10 tick | 6 件/秒 |
| 1 | 3 | 1 tick | 20 件/秒 |
| 3 | 3 | 1 tick | 60 件/秒 |

### 界面（GUI）

**空手右键机器**（或手持非词缀装备右键）打开 AE2 风格界面：

| 区域 | 说明 |
|---|---|
| 过滤列表（9 格） | 黑/白名单条目，只按**物品种类**匹配（忽略耐久与 NBT 词缀）；**只接受神化词缀装备** |
| 待分解（9 格） | 输入缓冲，**只接受神化词缀装备**（空槽悬停有提示） |
| 升级（3 格） | 分解卡 / 加速卡，可以直接在界面里插拔 |
| 物品栏 | 玩家背包与快捷栏，支持 shift 点击快速移动 |

右上角的按钮在 **不过滤 → 白名单 → 黑名单** 三种模式间循环切换：

- **不过滤**：所有神化词缀装备都会被拆解
- **白名单**：只拆解过滤列表里列出的物品种类（列表为空则什么都不拆）
- **黑名单**：过滤列表里列出的物品种类永不拆解

模式与列表都会随机器一起保存，切换模式后已在缓冲里的物品会立即按新规则重新判定。

### 分解卡（`applied_apotheosis:salvage_card`）

AE2 升级卡（继承 `UpgradeCardItem`）。**至少安装 1 张机器才会工作**（未装卡时机器完全待机），
最多 3 张，每张让机器每 tick 多处理 1 件装备。

安装方式：**潜行 + 右键机器**（AE2 升级卡通用行为），或在 GUI 的升级槽里放入。

## 合成

ME 分解器（工作台有序合成）：

```
聚能石英玻璃  运算处理器  聚能石英玻璃      聚能石英玻璃 ×4   ae2:quartz_vibrant_glass
工程处理器    回收台      工程处理器        运算处理器   ×1   ae2:calculation_processor
聚能石英玻璃  逻辑处理器  聚能石英玻璃      工程处理器   ×2   ae2:engineering_processor
                                          逻辑处理器   ×1   ae2:logic_processor
                                          回收台       ×1   apotheosis:salvaging_table
```

分解卡（工作台有序合成，左右不对称）：

```
              宝石粉                        宝石粉       ×3   apotheosis:gem_dust
宝石粉        高级卡        宝石粉          高级卡       ×1   ae2:advanced_card
              逻辑处理器                    逻辑处理器   ×1   ae2:logic_processor
```

两个配方都设了 `show_notification`，做出来一次后配方书会显示，可以直接点着自动摆料。
配方文件在 `src/main/resources/data/applied_apotheosis/recipes/`，想改材料直接编辑 JSON。

## 依赖

| 依赖 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.20.1 | |
| Forge | 47.4.23（≥47.1.3 即可） | |
| Applied Energistics 2 | 15.4.10 | 硬依赖 |
| GuideME | 20.1.7 | AE2 的硬依赖 |
| Apotheosis | 1.20.1-7.4.8 | 硬依赖 |
| Placebo | 1.20.1-8.6.2 | Apotheosis 硬依赖 |
| ApothicAttributes | 1.20.1-1.3.5 | Apotheosis 硬依赖 |

## 构建

需要 **JDK 17**。

```bash
./gradlew build          # 产物在 build/libs/applied_apotheosis-<version>.jar
./gradlew runClient      # 启动开发环境客户端
./gradlew runServer      # 启动开发环境服务端（首次需在 run/eula.txt 写 eula=true）
./gradlew runServer -Pselftest        # 服务端自检：拆解 + 黑白名单 + 入网
./gradlew runClient -Pclientselftest  # 客户端自检：自动打开 GUI 并截图
```

### 开发者自检

`-Pselftest` 会在服务端世界里自动搭一个微型 ME 网络（ME 分解器 + 创造能源元件 + ME 箱子 + 1k 元件），
用 Apotheosis 的 API 现场随机生成**神话**词缀装备，验证注册、卡牌并行度、黑白名单、拆解与入网，
然后自动关服：

```
[selftest] parallelism with 1 salvage card  = 1 item(s) per tick (expect 1)
[selftest] parallelism with 3 salvage cards = 3 item(s) per tick (expect 3)
[selftest] lowest-rarity gear (common) into input -> leftover = 0 (0 = correctly accepted)
[selftest] common gear into filter list  -> valid = true    (true = correctly accepted)
[selftest] epic gear into input          -> leftover = 0    (0 = correctly accepted)
[selftest] mythic gem = 1 gem | rarity = apotheosis:mythic | hasAffixes = false | accepted = false
[selftest] mythic rarity but empty affix list -> hasAffixes = false | accepted = false
[selftest] BLACKLIST + listed sword -> sword leftover = 1 (1 = correctly blocked)
[selftest] WHITELIST + unlisted chestplate -> leftover = 1 (1 = correctly blocked)
[selftest] gear consumed at tick 21 (machine salvaged it)
[selftest] tick 40: node ready=true active=true powered=true | input=0 air | network=[3 x apotheosis:mythic_material]
```

`-Pclientselftest` 会以 quick-play 打开存档 `run/saves/selftest`，自动摆放机器、用神化装备填满过滤列表并
**打开 GUI**，然后在 `run/screenshots/` 里留下一张界面截图（用于检查样式表与槽位布局）。

> 两个自检由系统属性 `applied_apotheosis.selftest` / `applied_apotheosis.clientselftest` 控制，
> 只在对应 Gradle 参数下开启，正常游戏不会触发。

### 如果 Gradle 报 native 库/缓存写入失败

某些受限环境（或权限不足的用户目录）无法写入默认的 `~/.gradle`。
把 Gradle 家目录指到工程内即可：

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-home"   # 已被 .gitignore 忽略
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat build
```

### IntelliJ IDEA 注意事项

工程用 ForgeGradle 6 + JDK 17。装了 **Minecraft Development** 插件时，IDE 会把 ForgeGradle
解压出的 Minecraft 反编译源码（`build/tmp/.cache/expanded/...`）挂进模块；如果按 IDE 自己的
**Build**（Ctrl+F9）或对这类文件按"编译当前文件"，就会满屏"找不到符号"。

正确做法（二选一）：

1. `Settings → Build Tools → Gradle`：`Build and run using` 与 `Run tests using` 都设为 **Gradle**，
   `Gradle JVM` 设为 **17**；之后 Ctrl+F9 会委托给 Gradle。
2. 直接用 Gradle 面板 `Tasks → forgegradle runs → runClient`，或在终端跑 `gradlew runClient`。

## 工程结构

```
src/main/java/com/jianjian/appliedapotheosis/
├── AppliedApotheosis.java                 模组入口
├── block/MeSalvagerBlock.java             方块（右键开界面 / 投放装备）
├── blockentity/MeSalvagerBlockEntity.java 机器逻辑（ME 网络节点 / 拆解 / 耗电 / 黑白名单）
├── filter/FilterMode.java                 不过滤 / 白名单 / 黑名单
├── item/SalvageCardItem.java              分解卡（AE2 UpgradeCardItem）
├── menu/MeSalvagerMenu.java               容器菜单（输入 / 过滤 / 升级 / 玩家背包槽）
├── client/MeSalvagerScreen.java           AE2 风格界面
├── client/FilterModeButton.java           三态过滤按钮（WHITELIST / BLACKLIST 图标）
├── registry/                              方块 / 物品 / 方块实体 / 菜单 / 创造标签页注册
└── dev/                                   开发者自检（仅系统属性开启时生效）
src/main/resources/
├── META-INF/mods.toml                     模组元数据与依赖声明
├── assets/ae2/screens/me_salvager.json    GUI 样式表（AE2 只从 ae2 命名空间读取样式）
├── assets/applied_apotheosis/             模型、方块状态、贴图（含 GUI 背景）、语言文件
└── data/applied_apotheosis/               战利品表、合成配方
```

## 已知限制

- 方块没有朝向属性，正面固定为北面。
- 稀有度门槛是**写死的常量**（`MINIMUM_RARITY = apotheosis:common`，即全部收），不能在游戏里调；
  要做成可配置（GUI 按钮或配置文件）需要再加一层。
- 过滤列表只按物品种类匹配，同一物品种类的不同词缀无法区分。
