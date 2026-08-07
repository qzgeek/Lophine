# Lophine 假人改版文档

> 基于 Lophine 26.1.2 的假人改版说明。当前实现的核心原则是：GUI 只做薄壳，真正的权限与行为都落在 `/bot` 命令层。

## 先看结论

- 当前主入口是 `/bot`
- 推荐的命令顺序是：`/bot <动作> <名字> [参数]`
- 玩家手册见：`docs/BOT_PLAYER_GUIDE.md`
- GUI 只负责发命令，不直接改 bot 状态
- 假人列表同时包含在线 bot 和离线持久化记录
- 权限系统是“主人/管理员 > 个人权限(PSet) > 公开权限(GSet) > 拒绝”
- 经验系统内部只存总经验点数，等级实时反推

## 当前仓库里的核心文件

- `lophine-server/src/main/java/fun/bm/lophine/command/player/PlayerCommand.java`
- `lophine-server/src/main/java/org/leavesmc/leaves/bot/gui/BotGui.java`
- `lophine-server/src/main/java/org/leavesmc/leaves/bot/gui/BotGuiHolder.java`
- `lophine-server/src/main/java/org/leavesmc/leaves/bot/BotList.java`
- `lophine-server/src/main/java/org/leavesmc/leaves/bot/BotOwnerRegistry.java`
- `lophine-server/src/main/java/org/leavesmc/leaves/bot/ServerBot.java`
- `lophine-server/src/main/java/fun/bm/lophine/config/modules/function/FakeplayerConfig.java`
- `lophine-server/src/main/java/fun/bm/lophine/carpet/config/modules/FakePlayerCompatConfig.java`

## 快速开始

```bash
/bot
/bot help
/bot spawn test
/bot menu test
```

快捷操作：

- 蹲下 + 空手右键假人：打开控制面板
- GUI 的所有按钮：最终都会转成 `/bot ...` 命令

## 命令总览

### 1) 入口与帮助

| 命令 | 说明 |
|---|---|
| `/bot` | 打开假人列表主界面 |
| `/bot gui` | 同上 |
| `/bot help [页码]` | 打开帮助，当前共 3 页 |

### 2) 创建、召回、删除

| 命令 | 说明 |
|---|---|
| `/bot spawn <名字>` | 召唤假人 |
| `/bot spawn <名字> time <秒>` | 延迟召唤 |
| `/bot kill <名字>` | 终止假人（非彻底删除） |
| `/bot remove <名字>` | 彻底删除假人，数据不可恢复 |
| `/bot menu <名字>` | 打开该假人的控制面板 |
| `/bot tp <名字>` | 把假人传送到自己身边 |
| `/bot echest <名字>` | 打开假人的末影箱 |
| `/bot inventory <名字>` | 打开假人的背包界面 |
| `/bot save <名字>` | 保存为可恢复数据 |
| `/bot load <名字>` | 从可恢复数据加载 |

说明：

- `kill` 只是“下线/终止”语义
- `remove` 才是彻底删除，会清数据文件并移除所有权记录
- 主面板里的“删除该假人”对应的是彻底删除

### 3) 动作控制

| 命令 | 说明 |
|---|---|
| `/bot sneak <名字>` / `/bot unsneak <名字>` | 切换潜行 |
| `/bot sprint <名字>` / `/bot unsprint <名字>` | 切换疾跑 |
| `/bot attack <名字> [continuous]` | 攻击 |
| `/bot use <名字> [continuous]` | 使用物品 |
| `/bot break <名字> [continuous]` | 挖掘 |
| `/bot jump <名字>` | 跳跃 |
| `/bot drop <名字>` | 丢弃主手物品 |
| `/bot swapHands <名字>` | 交换主副手 |
| `/bot mount <名字>` | 骑乘 |
| `/bot dismount <名字>` | 下马 |
| `/bot look <名字> <north/south/east/west/up/down>` | 看向方向 |
| `/bot look <名字> at <坐标>` | 看向指定坐标 |
| `/bot turn <名字> <left/right/back/rotation>` | 转身 |
| `/bot move <名字> <forward/backward/left/right>` | 移动 |
| `/bot stop <名字>` | 停止所有动作 |
| `/bot actionstop <名字> <动作>` | 停止指定动作 |
| `/bot hotbar <名字> <1-9>` | 切换快捷栏 |

这里最重要的一点是：参数顺序是“动作 → 名字 → 参数”，不是“名字 → 动作”。

### 4) 经验系统

| 命令 | 说明 |
|---|---|
| `/bot xp <名字> take <点数>` | 从假人取经验点数 |
| `/bot xp <名字> level <等级>` | 从假人取经验等级 |
| `/bot xp <名字> give <点数>` | 给假人经验点数 |
| `/bot xp <名字> level give <等级>` | 给假人经验等级 |
| `/bot xp <名字> clear` | 清空经验 |

经验系统的实现要点：

- 假人内部保存的是 `totalExperience`
- 等级和进度是实时推算出来的
- 取/给等级时，最终都会折算成经验点数
- GUI 里的 4 个 XP 页面分别是：取级、取点、给级、给点

### 5) 配置与协作者

| 命令 | 说明 |
|---|---|
| `/bot config <名字>` | 打开假人配置面板 |
| `/bot config <名字> <setting> <value>` | 直接修改配置 |
| `/bot col <名字> add <玩家>` | 添加协作者 |
| `/bot col <名字> remove <玩家>` | 移除协作者 |
| `/bot col <名字> list` | 查看协作者列表 |

补充说明：

