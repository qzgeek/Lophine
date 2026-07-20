package fun.bm.lophine.command.player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.lophine.config.modules.function.FakeplayerConfig;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.PaperCommandSourceStack;
import io.papermc.paper.command.brigadier.PaperCommands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.bot.BotConfigMenu;
import org.leavesmc.leaves.bot.BotCreateState;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.BotOwnerRegistry;
import org.leavesmc.leaves.bot.BotUtil;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.bot.gui.BotGui;
import org.leavesmc.leaves.bot.agent.Actions;
import org.leavesmc.leaves.bot.agent.Configs;
import org.leavesmc.leaves.bot.agent.actions.AbstractBotAction;
import org.leavesmc.leaves.bot.agent.actions.ServerMoveAction;
import org.leavesmc.leaves.bot.agent.configs.AbstractBotConfig;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.leavesmc.leaves.entity.bot.action.MoveAction.MoveDirection;
import org.leavesmc.leaves.event.bot.BotActionStopEvent;
import org.leavesmc.leaves.event.bot.BotCreateEvent;
import org.leavesmc.leaves.event.bot.BotRemoveEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.spaces;

@SuppressWarnings("unchecked")
public final class PlayerCommand {

    private static final String PERMISSION_BASE = "bukkit.command.player";
    private static boolean registered = false;

    private PlayerCommand() {
    }

