# MoreBuildingBlocks

为陶瓦家族补上楼梯与台阶，并让**所有原版半砖**都能竖直放置。

- Minecraft **26.3**（Wilderness Bound，2026-09-15 正式版）
- Fabric Loader 0.19.5 / Fabric API 0.162.0+26.3 / Fabric Loom 1.18.3
- **Mojang 官方映射（mojmap）**——26.x 起 Fabric 不再提供 Yarn 映射
- Java 25 / Gradle 9.7.1

## 内容

### 1. 陶瓦家族楼梯与台阶（66 个新方块）

| 基础方块 | 数量 | 新增 |
|---|---|---|
| 陶瓦 Terracotta | 1 | 楼梯 + 台阶 |
| 染色陶瓦 Dyed Terracotta | 16 | 楼梯 + 台阶 |
| 带釉陶瓦 Glazed Terracotta | 16 | 楼梯 + 台阶 |
| **合计** | **33** | **66** |

每个新方块都通过 `BlockBehaviour.Properties.ofFullCopy` / `ofLegacyCopy`
继承对应基础方块的爆炸抗性、硬度、音效组与可燃性，与原版陶瓦一致。

### 2. 竖半砖（不新增方块）

**全部 101 个原版半砖**（含 16 羊毛半砖、16 混凝土半砖、8 铜半砖）都获得了竖直放置形态。
实现方式不是新增方块，而是用 Mixin 给原版 `SlabBlock` 增加两个状态属性：

- `vertical`（布尔）：是否为竖直形态
- `axis`（原版 `BlockStateProperties.AXIS`，直接复用）：薄板朝向哪个轴

配合原版自带的 `type`（`bottom` / `top` / `double`）表示「填满哪一半」，
竖直半砖只用 **2 个新属性**就完整表达了 4 个朝向 × 上下两半的 8 种形态。

- **放置逻辑**：看向**侧面**一律平放（与原版一致）；看向**上/下表面**时按视线落点判定——
  命中点离方块中轴两轴都在 1/4 格以内就是平放；两轴都超出才是竖放，薄板沿**落点更偏的那个轴**
  竖起，`type` 按竖放语义取（竖放时 `type=top` 是**负半轴**，与平放相反）。
- **续墙**：已经立起来的竖半砖（`vertical=true` 且非 `double`）另有一套：
  - 点**开面**（封住空半的那个平面，也就是玩家看到这块半砖的那一面，`VerticalSlab.openFace`）
    = 这块只做了一半 → 留在原格补成 `double`；
  - 点**两个薄侧面**或**填满那半远端的格子边界** = 墙继续 → 落到相邻格，并填**被点那块自己的半**
    （不是点击点最近的那半，否则墙会在接缝处转 90°）。
- **碰撞箱**：8/16 厚度，`getShape` 按 `axis` + `type` 返回对应薄板盒。
- **旋转与镜像**：`BlockBehaviourMixin` 覆盖 `rotate` / `mirror`，让竖直半砖
  随结构方块旋转/镜像正确改变朝向。
- **上下颠倒**：竖直形态的 `type` 沿用「立起来的那块板占哪半」的语义——`type=bottom` 是**正半轴**、
  `type=top` 是**负半轴**（与平放恰好相反，见 `VerticalSlab.Fill.positive()`）；两种都支持，也是竖半砖能从两水平面各自竖起半格的原因。

### 3. 配方（全部由 datagen 生成）

- 工作台：**6 个方块 → 4 个楼梯**、**3 个方块 → 6 个台阶**（标准原版配方，不可逆）
- 切石机：33 种基础方块全部可切出楼梯（1 个）与台阶（2 个）

### 4. 创造模式物品栏

新增 **MoreBuildingBlocks** 标签页，按「原版陶瓦 → 16 染色陶瓦 → 16 带釉陶瓦 →
各自楼梯与台阶」的顺序收录全部 99 个物品。

## 纹理与模型

**不新增任何纹理文件**，全部复用原版纹理：