- `col add/remove` 都支持 `all`，表示公开访问或全部移除
- 不能把主人自己加成协作者
- 协作者列表和公开访问都会同步进 `BotOwnerRegistry`

## GUI 结构

### 假人列表

- 27 格布局
- 显示在线 bot 和离线持久化记录
- 在线 bot 来自 `BotList.INSTANCE.bots`
- 离线 bot 来自 `BotOwnerRegistry.INSTANCE`
- `+ 创建假人` 会让玩家在聊天栏输入名字

### 控制面板

- 27 格布局，分页
- 第 1 页：背包、末影箱、动作、设置、权限管理、经验、传送到我
- 第 2 页：删除该假人

### 动作面板

- 使用上游 `BotActionGuiContainer/BotActionGuiMenu`
- 54 格布局，先选择 Start / Stop
- Start 模式按动作树逐层选择并执行命令
- Stop 模式列出当前运行中的动作，可按动作 UUID 停止
- 控制面板里的“动作”按钮现在直接打开这套上游动作 GUI
- 2026-08 排查结论：生产服 action GUI 无法让假人移动的首要根因不是 LuoOS 登录保护，而是 `/mc/lophine_config/lophine_carpet_config.toml` 中 `fakePlayerTicksLikeRealPlayer = true`。该配置会让假人走 NETWORK tick，动作队列不会正常执行；生产应改为 `false`。
- action GUI 当前已汉化：启动/停止、返回/主页、分页、命令预览、动作名称、延迟/间隔/次数、停止动作提示均为中文。

### 设置面板

- 27 格布局，分页
- 主要切换：死亡重生、跳过睡眠、始终发送数据、生成幻翼、死亡不掉落、模拟距离、Tick 类型、定位栏

### 权限管理

- 27 格布局，分页
- 支持公开权限(GSet)
- 支持每个协作者的个人权限(PSet)
- 支持在线玩家选择器添加协作者

## 权限模型

权限判断顺序：

1. 主人 / OP
2. PSet：个人权限
3. GSet：公开权限
4. 默认拒绝

当前 20 个权限标志：

`inv, echest, attack, use, break, sneak, sprint, jump, drop, swap, mount, dismount, move, look, tp, set, xp, despawn, remove, spawn`

含义简述：

- `inv` / `echest` / `xp` / `set` / `spawn` / `remove` / `despawn`
- 以及动作类权限：攻击、使用、挖掘、潜行、疾跑、跳跃、丢弃、换手、骑乘、下马、移动、视角、传送

特别约束：

- 不能把自己添加为协作者
- 公开访问开关和协作者变更都会写回 `BotOwnerRegistry`

## 数据持久化

当前主要数据文件都放在 `world/lophine_config/` 下：

- `bot_owners.dat`：所有权、协作者、GSet/PSet
- `bot_configs/<uuid>.dat`：假人配置
- `bot_inventory/<uuid>.dat`：背包、装备、经验
- `fakeplayerdata/`：手动保存数据
- `resume_fakeplayerdata/`：自动恢复数据

说明：

- `kill` 不等于彻底删除
- `remove` 才会清掉所有关联数据
- 只有启用 resident / 手动保存时，假人才会在重启后恢复

## 关键代码机制

### 1) ServerBot.interact() 的空手蹲下拦截

这是防止原版玩家背包自动打开的关键：

- 玩家蹲下
- 主手空手
- 右键假人
- `ServerBot.interact()` 返回 `PASS`

这样 GUI 才能接管交互。

### 2) GUI 作为薄壳

所有按钮最终都调用 `player.performCommand(...)`，例如：

- `bot attack test continuous`
- `bot look test north`
- `bot move test forward`
- `bot actionstop test attack`
- `bot xp test take 10`

GUI 不直接改 bot 的内部行动队列。

### 3) 名字处理

代码里同时处理两种名字：

- raw name：玩家输入的原名
- full name：`prefix + rawName + suffix`

这样可以避免 `BOT_` 前缀导致的查找和命令失配。

### 4) XP 页面页码状态

`PANEL` 和 `XP` 共用同一种容器类型，所以代码里用 `XP_PAGE` 单独区分上下文。

注意三处必须清理：

- 关闭窗口
- 返回面板
- 返回列表

否则“下一页”会串到 XP 页面。

## 重要坑位

### 1) `fakePlayerTicksLikeRealPlayer = true` 会让假人“只挨打不干活”

这是最常见的误判。

如果这个开关开着，bot 会走网络 tick 路径，`runAction()` 和 `doTick()` 可能被跳过，表现就是：

- 能受伤
- 不能交互
- 不能推挤
- 动作不执行

排查顺序里，这一条要放第一位。

### 2) GUI 和命令层必须保持对等

不要让 GUI 做命令层做不到的事，也不要让命令层有 GUI 没有的能力却不补入口。

### 3) 如果看到旧入口写法

以当前实现为准，主入口应按 `/bot` 理解。

## 经验页的交互方式

- 点按钮：直接发命令
- 选“自定义”：聊天栏输入数字
- 4 个页面分别对应取级 / 取点 / 给级 / 给点

## 配置项建议

如果你只想要“当前这套改版”的基本可用配置，至少保证：

- GUI 开启
- 快捷键开启
- 假人上限合理
- `fakePlayerTicksLikeRealPlayer = false`
- `respawnOnDeath` 按需要设置

## 这份文档和代码的对应关系

这份文档对应的是当前仓库里的实际实现，不是旧版 Leaves 的说明。
如果以后再改命令树、GUI 布局或权限模型，优先先改代码，再同步更新这里和帮助文本。