    public static void register() {
        if (registered) return;

        PaperCommands.INSTANCE.setValid();
        CommandDispatcher<CommandSourceStack> dispatcher = PaperCommands.INSTANCE.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> command = LiteralArgumentBuilder.<CommandSourceStack>literal("bot")
                .requires(src -> src.getSender().hasPermission(PERMISSION_BASE))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof org.bukkit.entity.Player p) { BotGui.openMain(p); return 1; }
                    return 0;
                })
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("gui").executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof org.bukkit.entity.Player p) { BotGui.openMain(p); return 1; }
                    return 0;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("help")
                        .executes(ctx -> showHelp(ctx, 1))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("page", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> showHelp(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                        )
                )
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemainingLowerCase();
                            for (String name : getPlayerNameSuggestions()) {
                                if (name.toLowerCase().startsWith(input)) {
                                    builder.suggest(name);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("spawn")
                                .executes(PlayerCommand::spawn)
                        )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("time")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("seconds", IntegerArgumentType.integer(1, 86400))
                                                .executes(PlayerCommand::spawnDelayed)
                                        )
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("kill").executes(PlayerCommand::kill))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("menu").executes(ctx -> { ServerBot b = getBot(ctx); if(b!=null&&ctx.getSource().getSender() instanceof org.bukkit.entity.Player p) BotGui.openPanel(p,b); return 1; }))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("echest").executes(ctx -> {
                            ServerBot bot = getBot(ctx);
                            if (bot != null && ctx.getSource().getSender() instanceof org.bukkit.entity.Player p && hasManagePermission(bot, p)) {
                                BotGui.openEchest(p, bot);
                            }
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("tp").executes(PlayerCommand::teleportHere))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("actionstop")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("action", StringArgumentType.word())
                                        .executes(ctx -> {
                                            if (cantManipulate(ctx)) return 0;
                                            ServerBot bot = getBot(ctx);
                                            if (bot == null) return 0;
                                            String name = StringArgumentType.getString(ctx, "action");
                                            stopAction(bot, name);
                                            ctx.getSource().getSender().sendMessage(Component.text("已停止 " + name, NamedTextColor.GREEN));
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop").executes(manipulation(bot -> {
                            stopAllActions(bot);
                            bot.zza = 0.0f;
                            bot.xxa = 0.0f;
                        })))
                        .then(makeActionLiteral("use", "use_auto"))
                        .then(makeActionLiteral("attack", "attack"))
                        .then(makeActionLiteral("break", "break"))
                        .then(makeActionLiteral("jump", "jump"))
                        .then(makeDropLiteral("drop"))
                        .then(makeActionLiteral("swapHands", "swap"))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("hotbar")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("slot", IntegerArgumentType.integer(1, 9))
                                        .executes(PlayerCommand::hotbar)
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("mount")
                                .executes(manipulation(bot -> startAction(bot, "mount", ActionMode.ONCE)))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("anything")
                                        .executes(manipulation(bot -> startAction(bot, "mount", ActionMode.ONCE)))
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("dismount")
                                .executes(manipulation(bot -> bot.stopRiding()))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("sneak")
                                .executes(manipulation(bot -> startAction(bot, "sneak", ActionMode.CONTINUOUS)))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("unsneak")
                                .executes(manipulation(bot -> stopAction(bot, "sneak")))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("sprint")
                                .executes(manipulation(bot -> startAction(bot, "sprint", ActionMode.CONTINUOUS)))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("unsprint")
                                .executes(manipulation(bot -> stopAction(bot, "sprint")))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("look")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("north")
                                        .executes(manipulation(bot -> bot.setYRot(180.0f)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("south")
                                        .executes(manipulation(bot -> bot.setYRot(0.0f)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("east")
                                        .executes(manipulation(bot -> bot.setYRot(-90.0f)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("west")
                                        .executes(manipulation(bot -> bot.setYRot(90.0f)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("up")
                                        .executes(manipulation(bot -> bot.setXRot(-90.0f)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("down")
                                        .executes(manipulation(bot -> bot.setXRot(90.0f)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("at")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("position", Vec3Argument.vec3())
                                                .executes(ctx -> {
                                                    ServerBot bot = getBot(ctx);
                                                    if (bot == null) return 0;
                                                    CommandContext<net.minecraft.commands.CommandSourceStack> mcCtx = (CommandContext<net.minecraft.commands.CommandSourceStack>) (CommandContext<?>) ctx;
                                                    Vec3 pos = Vec3Argument.getVec3(mcCtx, "position");
                                                    bot.faceLocation(new Location(bot.getBukkitEntity().getWorld(), pos.x, pos.y, pos.z));
                                                    return 1;
                                                })
                                        )
                                )
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("direction", RotationArgument.rotation())
                                        .executes(ctx -> {
                                            ServerBot bot = getBot(ctx);
                                            if (bot == null) return 0;
                                            CommandContext<net.minecraft.commands.CommandSourceStack> mcCtx = (CommandContext<net.minecraft.commands.CommandSourceStack>) (CommandContext<?>) ctx;
                                            Vec2 rot = RotationArgument.getRotation(mcCtx, "direction").getRotation(mcCtx.getSource());
                                            bot.setYRot(rot.y);
                                            bot.setXRot(rot.x);
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("turn")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("left")
                                        .executes(manipulation(bot -> bot.setYRot(bot.getYRot() - 90.0f)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("right")
                                        .executes(manipulation(bot -> bot.setYRot(bot.getYRot() + 90.0f)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("back")
                                        .executes(manipulation(bot -> bot.setYRot(bot.getYRot() + 180.0f)))
                                )
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("rotation", RotationArgument.rotation())
                                        .executes(ctx -> {
                                            ServerBot bot = getBot(ctx);
                                            if (bot == null) return 0;
                                            CommandContext<net.minecraft.commands.CommandSourceStack> mcCtx = (CommandContext<net.minecraft.commands.CommandSourceStack>) (CommandContext<?>) ctx;
                                            Vec2 rot = RotationArgument.getRotation(mcCtx, "rotation").getRotation(mcCtx.getSource());
                                            bot.setYRot(bot.getYRot() + rot.y);
                                            bot.setXRot(bot.getXRot() + rot.x);
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("move")
                                .executes(manipulation(bot -> stopAction(bot, "move")))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("forward")
                                        .executes(manipulation(bot -> startMoveAction(bot, MoveDirection.FORWARD)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("backward")
                                        .executes(manipulation(bot -> startMoveAction(bot, MoveDirection.BACKWARD)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("left")
                                        .executes(manipulation(bot -> startMoveAction(bot, MoveDirection.LEFT)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("right")
                                        .executes(manipulation(bot -> startMoveAction(bot, MoveDirection.RIGHT)))
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("col")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("target", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    String input = builder.getRemainingLowerCase();
                                                    builder.suggest("all");
                                                    for (Player p : Bukkit.getOnlinePlayers()) {
                                                        String name = p.getName();
                                                        if (name.toLowerCase().startsWith(input)) {
                                                            builder.suggest(name);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    ServerBot bot = getBot(ctx);
                                                    if (bot == null) return 0;
                                                    if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                                        return 0;
                                                    }
                                                    String targetName = StringArgumentType.getString(ctx, "target").toLowerCase(Locale.ROOT);
                                                    if (targetName.equals("all")) {
                                                        bot.collaborators.add(ServerBot.PUBLIC_ACCESS_UUID);
                                                        ctx.getSource().getSender().sendMessage(Component.text("已将所有玩家添加为 " + bot.getBukkitEntity().getName() + " 的协作者", NamedTextColor.YELLOW));
                                                    } else {
                                                        Player target = Bukkit.getPlayerExact(targetName);
                                                        if (target == null) {
                                                            ctx.getSource().getSender().sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                                                            return 0;
                                                        }
                                                        bot.collaborators.add(target.getUniqueId());
                                                        ctx.getSource().getSender().sendMessage(Component.text("已将 " + target.getName() + " 添加为 " + bot.getBukkitEntity().getName() + " 的协作者", NamedTextColor.YELLOW));
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("target", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    String input = builder.getRemainingLowerCase();
                                                    builder.suggest("all");
                                                    for (String name : getCollaboratorNames(ctx)) {
                                                        if (name.toLowerCase().startsWith(input)) {
                                                            builder.suggest(name);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    ServerBot bot = getBot(ctx);
                                                    if (bot == null) return 0;
                                                    if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                                        return 0;
                                                    }
                                                    String targetName = StringArgumentType.getString(ctx, "target").toLowerCase(Locale.ROOT);
                                                    if (targetName.equals("all")) {
                                                        bot.collaborators.remove(ServerBot.PUBLIC_ACCESS_UUID);
                                                        ctx.getSource().getSender().sendMessage(Component.text("已移除 " + bot.getBukkitEntity().getName() + " 的所有协作者", NamedTextColor.YELLOW));
                                                    } else {
                                                        String[] names = getCollaboratorNames(ctx);
                                                        Player target = null;
                                                        for (String n : names) {
                                                            Player p = Bukkit.getPlayerExact(n);
                                                            if (p != null && p.getName().equalsIgnoreCase(targetName)) {
                                                                target = p;
                                                                break;
                                                            }
                                                        }
                                                        if (target == null) {
                                                            ctx.getSource().getSender().sendMessage(Component.text("找不到该协作者", NamedTextColor.RED));
                                                            return 0;
                                                        }
                                                        if (bot.collaborators.remove(target.getUniqueId())) {
                                                            ctx.getSource().getSender().sendMessage(Component.text("已将 " + target.getName() + " 从 " + bot.getBukkitEntity().getName() + " 的协作者列表中移除", NamedTextColor.YELLOW));
                                                        } else {
                                                            ctx.getSource().getSender().sendMessage(Component.text("该玩家不是协作者", NamedTextColor.RED));
                                                        }
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                                        .executes(ctx -> {
                                            ServerBot bot = getBot(ctx);
                                            if (bot == null) return 0;
                                            if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                                ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                                return 0;
                                            }
                                            if (bot.collaborators.contains(ServerBot.PUBLIC_ACCESS_UUID)) {
                                                ctx.getSource().getSender().sendMessage(Component.text("假人 " + bot.getBukkitEntity().getName() + " 的协作者: all", NamedTextColor.YELLOW));
                                            } else if (bot.collaborators.isEmpty()) {
                                                ctx.getSource().getSender().sendMessage(Component.text("假人 " + bot.getBukkitEntity().getName() + " 没有协作者", NamedTextColor.YELLOW));
                                            } else {
                                                Set<String> names = new LinkedHashSet<>();
                                                for (String name : getCollaboratorNames(ctx)) {
                                                    names.add(name);
                                                }
                                                ctx.getSource().getSender().sendMessage(Component.text("假人 " + bot.getBukkitEntity().getName() + " 的协作者: " + String.join(", ", names), NamedTextColor.YELLOW));
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("config")
                                .executes(ctx -> {
                                    if (cantManipulate(ctx)) return 0;
                                    ServerBot bot = getBot(ctx);
                                    if (bot == null) return 0;
                                    if (!FakeplayerConfig.canModifyConfig) {
                                        ctx.getSource().getSender().sendMessage(Component.text("修改配置功能已被禁用", NamedTextColor.RED));
                                        return 0;
                                    }
                                    if (ctx.getSource().getSender() instanceof org.bukkit.entity.Player player) {
                                        ServerPlayer serverPlayer = (ServerPlayer) ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle();
                                        BotConfigMenu.open(serverPlayer, bot);
                                    }
                                    return 1;
                                })
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("configName", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (AbstractBotConfig<?, ?> c : Configs.getConfigs()) {
                                                builder.suggest(c.getName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("value", StringArgumentType.word())
                                                .executes(ctx -> setBotConfig(ctx))
                                        )
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("open")
                                .executes(ctx -> {
                                    ServerBot bot = getBot(ctx);
                                    if (bot == null) return 0;
                                    if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                        ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                        return 0;
                                    }
                                    org.bukkit.entity.Player player = ctx.getSource().getSender() instanceof org.bukkit.entity.Player p ? p : null;
                                    if (player != null) {
                                        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54, "Bot Inventory: " + bot.getBukkitEntity().getName());
                                        java.util.List<org.bukkit.inventory.ItemStack> bukkitItems = new java.util.ArrayList<>();
                                        for (net.minecraft.world.item.ItemStack item : bot.getInventory().getContents()) {
                                            bukkitItems.add(item.asBukkitCopy());
                                        }
                                        inv.setContents(bukkitItems.toArray(new org.bukkit.inventory.ItemStack[0]));
                                        player.openInventory(inv);
                                    }
                                    return 1;
                                })
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("xp")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("take")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerBot bot = getBot(ctx);
                                                    if (bot == null) return 0;
                                                    if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                                        return 0;
                                                    }
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    org.bukkit.entity.Player player = ctx.getSource().getSender() instanceof org.bukkit.entity.Player p ? p : null;
                                                    if (player == null) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("只有玩家才能获取经验", NamedTextColor.RED));
                                                        return 0;
                                                    }
                                                    return takeXp(bot, player, amount, false, ctx);
                                                })
                                        )
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("level")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerBot bot = getBot(ctx);
                                                    if (bot == null) return 0;
                                                    if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                                        return 0;
                                                    }
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    org.bukkit.entity.Player player = ctx.getSource().getSender() instanceof org.bukkit.entity.Player p ? p : null;
                                                    if (player == null) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("只有玩家才能操作经验", NamedTextColor.RED));
                                                        return 0;
                                                    }
                                                    return takeXpLevel(bot, player, amount, ctx);
                                                })
                                        )
                                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("give")
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> {
                                                            ServerBot bot = getBot(ctx);
                                                            if (bot == null) return 0;
                                                            if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                                                ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                                                return 0;
                                                            }
                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            org.bukkit.entity.Player player = ctx.getSource().getSender() instanceof org.bukkit.entity.Player p ? p : null;
                                                            if (player == null) {
                                                                ctx.getSource().getSender().sendMessage(Component.text("只有玩家才能操作经验", NamedTextColor.RED));
                                                                return 0;
                                                            }
                                                            return giveXpLevelToBot(bot, player, amount, ctx);
                                                        })
                                                )
                                        )
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("give")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerBot bot = getBot(ctx);
                                                    if (bot == null) return 0;
                                                    if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                                        return 0;
                                                    }
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    org.bukkit.entity.Player player = ctx.getSource().getSender() instanceof org.bukkit.entity.Player p ? p : null;
                                                    if (player == null) {
                                                        ctx.getSource().getSender().sendMessage(Component.text("只有玩家才能获取经验", NamedTextColor.RED));
                                                        return 0;
                                                    }
                                                    return giveXpToBot(bot, player, amount, false, ctx);
                                                })
                                        )
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                                        .executes(ctx -> {
                                            ServerBot bot = getBot(ctx);
                                            if (bot == null) return 0;
                                            if (!hasManagePermission(bot, ctx.getSource().getSender())) {
                                                ctx.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
                                                return 0;
                                            }
                                            bot.totalExperience = 0;
                                            bot.experienceLevel = 0;
                                            bot.experienceProgress = 0f;
            
                                            ctx.getSource().getSender().sendMessage(join(spaces(),
                                                    Component.text("已清空", NamedTextColor.GRAY),
                                                    Component.text(bot.getBukkitEntity().getName(), NamedTextColor.AQUA),
                                                    Component.text("的经验", NamedTextColor.GRAY)
                                            ));
                                            return 1;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("save")
                                .requires(src -> src.getSender().isOp() || src.getSender() instanceof org.bukkit.entity.Player)
                                .executes(ctx -> {
                                    ServerBot bot = getBot(ctx);
                                    if (bot == null) return 0;
                                    BotList.INSTANCE.saveBotResume(bot);
                                    ctx.getSource().getSender().sendMessage(join(spaces(),
                                            Component.text("已保存", NamedTextColor.GRAY),
                                            Component.text(bot.getBukkitEntity().getName(), NamedTextColor.AQUA),
                                            Component.text("的数据", NamedTextColor.GRAY)
                                    ));
                                    return 1;
                                })
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("load")
                                .requires(src -> src.getSender().isOp())
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String fullName = BotUtil.getFullName(name);
                                            ServerBot bot = BotList.INSTANCE.loadNewResumeBot(fullName);
                                            if (bot == null) {
                                                ctx.getSource().getSender().sendMessage(Component.text("找不到该保存的假人", NamedTextColor.RED));
                                                return 0;
                                            }
                                            ctx.getSource().getSender().sendMessage(join(spaces(),
                                                    Component.text("已加载", NamedTextColor.GRAY),
                                                    Component.text(bot.getBukkitEntity().getName(), NamedTextColor.AQUA),
                                                    Component.text("的数据", NamedTextColor.GRAY)
                                            ));
                                             return 1;
                                             })
                                             )
                                             );

        PaperCommands.INSTANCE.setValid();
        dispatcher.register(command);
        PaperCommands.INSTANCE.invalidate();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);

        if (Bukkit.getPluginManager().getPermission(PERMISSION_BASE) == null) {
            Bukkit.getPluginManager().addPermission(
                    new org.bukkit.permissions.Permission(PERMISSION_BASE, org.bukkit.permissions.PermissionDefault.TRUE));
        }

        registered = true;
    }

    public static void unregister() {
        if (!registered) return;
        PaperCommands.INSTANCE.setValid();
        PaperCommands.INSTANCE.getDispatcher().getRoot().removeCommand("player");
        PaperCommands.INSTANCE.invalidate();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        registered = false;
    }

    private static Collection<String> getPlayerNameSuggestions() {
        Set<String> names = new LinkedHashSet<>(List.of("Steve", "Alex"));
        for (ServerBot bot : BotList.INSTANCE.bots) {
            names.add(bot.getBukkitEntity().getName());
        }
        return names;
    }

    private static String[] getCollaboratorNames(CommandContext<CommandSourceStack> ctx) {
        ServerBot bot = getBot(ctx);
        if (bot == null) return new String[0];
        Set<String> names = new LinkedHashSet<>();
        for (UUID uuid : bot.collaborators) {
            org.bukkit.entity.Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                names.add(p.getName());
            }
        }
        return names.toArray(new String[0]);
    }

    @Nullable
    private static ServerBot getBot(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");
        ServerBot bot = BotList.INSTANCE.getBotByName(playerName.toLowerCase(Locale.ROOT));
        if (bot != null) return bot;
        org.bukkit.entity.Player p = Bukkit.getPlayerExact(playerName);
        if (p instanceof org.leavesmc.leaves.entity.bot.CraftBot craftBot) {
            return craftBot.getHandle();
        }
        return null;
    }

    private static boolean hasManagePermission(ServerBot bot, org.bukkit.command.CommandSender sender) {
        if (sender.isOp()) return true;
        if (sender instanceof org.bukkit.entity.Player player) {
            return bot.hasManagePermission(player.getUniqueId());
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int setBotConfig(CommandContext<CommandSourceStack> ctx) {
        if (cantManipulate(ctx)) return 0;
        if (!FakeplayerConfig.canModifyConfig) {
            ctx.getSource().getSender().sendMessage(Component.text("修改配置功能已被禁用", NamedTextColor.RED));
            return 0;
        }
        ServerBot bot = getBot(ctx);
        if (bot == null) return 0;

        String configName = StringArgumentType.getString(ctx, "configName");
        String valueStr = StringArgumentType.getString(ctx, "value");

        AbstractBotConfig<?, ?> config = Configs.getConfig(configName);
        if (config == null) {
            ctx.getSource().getSender().sendMessage(Component.text("未知配置: " + configName, NamedTextColor.RED));
            return 0;
        }

        try {
            if (config == Configs.SIMULATION_DISTANCE) {
                int val = Integer.parseInt(valueStr);
                if (val < -1 || val > 32) {
                    ctx.getSource().getSender().sendMessage(Component.text("模拟距离范围: -1 ~ 32", NamedTextColor.RED));
                    return 0;
                }
                ((AbstractBotConfig<Integer, ?>) bot.getConfig(config)).setValue(val);
            } else if (config == Configs.TICK_TYPE) {
                ServerBot.TickType type = ServerBot.TickType.valueOf(valueStr.toUpperCase());
                ((AbstractBotConfig<ServerBot.TickType, ?>) bot.getConfig(config)).setValue(type);
            } else {
                if (!valueStr.equalsIgnoreCase("true") && !valueStr.equalsIgnoreCase("false")) {
                    ctx.getSource().getSender().sendMessage(Component.text("布尔值只能为 true 或 false", NamedTextColor.RED));
                    return 0;
                }
                boolean val = Boolean.parseBoolean(valueStr);
                ((AbstractBotConfig<Boolean, ?>) bot.getConfig(config)).setValue(val);
            }
            BotList.INSTANCE.saveBotConfigs(bot);

            ctx.getSource().getSender().sendMessage(
                    Component.text("已设置 " + configName + " 为 " + valueStr, NamedTextColor.GREEN)
            );
        } catch (Exception e) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("设置失败: " + e.getMessage(), NamedTextColor.RED)
            );
        }
        return 1;
    }

    private static boolean cantManipulate(CommandContext<CommandSourceStack> context) {
        ServerBot bot = getBot(context);
        if (bot == null) {
            context.getSource().getSender().sendMessage(Component.text("找不到该假人", NamedTextColor.RED));
            return true;
        }
        if (!hasManagePermission(bot, context.getSource().getSender())) {
            context.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
            return true;
        }
        return false;
    }

    private static boolean cantSpawn(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");

        if (Bukkit.getPlayerExact(playerName) != null) {
            context.getSource().getSender().sendMessage(Component.text("该玩家已在线", NamedTextColor.RED));
            return true;
        }
        if (!playerName.matches("^[a-zA-Z0-9_]{4,16}$")) {
            context.getSource().getSender().sendMessage(Component.text("名称不合法，假人名称必须为4-16个字符，且只能包含字母、数字和下划线。", NamedTextColor.RED));
            return true;
        }
        if (FakeplayerConfig.unableNames.contains(playerName)) {
            context.getSource().getSender().sendMessage(Component.text("此名称不允许在该服务器中使用", NamedTextColor.RED));
            return true;
        }
        if (BotList.INSTANCE.bots.size() >= FakeplayerConfig.limit) {
            context.getSource().getSender().sendMessage(Component.text("假人数量已达上限", NamedTextColor.RED));
            return true;
        }
        String fullName = BotUtil.getFullName(playerName);
        if (BotList.INSTANCE.getBotByName(fullName) != null) {
            context.getSource().getSender().sendMessage(Component.text("该假人已存在", NamedTextColor.RED));
            return true;
        }
        if (context.getSource().getSender() instanceof org.bukkit.entity.Player sp && !sp.isOp()) {
            int lim = FakeplayerConfig.perPlayerLimit;
            if (lim >= 0 && BotOwnerRegistry.INSTANCE.countByOwner(sp.getUniqueId()) >= lim) {
                sp.sendMessage(Component.text("你的假人数量已达上限 (" + lim + ")", NamedTextColor.RED));
                return true;
            }
        }
        return false;
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (cantSpawn(context)) return 0;

        String playerName = StringArgumentType.getString(context, "player");

        CommandContext<net.minecraft.commands.CommandSourceStack> mcCtx = (CommandContext<net.minecraft.commands.CommandSourceStack>) (CommandContext<?>) context;
        net.minecraft.commands.CommandSourceStack mcSource = mcCtx.getSource();
        PaperCommandSourceStack paperSource = (PaperCommandSourceStack) context.getSource();

        Vec3 pos;
        try {
            pos = Vec3Argument.getVec3(mcCtx, "position");
        } catch (IllegalArgumentException e) {
            if (mcSource.getEntity() != null) {
                pos = mcSource.getPosition();
            } else {
                org.bukkit.Location spawnLoc = mcSource.getLevel().getWorld().getSpawnLocation();
                pos = new Vec3(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
            }
        }

        Vec2 facing;
        try {
            facing = RotationArgument.getRotation(mcCtx, "direction").getRotation(mcSource);
        } catch (IllegalArgumentException e) {
            facing = mcSource.getRotation();
        }

        ResourceKey<Level> dimType;
        try {
            dimType = DimensionArgument.getDimension(mcCtx, "dimension").dimension();
        } catch (IllegalArgumentException e) {
            dimType = mcSource.getLevel().dimension();
        }

        GameType mode = GameType.CREATIVE;
        boolean flying = false;
        if (mcSource.getEntity() instanceof ServerPlayer sender) {
            mode = sender.gameMode.getGameModeForPlayer();
            flying = sender.getAbilities().flying;
        }
        try {
            mode = GameModeArgument.getGameMode(mcCtx, "gamemode");
        } catch (IllegalArgumentException ignored) {
        }

        if (mode == GameType.SPECTATOR) {
            flying = true;
        } else if (mode.isSurvival()) {
            flying = false;
        }

        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) {
            paperSource.getSender().sendMessage(Component.text("不能在世界范围外放置玩家", NamedTextColor.RED));
            return 0;
        }

        org.bukkit.World bukkitWorld = mcSource.getServer().getLevel(dimType).getWorld();
        Location location = new Location(bukkitWorld, pos.x, pos.y, pos.z, facing.y, facing.x);

        BotCreateState
                .builder(playerName, location)
                .createReason(BotCreateEvent.CreateReason.COMMAND)
                .skinName(playerName)
                .creator(paperSource.getSender())
                .spawnWithSkin(bot -> {
                    if (bot != null) {
                        ServerBot serverBot = ((org.leavesmc.leaves.entity.bot.CraftBot) bot).getHandle();
                        BotList.INSTANCE.loadBotConfigs(serverBot);
                        BotList.INSTANCE.loadBotInventoryAndEquipment(serverBot);
                    }
                });

        return 1;
    }

    private static int spawnDelayed(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");

        org.bukkit.entity.Player senderPlayer = context.getSource().getSender() instanceof org.bukkit.entity.Player p ? p : null;
        if (senderPlayer == null) {
            context.getSource().getSender().sendMessage(Component.text("只有玩家才能使用此命令", NamedTextColor.RED));
            return 0;
        }

        if (cantSpawn(context)) return 0;

        org.bukkit.Location loc = senderPlayer.getLocation();

        context.getSource().getSender().sendMessage(
                Component.text("将在 " + seconds + " 秒后在当前位置召唤 " + playerName, NamedTextColor.GREEN)
        );

        Bukkit.getGlobalRegionScheduler().runDelayed(MinecraftInternalPlugin.INSTANCE, task -> {
            if (Bukkit.getPlayerExact(playerName) != null) {
                context.getSource().getSender().sendMessage(
                        Component.text("召唤失败: 该玩家已在线或假人已存在", NamedTextColor.RED));
                return;
            }
            if (BotList.INSTANCE.getBotByName(BotUtil.getFullName(playerName)) != null) {
                context.getSource().getSender().sendMessage(
                        Component.text("召唤失败: 该假人已存在", NamedTextColor.RED));
                return;
            }
            if (BotList.INSTANCE.bots.size() >= FakeplayerConfig.limit) {
                context.getSource().getSender().sendMessage(
                        Component.text("召唤失败: 假人数量已达上限", NamedTextColor.RED));
                return;
            }

            BotCreateState
                    .builder(playerName, loc)
                    .createReason(BotCreateEvent.CreateReason.COMMAND)
                    .skinName(playerName)
                    .creator(context.getSource().getSender())
                    .spawnWithSkin(bot -> {
                        if (bot != null) {
                            ServerBot serverBot = ((org.leavesmc.leaves.entity.bot.CraftBot) bot).getHandle();
                            BotList.INSTANCE.loadBotConfigs(serverBot);
                            BotList.INSTANCE.loadBotInventoryAndEquipment(serverBot);
                        }
                    });
        }, seconds * 20L);

        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx, int page) {
        Component[][] pages = {
            { Component.text("=== /bot 假人帮助 (1/3) ===", NamedTextColor.GOLD),
                Component.text("\n", NamedTextColor.WHITE),
                Component.text("\n§6§l基本操作", NamedTextColor.GOLD),
                Component.text("\n/bot — 打开假人管理主界面"),
                Component.text("\n/bot help [页码] — 翻页帮助,共3页"),
                Component.text("\n/bot <名字> spawn — 在身边召唤假人"),
                Component.text("\n/bot <名字> menu — 打开控制面板"),
                Component.text("\n/bot <名字> echest — 打开末影箱"),
                Component.text("\n/bot <名字> tp — 传送到身边"),
                Component.text("\n/bot <名字> kill — 杀死假人"),
                Component.text("\n", NamedTextColor.WHITE),
                Component.text("\n§6§l动作", NamedTextColor.GOLD),
                Component.text("\nsneak/unsneak sprint/unsprint"),
                Component.text("\nattack/use/break [continuous]"),
                Component.text("\njump drop swapHands mount/dismount"),
                Component.text("\nstop — 停止所有动作"),
                Component.text("\nactionstop <动作> — 停止指定动作"),
            },
            { Component.text("=== /bot 假人帮助 (2/3) ===", NamedTextColor.GOLD),
                Component.text("\n", NamedTextColor.WHITE),
                Component.text("\n§6§l视角与移动", NamedTextColor.GOLD),
                Component.text("\nlook north/south/east/west/up/down"),
                Component.text("\nlook at <坐标> — 看向坐标"),
                Component.text("\nturn left/right/back — 转向"),
                Component.text("\nmove forward/backward/left/right"),
                Component.text("\n", NamedTextColor.WHITE),
                Component.text("\n§6§l经验（只存点数，等级实时推算）", NamedTextColor.GOLD),
                Component.text("\nxp take <点数> — 取经验点数"),
                Component.text("\nxp level <等级> — 取经验等级"),
                Component.text("\nxp give <点数> — 给假人经验点"),
                Component.text("\nxp level give <等级> — 给假人经验等级"),
                Component.text("\n", NamedTextColor.WHITE),
                Component.text("\n§6§l设置", NamedTextColor.GOLD),
                Component.text("\nconfig <项> <值>"),
                Component.text("\n skip_sleep always_send_data"),
                Component.text("\n spawn_phantom keep_inventory"),
                Component.text("\n simulation_distance tick_type"),
            },
            { Component.text("=== /bot 假人帮助 (3/3) ===", NamedTextColor.GOLD),
                Component.text("\n", NamedTextColor.WHITE),
                Component.text("\n§6§l协作者与权限", NamedTextColor.GOLD),
                Component.text("\ncol add <玩家> — 添加协作者"),
                Component.text("\ncol remove <玩家> — 移除协作者"),
                Component.text("\ncol list — 查看协作者"),
                Component.text("\nGUI支持15项精细权限"),
                Component.text("\n", NamedTextColor.WHITE),
                Component.text("\n§6§l快捷操作", NamedTextColor.GOLD),
                Component.text("\n蹲下+空手右键 → 控制面板"),
                Component.text("\n「+创建假人」→ 聊天输名字"),
                Component.text("\n", NamedTextColor.WHITE),
                Component.text("\n§7自动加BOT_前缀: test→BOT_test"),
                Component.text("\n§7每人上限5个,OP无限制"),
                Component.text("\n§7/bot help 1/2/3 翻页", NamedTextColor.GRAY),
            },
        };
        int idx = Math.max(0, Math.min(page - 1, pages.length - 1));
        ctx.getSource().getSender().sendMessage(join(spaces(), pages[idx]));
        return 1;
    }

    private static int kill(CommandContext<CommandSourceStack> context) {
        ServerBot bot = getBot(context);
        if (bot == null) {
            context.getSource().getSender().sendMessage(Component.text("找不到该假人", NamedTextColor.RED));
            return 0;
        }
        if (!hasManagePermission(bot, context.getSource().getSender())) {
            context.getSource().getSender().sendMessage(Component.text("你没有权限管理该假人", NamedTextColor.RED));
            return 0;
        }
        BotList.INSTANCE.removeBot(bot, BotRemoveEvent.RemoveReason.COMMAND, context.getSource().getSender(), false, false);
        return 1;
    }

    private static int teleportHere(CommandContext<CommandSourceStack> context) {
        ServerBot bot = getBot(context);
        if (bot == null) return 0;
        if (!hasManagePermission(bot, context.getSource().getSender())) { context.getSource().getSender().sendMessage(Component.text("没有权限", NamedTextColor.RED)); return 0; }
        if (!(context.getSource().getSender() instanceof org.bukkit.entity.Player sp)) { context.getSource().getSender().sendMessage(Component.text("只有玩家可用", NamedTextColor.RED)); return 0; }
        bot.getBukkitEntity().teleportAsync(sp.getLocation()).thenAccept(ok -> sp.sendMessage(ok ? Component.text("已传送到你身边", NamedTextColor.GREEN) : Component.text("传送失败", NamedTextColor.RED)));
        return 1;
    }

    private static int hotbar(CommandContext<CommandSourceStack> context) {
        if (cantManipulate(context)) return 0;
        ServerBot bot = getBot(context);
        if (bot == null) return 0;
        int slot = IntegerArgumentType.getInteger(context, "slot");
        int hotbarSlot = slot - 1;
        if (hotbarSlot >= 0 && hotbarSlot < 9) {
            bot.getInventory().setSelectedSlot(hotbarSlot);
            context.getSource().getSender().sendMessage(join(spaces(),
                    Component.text("已将", NamedTextColor.GRAY),
                    PaperAdventure.asAdventure(bot.getDisplayName()),
                    Component.text("的快捷栏切换至", NamedTextColor.GRAY),
                    Component.text("第" + slot + "格", NamedTextColor.AQUA)
            ));
        }
        return 1;
    }

    private static Command<CommandSourceStack> manipulation(Consumer<ServerBot> action) {
        return c -> {
            if (cantManipulate(c)) return 0;
            ServerBot bot = getBot(c);
            if (bot == null) return 0;
            action.accept(bot);
            return 1;
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makeActionLiteral(String commandName, String actionName) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(commandName)
                .executes(manipulation(bot -> startAction(bot, actionName, ActionMode.ONCE)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("once")
                        .executes(manipulation(bot -> startAction(bot, actionName, ActionMode.ONCE)))
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("continuous")
                        .executes(manipulation(bot -> startAction(bot, actionName, ActionMode.CONTINUOUS)))
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("interval")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    ServerBot bot = getBot(c);
                                    if (bot == null) return 0;
                                    int ticks = IntegerArgumentType.getInteger(c, "ticks");
                                    startAction(bot, actionName, ActionMode.interval(ticks));
                                    return 1;
                                })
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makeDropLiteral(String commandName) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(commandName)
                .executes(manipulation(bot -> startAction(bot, "drop", ActionMode.ONCE)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("once")
                        .executes(manipulation(bot -> startAction(bot, "drop", ActionMode.ONCE)))
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("continuous")
                        .executes(manipulation(bot -> startAction(bot, "drop", ActionMode.CONTINUOUS)))
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("interval")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    ServerBot bot = getBot(c);
                                    if (bot == null) return 0;
                                    int ticks = IntegerArgumentType.getInteger(c, "ticks");
                                    startAction(bot, "drop", ActionMode.interval(ticks));
                                    return 1;
                                })
                        )
                )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("all")
                                .executes(manipulation(bot -> bot.dropAll(false)))
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("mainhand")
                        .executes(manipulation(bot -> {
                            var item = bot.getMainHandItem().copy();
                            if (!item.isEmpty()) {
                                bot.drop(item, false, false);
                                bot.getMainHandItem().setCount(0);
                            }
                        }))
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("offhand")
                        .executes(manipulation(bot -> {
                            var item = bot.getOffhandItem().copy();
                            if (!item.isEmpty()) {
                                bot.drop(item, false, false);
                                bot.getOffhandItem().setCount(0);
                            }
                        }))
                )
                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("slot", IntegerArgumentType.integer(0, 40))
                        .executes(c -> {
                            ServerBot bot = getBot(c);
                            if (bot == null) return 0;
                            int slot = IntegerArgumentType.getInteger(c, "slot");
                            var item = bot.getInventory().getItem(slot).copy();
                            if (!item.isEmpty()) {
                                bot.drop(item, false, false);
                                bot.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                            }
                            return 1;
                        })
                );
    }

    private static void startAction(ServerBot bot, String actionName, ActionMode mode) {
        AbstractBotAction<?> template = Actions.getForName(actionName);
        if (template == null) return;

        AbstractBotAction<?> action = template.create();
        action.setDoIntervalTick(mode.ticks);
        action.setDoNumber(mode.count);
        action.setStartDelayTick(0);
        bot.addBotAction(action, Bukkit.getConsoleSender());
    }

    private static void startMoveAction(ServerBot bot, MoveDirection direction) {
        ServerMoveAction action = new ServerMoveAction();
        action.setDirection(direction);
        action.setDoIntervalTick(1);
        action.setDoNumber(-1);
        action.setStartDelayTick(0);
        bot.addBotAction(action, Bukkit.getConsoleSender());
    }

    private static void stopAllActions(ServerBot bot) {
        List<AbstractBotAction<?>> actions = bot.getBotActions();
        for (int i = actions.size() - 1; i >= 0; i--) {
            actions.get(i).stop(bot, BotActionStopEvent.Reason.COMMAND);
        }
    }

    private static void stopAction(ServerBot bot, String actionName) {
        List<AbstractBotAction<?>> actions = bot.getBotActions();
        for (int i = actions.size() - 1; i >= 0; i--) {
            AbstractBotAction<?> action = actions.get(i);
            if (action.getName().equals(actionName)) {
                action.stop(bot, BotActionStopEvent.Reason.COMMAND);
            }
        }
    }

    // ============================================================
    // XP SYSTEM — bot stores ONLY totalExperience (points).
    // Level and progress are DERIVED, never stored independently.
    // Formulas match vanilla Minecraft exactly.
    // ============================================================

    /** XP needed to advance from level L to L+1 (vanilla Minecraft) */
    private static int xpForLevel(int level) {
        if (level < 16) return 2 * level + 7;
        if (level < 31) return 5 * level - 38;
        return 9 * level - 158;
    }

    /** Total XP required to reach exactly `level` (i.e. XP from level 0 to `level`).
     *  Exact integer arithmetic from Minecraft Wiki — no floating point errors. */
    private static int totalXpForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        // 2.5*L² - 40.5*L + 360  =  (5L² - 81L + 720) / 2
        if (level <= 31) return (5 * level * level - 81 * level + 720) / 2;
        // 4.5*L² - 162.5*L + 2220  =  (9L² - 325L + 4440) / 2
        return (9 * level * level - 325 * level + 4440) / 2;
    }

    /** Recalculate experienceLevel + experienceProgress from totalExperience */
    private static void recalcBotLevel(ServerBot bot) {
        int xp = Math.max(0, bot.totalExperience);
        int level = 0;
        while (true) {
            int needed = xpForLevel(level);
            if (xp >= needed) { xp -= needed; level++; } else break;
        }
        bot.experienceLevel = level;
        int next = xpForLevel(level);
        bot.experienceProgress = next > 0 ? (float) xp / next : 0f;
    }

    /** Take XP points from bot, recalc level afterwards */
    private static int takeXp(ServerBot bot, org.bukkit.entity.Player player, int amount, boolean asOrbs, CommandContext<CommandSourceStack> ctx) {
        int available = bot.totalExperience;
        if (available <= 0) {
            ctx.getSource().getSender().sendMessage(Component.text("该假人没有经验", NamedTextColor.RED));
            return 0;
        }
        int taken = Math.min(amount, available);
        if (taken <= 0) return 0;
        bot.totalExperience -= taken;
        recalcBotLevel(bot);
        if (asOrbs) bot.spawnExperienceAsOrbs();
        else player.giveExp(taken);
        ctx.getSource().getSender().sendMessage(join(spaces(),
            Component.text("已从", NamedTextColor.GRAY),
            Component.text(bot.getBukkitEntity().getName(), NamedTextColor.AQUA),
            Component.text("获取了", NamedTextColor.GRAY),
            Component.text(taken + " 点经验", NamedTextColor.AQUA)
        ));
        return 1;
    }

    /** Take N complete levels from bot (calculate XP value correctly via vanilla formula).
     *  If bot doesn't have enough XP for those levels, fail. */
    private static int takeXpLevel(ServerBot bot, org.bukkit.entity.Player player, int wantedLevels, CommandContext<CommandSourceStack> ctx) {
        int currentLevel = bot.experienceLevel;
        if (currentLevel <= 0) {
            ctx.getSource().getSender().sendMessage(Component.text("该假人没有等级", NamedTextColor.RED));
            return 0;
        }
        // How many levels can we actually take?
        int maxTake = Math.min(wantedLevels, currentLevel);
        // XP value of those levels = totalXpForLevel(currentLevel) - totalXpForLevel(currentLevel - maxTake)
        int xpCost = totalXpForLevel(currentLevel) - totalXpForLevel(currentLevel - maxTake);
        if (xpCost > bot.totalExperience) {
            // safety: recalc and retry with actual available levels
            recalcBotLevel(bot);
            currentLevel = bot.experienceLevel;
            maxTake = Math.min(wantedLevels, currentLevel);
            xpCost = totalXpForLevel(currentLevel) - totalXpForLevel(currentLevel - maxTake);
            if (xpCost > bot.totalExperience || maxTake <= 0) {
                ctx.getSource().getSender().sendMessage(Component.text("该假人经验不足", NamedTextColor.RED));
                return 0;
            }
        }
        bot.totalExperience -= xpCost;
        recalcBotLevel(bot);
        player.giveExp(xpCost); // Use raw XP points — same value bot lost
        ctx.getSource().getSender().sendMessage(join(spaces(),
            Component.text("已从", NamedTextColor.GRAY),
            Component.text(bot.getBukkitEntity().getName(), NamedTextColor.AQUA),
            Component.text("获取了", NamedTextColor.GRAY),
            Component.text(maxTake + " 级经验 (" + xpCost + " 点)", NamedTextColor.AQUA)
        ));
        return 1;
    }

    /** Give XP to bot, recalc level afterwards */
    private static int giveXpToBot(ServerBot bot, org.bukkit.entity.Player player, int amount, boolean asOrbs, CommandContext<CommandSourceStack> ctx) {
        if (asOrbs) {
            ctx.getSource().getSender().sendMessage(Component.text("经验球形式仅支持从假人获取经验", NamedTextColor.RED));
            return 0;
        }
        int playerXp = player.getTotalExperience();
        if (playerXp <= 0) {
            ctx.getSource().getSender().sendMessage(Component.text("你没有经验", NamedTextColor.RED));
            return 0;
        }
        int given = Math.min(amount, playerXp);
        player.giveExp(-given);
        bot.totalExperience += given;
        recalcBotLevel(bot);
        ctx.getSource().getSender().sendMessage(join(spaces(),
            Component.text("已给予", NamedTextColor.GRAY),
            Component.text(bot.getBukkitEntity().getName(), NamedTextColor.AQUA),
            Component.text(given + " 点经验", NamedTextColor.AQUA)
        ));
        return 1;
    }

    /** Give N full levels from player to bot — converts to XP points first */
    private static int giveXpLevelToBot(ServerBot bot, org.bukkit.entity.Player player, int wantedLevels, CommandContext<CommandSourceStack> ctx) {
        int playerLevel = player.getLevel();
        if (playerLevel <= 0 || wantedLevels <= 0) {
            ctx.getSource().getSender().sendMessage(Component.text("你没有等级或输入无效", NamedTextColor.RED));
            return 0;
        }
        int actualGive = Math.min(wantedLevels, playerLevel);
        int targetLevel = playerLevel - actualGive;
        // Folia: player.getTotalExperience() is often wrong — use Wiki formula exclusively
        int currentTotal = totalXpForLevel(playerLevel);
        int targetTotal = totalXpForLevel(targetLevel);
        // Add progress between levels (exp bar 0.0~1.0, more likely correct than getTotalExperience)
        float progress = player.getExp();
        if (progress > 0 && playerLevel < 21863) {
            currentTotal += Math.round(progress * xpForLevel(playerLevel));
        }
        int xpToRemove = currentTotal - targetTotal;
        if (xpToRemove <= 0) {
            ctx.getSource().getSender().sendMessage(Component.text("经验不足", NamedTextColor.RED));
            return 0;
        }
        // Transfer points
        player.giveExp(-xpToRemove);
        final int finalXp = xpToRemove;
        bot.getBukkitEntity().getScheduler().run(MinecraftInternalPlugin.INSTANCE, task -> {
            bot.totalExperience += finalXp;
            recalcBotLevel(bot);
        }, null);
        ctx.getSource().getSender().sendMessage(join(spaces(),
            Component.text("已给予", NamedTextColor.GRAY),
            Component.text(bot.getBukkitEntity().getName(), NamedTextColor.AQUA),
            Component.text(actualGive + " 级经验 (" + xpToRemove + " 点)", NamedTextColor.AQUA)
        ));
        return 1;
    }

    private static int takeXpAsOrbs(ServerBot bot, org.bukkit.entity.Player player, int amount, CommandContext<CommandSourceStack> ctx) {
        int available = bot.totalExperience;
        if (available <= 0) {
            ctx.getSource().getSender().sendMessage(Component.text("该假人没有经验", NamedTextColor.RED));
            return 0;
        }
        int taken = Math.min(amount, available);
        bot.totalExperience -= taken;
        if (taken > 0) {
            Location loc = bot.getBukkitEntity().getLocation();
            loc.getWorld().spawn(loc, org.bukkit.entity.ExperienceOrb.class).setExperience(taken);
        }
        ctx.getSource().getSender().sendMessage(join(spaces(),
                Component.text("已从", NamedTextColor.GRAY),
                Component.text(bot.getBukkitEntity().getName(), NamedTextColor.AQUA),
                Component.text("以经验球形式获取了", NamedTextColor.GRAY),
                Component.text(taken + " 点经验", NamedTextColor.AQUA)
        ));
        return 1;
    }

    private record ActionMode(int count, int ticks) {
        static final ActionMode ONCE = new ActionMode(1, 1);
        static final ActionMode CONTINUOUS = new ActionMode(-1, 1);

        static ActionMode interval(int ticks) {
            return new ActionMode(-1, ticks);
        }
    }
}