- 普通/染色陶瓦：用原版 `slab` / `stairs` 系列模板，槽位 `#bottom` / `#top` / `#side`
  直接指向 `minecraft:block/<原名>`。
- **带釉陶瓦**：图案是有方向的，直接套用平面 UV 会让切角后花纹错位。
  因此楼梯与台阶的几何由模组按原版 `template_glazed_terracotta` 的
  **逐面旋转表**（`down 0, up 0, north 90, south 270, west 0, east 180`）自行生成，
  保证花纹方向与整方块一致。
- 竖半砖：**每一个半砖都有自己的独立模型**，由 datagen 生成在
  `assets/morebuildingblocks/models/vertical_slab/<方块名>.json`（101 个原版半砖 + 33 个模组半砖）。
  模型是朝北、厚度 8/16 的薄板，按 `axis` 绕 Y 轴旋转 90°/270° 得到两个朝向。
  纹理不是从基础方块猜测的，而是照抄原版 `models/block/<半砖>.json` 的 `bottom` / `top` / `side`
  三槽（例：`smooth_stone_slab` 的侧面是 `smooth_stone_slab_side`，
  `sandstone_slab` 的上下分别是 `sandstone_bottom` / `sandstone_top`），
  因为**从原版半砖的方块状态走到的模型不会继承任何子模型的纹理映射**，
  `#bottom` / `#top` / `#side` 必须自己写死。

### 26.3 上的 API 差异（与常见 Fabric 教程不同，实现时已按实际情况调整）

| 预期写法 | 26.3 的实际情况 | 本模组的做法 |
|---|---|---|
| Fabric datagen 的 `VERTICAL_SLAB` ModelTemplate | **不存在**，Fabric API 0.162.0+26.3 里没有 `ModelTemplate` / `BlockStateModelGenerator` 这两个类 | 自己生成模型 JSON（`ModelInstance` 即 `Supplier<JsonElement>`） |
| `BlockStateModelGenerator` 生成方块状态 | 26.3 改为原版 `net.minecraft.client.data.models.BlockModelGenerators` + `MultiVariantGenerator` / `PropertyDispatch` | 直接用原版 API，模型 id 与槽位命名与原版模板一致 |
| 用方块状态 JSON 给原版半砖加竖直形态 | 会覆盖原版命名空间下的 101 个文件 | 改为 `fabric-model-loading-api-v1` 的 `BlockStateResolver`，在运行时提供额外状态 |

## 目录结构

```
src/main/java/com/camelie/morebuildingblocks/
├── MoreBuildingBlocks.java          主入口
├── block/                           方块状态属性与竖半砖状态助手
├── registry/                        注册体系（方块、物品、家族、创造标签）
├── mixin/                           竖半砖 Mixin
└── datagen/                         配方与战利品表（主源集）
src/client/java/com/camelie/morebuildingblocks/client/
├── MoreBuildingBlocksClient.java    客户端入口
├── MBVanillaSlabModelPlugin.java    原版半砖运行时模型解析
├── datagen/                         模型与方块状态 datagen（客户端源集）
└── gametest/                        客户端截图测试
```

注册体系由 `MBRegistry`（底层注册）、`MBBlockFamilies`（楼梯/台阶成对注册）与
`MBBlocks`（33 个基础方块）三层组成，楼梯、台阶、竖半砖共用同一套注册代码，无重复。

## 构建与运行

```bash
./gradlew build             # 编译并打包
./gradlew runClient         # 启动客户端
./gradlew runServer         # 启动专用服务端
./gradlew runDatagen        # 重新生成 src/main/generated 下的全部 JSON
./gradlew runClientGameTest # 自动化验收：截图 + 断言
```

生成物写在 `src/main/generated/`，**需要一并提交**：执行 `runDatagen` 后
工作区应当没有 diff，这正是「datagen 可复现」的验收方式
（内容未变时输出为 `removed stale: 0, written: 0`）。

`build` / `runDatagen` / `runClientGameTest` 请**分开单跑**：一条命令里连跑时
`:runDatagen` 会被 Gradle 拒绝（`Task ':sourcesJar' uses this output of task ':runDatagen'
without declaring an explicit or implicit dependency`），因为 `src/main/generated` 被两边同时读写。
另外本仓库的 `main` 没有设置 upstream tracking，推送要写全 `git push origin main`。

## 自动化验收（`runClientGameTest`）

基于 Fabric 的 `fabric-client-gametest-api-v1`，一次运行覆盖下列检查，
截图写到 `run/screenshots/`：

| 检查 | 内容 |
|---|---|
| `verifyShapes` | 101 个原版半砖在 `axis=x` / `axis=z` 下碰撞箱厚度均为 0.5、高度为 1；水平半砖仍是 0～0.5；66 个陶瓦楼梯 `maxY=1.0`、台阶 `maxY=0.5` |
| `verifyPlacement` | 用真实 `BlockPlaceContext` 放置：点东半边 → 朝东的竖半砖（`axis=x` + `type=top`）；点北半边 → 朝北（`axis=z` + `type=bottom`）；点侧面一律平放；点正中心仍是水平半砖 |
| `verifyUprightStacking` | 竖半砖的**开面**（封住空半的平面，先按形状验一遍它确实是那一面）与**填满那半远端的格子边界**各点一次：开面 → 留在原格补成 double，远端边界 → 越到相邻格并填被点那块自己的半 |
| `verifyWallContinuation` | 一堵 `axis=z` 的竖半砖墙：两个薄侧面与填满那半远端的格子边界都续到相邻格，且新方块填的是**被点那块自己的半**；点开面则留在原格补成 double |
| `verifyDrops` | 破坏楼梯与台阶后各自掉落自身 |
| `verifyGlazedSlabs` | 带釉陶瓦半砖与普通半砖**逐格对照**：对每个带釉半砖，用同一份点击（6 个面 × 4 个朝向点空地、4 种竖放半 × 6 个面点竖半砖）分别打在普通半砖与带釉半砖上，要求 `Fill`、是否竖放完全一致；带釉半砖另外单独验三件事——点竖半砖的**开面**必须留在原格补成 `double`、开面按**自己碰撞箱的空半**复核（这条防的是「开面被读成填满那半的边界」，对照检查看不见这种整体反向）、每次放置的 `GLAZED_FACING` 都等于玩家朝向的反方向 |
| `verifyCreativeTab` | 标签页注册在 `morebuildingblocks:main`，且按顺序收录全部 99 个方块，每个都有对应物品 |
| `verifyDedicatedServer` | 由测试自己拉起专用服务端并连接：服务端注册 66 个楼梯/台阶 + 33 个基础方块，客户端也认得这 99 个物品；服务端放置的方块能同步到客户端 |
| `measureGlazed` | 带釉陶瓦花纹方向的**像素级**对照：把原版整方块与模组楼梯/台阶各按南/西/北/东填满 3×3 补丁、从正上方逐一拍照（12 张 `glazed-measure-*.png`），并把每一格服务端实际持有的状态写进日志（`glazed measurement <标签> held <状态>`）；两张图相减后能让它们一致的那个转角就是花纹的误差 |
| 截图 | 陶瓦家族全景、带釉陶瓦近景、带釉陶瓦楼梯近景、三段竖半砖长墙（6 张），外加 `measureGlazed` 的 12 张，共 18 张 |

> 手工验证多人时请注意：开发环境的客户端没有有效的 Mojang 会话，
> 直连专用服务端会在登录阶段卡在 `sessionserver.mojang.com/session/minecraft/join` 后断开。
> 因此多人验收由上面的 `verifyDedicatedServer` 承担。


## 已知限制（v0.0.1）

- **未提供语言文件**（`en_us` / `zh_cn` 均暂缺），方块在游戏中显示为翻译键。后续版本补齐。
- 仅承诺兼容 Minecraft **26.3**，不保证后续版本。
- 竖半砖通过 Mixin 扩展原版 `SlabBlock`，与同样改动 `SlabBlock` 状态属性的模组可能冲突。

## 许可证

MIT
