package org.leavesmc.leaves.bot.gui;

import fun.bm.lophine.config.modules.function.FakeplayerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.BotOwnerRegistry;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.bot.agent.Configs;
import org.leavesmc.leaves.bot.agent.actions.AbstractBotAction;
import org.leavesmc.leaves.bot.agent.configs.AbstractBotConfig;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BotGui implements Listener {
    private static final NamespacedKey AKEY = new NamespacedKey("lophine", "gui_action");
    private static final Map<UUID, Long> CD = new HashMap<>();
    private static final Set<UUID> AWAIT_NAME = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> AWAIT_GIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> AWAIT_XP = new ConcurrentHashMap<>();
    private static final Map<UUID, String> AWAIT_SEARCH = new ConcurrentHashMap<>();
    private static boolean reg;
    // XP mode: "level" or "points". Per-player, stored as string value.
    private static final Map<UUID, String> XP_MODE = new ConcurrentHashMap<>();

    private static Component t(String s, NamedTextColor c) { return Component.text(s,c).decoration(TextDecoration.ITALIC,false); }
    private static ItemStack item(Material m, String act, Component name, List<Component> lore) {
        ItemStack s = new ItemStack(m); ItemMeta me = s.getItemMeta();
        if(me!=null){me.displayName(name);if(lore!=null)me.lore(lore);me.getPersistentDataContainer().set(AKEY,PersistentDataType.STRING,act);s.setItemMeta(me);}return s;
    }
    private static ItemStack simple(Material m, String act, String nm, NamedTextColor c, String... lo) {
        List<Component> l = new ArrayList<>(); for(String ln:lo)l.add(t(ln,NamedTextColor.GRAY));
        return item(m,act,t(nm,c),l);
    }
    private static ItemStack border() { return simple(Material.GRAY_STAINED_GLASS_PANE,"noop"," ",NamedTextColor.GRAY); }
    private static String actOf(ItemStack s){if(s==null||!s.hasItemMeta())return null;return s.getItemMeta().getPersistentDataContainer().get(AKEY,PersistentDataType.STRING);}
    private static String ownName(ServerBot bot){if(bot.createPlayer==null)return "未知";BotOwnerRegistry.Entry e=BotOwnerRegistry.INSTANCE.get(bot.getScoreboardName());if(e!=null&&!e.ownerName.isEmpty())return e.ownerName;String n=Bukkit.getOfflinePlayer(bot.createPlayer).getName();return n!=null?n:"未知";}
    private static boolean canM(ServerBot bot, Player p){return p.isOp()||bot.hasManagePermission(p.getUniqueId());}
    public static synchronized void ensureR(){if(!reg){Bukkit.getPluginManager().registerEvents(new BotGui(),MinecraftInternalPlugin.INSTANCE);reg=true;}}

    // --- Pagination helpers ---
    private static void navRow(Inventory inv, int page, int total, String backAct) {
        inv.setItem(18, simple(Material.ARROW, backAct, "返回", NamedTextColor.WHITE));
        inv.setItem(19, border()); inv.setItem(20, border());
        inv.setItem(21, page>0 ? simple(Material.ARROW,"page:"+(page-1),"上一页",NamedTextColor.WHITE) : border());
        inv.setItem(22, simple(Material.PAPER,"noop","第"+(page+1)+"/"+total+"页",NamedTextColor.WHITE));
        inv.setItem(23, page+1<total ? simple(Material.ARROW,"page:"+(page+1),"下一页",NamedTextColor.WHITE) : border());
        inv.setItem(24, border()); inv.setItem(25, border());
        inv.setItem(26, simple(Material.OAK_DOOR,"close","关闭",NamedTextColor.RED));
    }

    /* ========== MAIN: 假人列表 (27 slot) ========== */
    public static void openMain(@NotNull Player p) { openMain(p,0); }
    public static void openMain(@NotNull Player p, int page) {
        if(!FakeplayerConfig.guiEnabled){p.sendMessage(t("GUI已禁用",NamedTextColor.RED));return;}ensureR();
        UUID uid=p.getUniqueId(); boolean op=p.isOp();
        List<ServerBot> on=new ArrayList<>();
        for(ServerBot b:BotList.INSTANCE.bots)if((b.createPlayer!=null&&b.createPlayer.equals(uid))||op)on.add(b);
        List<BotOwnerRegistry.Entry> off=new ArrayList<>();
        for(BotOwnerRegistry.Entry e:op?BotOwnerRegistry.INSTANCE.all():BotOwnerRegistry.INSTANCE.listByOwner(uid))
            if(BotList.INSTANCE.getBotByName(e.fullName.toLowerCase(Locale.ROOT))==null)off.add(e);
        List<Object> all=new ArrayList<>();all.addAll(on);all.addAll(off);
        int perPage=7, total=(int)Math.ceil((double)all.size()/perPage);if(total==0)total=1;
        if(page>=total)page=total-1;
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.MAIN,null),27,t("假人列表",NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border()); // row 1 bg
        int lim=FakeplayerConfig.perPlayerLimit; if(lim<0)lim=99;
        int cnt=on.size()+(int)off.stream().filter(e->e.isOwner(uid)).count();
        ItemStack hd=item(Material.PLAYER_HEAD,"noop",t(p.getName(),NamedTextColor.GREEN),
            List.of(t("假人数:"+cnt+"/"+(op?"∞":String.valueOf(lim)),NamedTextColor.GRAY)));
        if(hd.getItemMeta() instanceof SkullMeta sm){sm.setOwningPlayer(p);hd.setItemMeta(sm);}inv.setItem(4,hd);
        // content
        int start=page*perPage, end=Math.min(start+perPage,all.size()), slot=10;
        for(int i=start;i<end;i++){
            Object o=all.get(i);
            if(o instanceof ServerBot b){
                var loc=b.getBukkitEntity().getLocation();
                inv.setItem(slot++,item(Material.PLAYER_HEAD,"panel:"+b.getScoreboardName(),t("在线:"+b.getBukkitEntity().getName(),NamedTextColor.GREEN),
                    List.of(t("主人:"+ownName(b),NamedTextColor.GRAY),t(String.format("%s %d,%d,%d",loc.getWorld().getName(),loc.getBlockX(),loc.getBlockY(),loc.getBlockZ()),NamedTextColor.GRAY))));
            }else{
                BotOwnerRegistry.Entry e=(BotOwnerRegistry.Entry)o;
                inv.setItem(slot++,item(Material.SKELETON_SKULL,"spawn:"+e.rawName,t("离线:"+e.fullName,NamedTextColor.RED),
                    List.of(t("主人:"+e.ownerName,NamedTextColor.GRAY),t("点击召唤",NamedTextColor.YELLOW))));
            }
        }
        while(slot<=16)inv.setItem(slot++,new ItemStack(Material.AIR));
        inv.setItem(9,border()); inv.setItem(17,border());
        inv.setItem(18,simple(Material.NAME_TAG,"create","+ 创建假人",NamedTextColor.GREEN,"点击后在聊天栏输入名字"));
        navRow(inv,page,total,"close");
        p.openInventory(inv);
    }

    /* ========== PANEL: 假人主菜单 (27 slot) ========== */
    public static void openPanel(@NotNull Player p, @NotNull ServerBot bot) {
        if(!canM(bot,p)){p.sendMessage(t("没有权限",NamedTextColor.RED));return;}ensureR();
        String fn=bot.getScoreboardName(); var loc=bot.getBukkitEntity().getLocation();
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.PANEL,fn),27,t("假人 · "+fn,NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border());
        inv.setItem(4,item(Material.PLAYER_HEAD,"noop",t(fn,NamedTextColor.GREEN),List.of(
            t("生命:"+String.format("%.0f/%.0f",bot.getHealth(),bot.getMaxHealth()),NamedTextColor.RED),
            t("饱食:"+bot.getFoodData().getFoodLevel()+"/20",NamedTextColor.GOLD),
            t(String.format("(%d,%d,%d)",loc.getBlockX(),loc.getBlockY(),loc.getBlockZ()),NamedTextColor.GRAY),
            t("主人:"+ownName(bot),NamedTextColor.GRAY))));
        for(int i=9;i<27;i++)inv.setItem(i,border());
        inv.setItem(10,simple(Material.CHEST,"inv","背包",NamedTextColor.GREEN));
        inv.setItem(11,simple(Material.ENDER_CHEST,"echest","末影箱",NamedTextColor.LIGHT_PURPLE));
        inv.setItem(12,simple(Material.LEVER,"actions","动作",NamedTextColor.YELLOW));
        inv.setItem(13,simple(Material.CRAFTING_TABLE,"settings","设置",NamedTextColor.AQUA));
        inv.setItem(14,simple(Material.GOLD_INGOT,"perm","权限管理",NamedTextColor.GOLD));
        inv.setItem(15,simple(Material.EXPERIENCE_BOTTLE,"xp","经验",NamedTextColor.GREEN));
        inv.setItem(16,simple(Material.ENDER_PEARL,"tp","传送到我",NamedTextColor.BLUE));
        inv.setItem(17,simple(Material.BARRIER,"rm","删除假人",NamedTextColor.DARK_RED));
        inv.setItem(18,simple(Material.ARROW,"back_main","返回列表",NamedTextColor.WHITE));
        inv.setItem(26,simple(Material.OAK_DOOR,"close","关闭",NamedTextColor.WHITE));
        for(int i=19;i<=25;i++)inv.setItem(i,border());
        p.openInventory(inv);
    }

    /* ========== ACTIONS (54 slot, keep as-is) ========== */
    public static void openActions(@NotNull Player p, @NotNull ServerBot bot) {
        if(!canM(bot,p)){p.sendMessage(t("没有权限",NamedTextColor.RED));return;}ensureR();
        String fn=bot.getScoreboardName();
        boolean sn=bot.isShiftKeyDown(),sp=bot.isSprinting(),at=hasA(bot,"attack"),us=hasA(bot,"use_auto"),br=hasA(bot,"break");
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.ACTIONS,fn),54,t("动作 · "+fn,NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border());for(int i=45;i<54;i++)inv.setItem(i,border());
        inv.setItem(9,simple(Material.LEATHER_BOOTS,"act:sneak","潜行:"+(sn?"开":"关"),sn?NamedTextColor.GREEN:NamedTextColor.RED,"点击切换"));
        inv.setItem(10,simple(Material.SUGAR,"act:sprint","疾跑:"+(sp?"开":"关"),sp?NamedTextColor.GREEN:NamedTextColor.RED,"点击切换"));
        inv.setItem(12,simple(Material.WOODEN_SWORD,"act:attack_once","攻击一次",NamedTextColor.YELLOW));
        inv.setItem(13,simple(Material.FLINT_AND_STEEL,"act:use_once","使用一次",NamedTextColor.YELLOW));
        inv.setItem(14,simple(Material.GOLDEN_PICKAXE,"act:break_once","挖掘一次",NamedTextColor.YELLOW,"破坏面前方块"));
        inv.setItem(15,simple(Material.RABBIT_FOOT,"act:jump","跳跃",NamedTextColor.YELLOW));
        inv.setItem(16,simple(Material.DROPPER,"act:drop","丢弃主手",NamedTextColor.YELLOW));
        inv.setItem(17,simple(Material.STRUCTURE_VOID,"act:swap","交换主副手",NamedTextColor.YELLOW));
        inv.setItem(18,simple(at?Material.DIAMOND_SWORD:Material.IRON_SWORD,"act:attack_cont","连续攻击:"+(at?"运行中":"停止"),at?NamedTextColor.GREEN:NamedTextColor.RED,"点击开/关"));
        inv.setItem(19,simple(us?Material.CLOCK:Material.COMPARATOR,"act:use_cont","连续使用:"+(us?"运行中":"停止"),us?NamedTextColor.GREEN:NamedTextColor.RED,"点击开/关"));
        inv.setItem(20,simple(br?Material.DIAMOND_PICKAXE:Material.IRON_PICKAXE,"act:break_cont","连续挖掘:"+(br?"运行中":"停止"),br?NamedTextColor.GREEN:NamedTextColor.RED,"点击开/关"));
        inv.setItem(22,simple(Material.SADDLE,"act:mount","骑乘",NamedTextColor.YELLOW,"骑上附近载具"));
        inv.setItem(23,simple(Material.LEAD,"act:dismount","下马",NamedTextColor.YELLOW));
        inv.setItem(27,simple(Material.COMPASS,"act:look_n","看北",NamedTextColor.AQUA));
        inv.setItem(28,simple(Material.COMPASS,"act:look_s","看南",NamedTextColor.AQUA));
        inv.setItem(29,simple(Material.COMPASS,"act:look_e","看东",NamedTextColor.AQUA));
        inv.setItem(30,simple(Material.COMPASS,"act:look_w","看西",NamedTextColor.AQUA));
        inv.setItem(31,simple(Material.COMPASS,"act:look_u","看上",NamedTextColor.AQUA));
        inv.setItem(32,simple(Material.COMPASS,"act:look_d","看下",NamedTextColor.AQUA));
        inv.setItem(36,simple(Material.OAK_BUTTON,"act:move_f","前进",NamedTextColor.YELLOW));
        inv.setItem(37,simple(Material.OAK_BUTTON,"act:move_b","后退",NamedTextColor.YELLOW));
        inv.setItem(38,simple(Material.OAK_BUTTON,"act:move_l","左移",NamedTextColor.YELLOW));
        inv.setItem(39,simple(Material.OAK_BUTTON,"act:move_r","右移",NamedTextColor.YELLOW));
        inv.setItem(44,simple(Material.BARRIER,"act:stopall","停止全部动作",NamedTextColor.DARK_RED));
        inv.setItem(45,simple(Material.ARROW,"back_panel","返回面板",NamedTextColor.WHITE));
        inv.setItem(52,simple(Material.COMPASS,"back_main","返回列表",NamedTextColor.WHITE));
        inv.setItem(53,simple(Material.OAK_DOOR,"close","关闭",NamedTextColor.WHITE));
        p.openInventory(inv);
    }
    private static boolean hasA(ServerBot bot,String n){for(AbstractBotAction<?>a:bot.getBotActions())if(a.getName().equals(n))return true;return false;}

    /* ========== SETTINGS (27 slot, paginated) ========== */
    private static final AbstractBotConfig<?,?>[] SC={Configs.SKIP_SLEEP,Configs.ALWAYS_SEND_DATA,Configs.SPAWN_PHANTOM,Configs.KEEP_INVENTORY,Configs.SIMULATION_DISTANCE,Configs.TICK_TYPE,Configs.ENABLE_LOCATOR_BAR};
    private static String cn(String k){return switch(k){case"skip_sleep"->"跳过睡眠";case"always_send_data"->"始终发送数据";case"spawn_phantom"->"生成幻翼";case"keep_inventory"->"死亡不掉落";case"simulation_distance"->"模拟距离";case"tick_type"->"Tick类型";case"enable_locator_bar"->"定位栏";default->k;};}
    public static void openSettings(@NotNull Player p, @NotNull ServerBot bot, int page) {
        if(!canM(bot,p)){p.sendMessage(t("没有权限",NamedTextColor.RED));return;}ensureR();
        String fn=bot.getScoreboardName();
        int perPage=7, total=(int)Math.ceil((double)SC.length/perPage);if(total==0)total=1;
        if(page>=total)page=total-1;
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.SETTINGS,fn),27,t("设置 · "+fn,NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border());
        inv.setItem(4,simple(Material.STICK,"noop","假人设置",NamedTextColor.AQUA,"各项假人行为配置"));
        // Add respawnOnDeath toggle
        inv.setItem(10,simple(FakeplayerConfig.respawnOnDeath?Material.TOTEM_OF_UNDYING:Material.SKELETON_SKULL,"cfg:respawndeath","死亡重生:"+(FakeplayerConfig.respawnOnDeath?"开":"关"),FakeplayerConfig.respawnOnDeath?NamedTextColor.GREEN:NamedTextColor.RED,"点击切换"));
        int start=page*perPage,end=Math.min(start+perPage,SC.length),slot=11;
        for(int i=start;i<end;i++)inv.setItem(slot++,cfgItem(SC[i],bot));
        while(slot<=16)inv.setItem(slot++,new ItemStack(Material.AIR));
        inv.setItem(9,border()); inv.setItem(17,border());
        navRow(inv,page,total,"back_panel");
        p.openInventory(inv);
    }
    private static ItemStack cfgItem(AbstractBotConfig<?,?>c,ServerBot bot){
        String nm=cn(c.getName());Object v=bot.getConfigValue(c);
        if(c==Configs.SIMULATION_DISTANCE)return simple(Material.COMPARATOR,"cfg:"+c.getName(),nm,NamedTextColor.AQUA,"当前:"+v,"点击循环");
        if(c==Configs.TICK_TYPE){boolean net="NETWORK".equals(String.valueOf(v));return simple(net?Material.REDSTONE_TORCH:Material.REDSTONE_BLOCK,"cfg:"+c.getName(),nm,NamedTextColor.AQUA,"当前:"+v,"点击切换");}
        boolean on=v instanceof Boolean b&&b;return simple(on?Material.LIME_STAINED_GLASS_PANE:Material.RED_STAINED_GLASS_PANE,"cfg:"+c.getName(),nm,on?NamedTextColor.GREEN:NamedTextColor.RED,on?"已启用":"已禁用","点击切换");
    }

    /* ========== PERMISSIONS (27 slot, paginated) ========== */
    private static final String[] PFLAGS={"inv","echest","attack","use","break","sneak","sprint","jump","drop","swap","mount","dismount","move","look","tp","set","xp","despawn","remove","spawn"};
    private static final String[] PNAMES={"背包","末影箱","攻击","使用","挖掘","潜行","疾跑","跳跃","丢弃","换手","骑乘","下马","移动","视角","传送","设置","经验","下线保存","删除","召唤"};
    public static void openPermMgmt(@NotNull Player p, @NotNull ServerBot bot, int page) {
        COLLAB_IS_PICKER.remove(p.getUniqueId());
        if(!canM(bot,p)){p.sendMessage(t("没有权限",NamedTextColor.RED));return;}ensureR();
        String fn=bot.getScoreboardName();
        BotOwnerRegistry.Entry e=BotOwnerRegistry.INSTANCE.get(fn);if(e==null)return;
        // Build item list: public perm + collaborators + add button
        List<String> acts=new ArrayList<>();acts.add("p:g:main"); // public permissions page link
        for(UUID u:bot.collaborators)if(!u.equals(ServerBot.PUBLIC_ACCESS_UUID))acts.add("p:c:"+u);
        acts.add("p:add"); // add collaborator button
        int perPage=7,total=(int)Math.ceil((double)acts.size()/perPage);if(total==0)total=1;
        if(page>=total)page=total-1;
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.COLLAB,fn),27,t("权限管理 · "+fn,NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border());
        inv.setItem(4,simple(Material.GOLD_INGOT,"noop","权限管理",NamedTextColor.GOLD,"管理公开及个人权限"));
        int start=page*perPage,end=Math.min(start+perPage,acts.size()),slot=10;
        for(int i=start;i<end;i++){
            String act=acts.get(i);
            if("p:g:main".equals(act))inv.setItem(slot++,simple(Material.BEACON,"p:gopen","公开权限",NamedTextColor.GREEN,"设置所有玩家的默认权限"));
            else if("p:add".equals(act))inv.setItem(slot++,simple(Material.PLAYER_HEAD,"p:addonline","添加协作者",NamedTextColor.GREEN,"从在线玩家中选择"));
            else if(act.startsWith("p:c:")){
                UUID u=UUID.fromString(act.substring(4));String nm=Bukkit.getOfflinePlayer(u).getName();if(nm==null)nm=u.toString().substring(0,8);
                boolean hasP=e.psetFlags.containsKey(u)&&!e.psetFlags.get(u).isEmpty();
                ItemStack hd=item(Material.PLAYER_HEAD,act,t(nm,NamedTextColor.GOLD),List.of(t(hasP?"有个别权限":"默认权限",NamedTextColor.GRAY),t("潜行点击移除",NamedTextColor.RED)));
                if(hd.getItemMeta() instanceof SkullMeta sm){sm.setOwningPlayer(Bukkit.getOfflinePlayer(u));hd.setItemMeta(sm);}inv.setItem(slot++,hd);
            }
        }
        while(slot<=16)inv.setItem(slot++,new ItemStack(Material.AIR));
        inv.setItem(9,border()); inv.setItem(17,border());
        navRow(inv,page,total,"back_panel");
        p.openInventory(inv);
    }

    // Public permission page (all 15 flags)
    public static void openGSetPage(@NotNull Player p, @NotNull ServerBot bot) {
        ensureR(); String fn=bot.getScoreboardName();
        BotOwnerRegistry.Entry e=BotOwnerRegistry.INSTANCE.get(fn);if(e==null)return;
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.COLLAB,fn),54,t("公开权限 · "+fn,NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border());for(int i=45;i<54;i++)inv.setItem(i,border());
        for(int i=0;i<PFLAGS.length;i++){boolean on=e.gsetFlags.contains(PFLAGS[i]);inv.setItem(9+i,simple(on?Material.LIME_STAINED_GLASS_PANE:Material.RED_STAINED_GLASS_PANE,"p:g:"+PFLAGS[i],PNAMES[i],on?NamedTextColor.GREEN:NamedTextColor.RED,on?"所有人可":"仅主人","点击切换"));}
        inv.setItem(45,simple(Material.ARROW,"p:back_perm","返回权限管理",NamedTextColor.WHITE));
        inv.setItem(52,simple(Material.COMPASS,"back_main","返回列表",NamedTextColor.WHITE));
        inv.setItem(53,simple(Material.OAK_DOOR,"close","关闭",NamedTextColor.WHITE));
        p.openInventory(inv);
    }

    // Single collaborator permission page
    public static void openPSetPage(@NotNull Player p, @NotNull ServerBot bot, @NotNull UUID target) {
        ensureR(); String fn=bot.getScoreboardName();
        BotOwnerRegistry.Entry e=BotOwnerRegistry.INSTANCE.get(fn);if(e==null)return;
        String nm=Bukkit.getOfflinePlayer(target).getName();if(nm==null)nm=target.toString().substring(0,8);
        Set<String> ps=e.psetFlags.computeIfAbsent(target,k->ConcurrentHashMap.newKeySet());
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.COLLAB,fn),54,t("权限 · "+nm+" · "+fn,NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border());for(int i=45;i<54;i++)inv.setItem(i,border());
        for(int i=0;i<PFLAGS.length;i++){boolean on=ps.contains(PFLAGS[i]);inv.setItem(9+i,simple(on?Material.LIME_STAINED_GLASS_PANE:Material.RED_STAINED_GLASS_PANE,"p:ps:"+target+":"+PFLAGS[i],PNAMES[i],on?NamedTextColor.GREEN:NamedTextColor.RED,on?"允许":"禁止","点击切换"));}
        inv.setItem(44,simple(Material.BARRIER,"p:rm:"+target,"移除协作者",NamedTextColor.DARK_RED,"从协作者列表中删除此玩家"));
        inv.setItem(45,simple(Material.ARROW,"p:back_perm","返回权限管理",NamedTextColor.WHITE));
        inv.setItem(53,simple(Material.OAK_DOOR,"close","关闭",NamedTextColor.WHITE));
        p.openInventory(inv);
    }

    /* ========== ADD COLLABORATOR (27 slot, paginated, search) ========== */
    public static void openOnlinePicker(@NotNull Player p, @NotNull ServerBot bot, int page, String filter) {
        COLLAB_IS_PICKER.put(p.getUniqueId(), true);
        ensureR(); String fn=bot.getScoreboardName();
        List<Player> players=new ArrayList<>();for(Player op:Bukkit.getOnlinePlayers()){
            if(filter!=null&&!filter.isEmpty()&&!op.getName().toLowerCase().contains(filter.toLowerCase()))continue;players.add(op);
        }
        int perPage=7,total=Math.max(1,(int)Math.ceil((double)players.size()/perPage));
        if(page>=total)page=total-1;
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.COLLAB,fn),27,t("添加协作者 · "+fn,NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border());
        inv.setItem(7,simple(Material.COMPASS,"p:search","搜索",NamedTextColor.AQUA,"点击后输入名字搜索"));
        int start=page*perPage,end=Math.min(start+perPage,players.size()),slot=10;
        for(int i=start;i<end;i++){
            Player op=players.get(i);
            ItemStack hd=item(Material.PLAYER_HEAD,"p:add:"+op.getUniqueId(),t(op.getName(),NamedTextColor.GREEN),List.of(t("点击添加为协作者",NamedTextColor.YELLOW)));
            if(hd.getItemMeta() instanceof SkullMeta sm){sm.setOwningPlayer(op);hd.setItemMeta(sm);}inv.setItem(slot++,hd);
        }
        while(slot<=16)inv.setItem(slot++,new ItemStack(Material.AIR));
        inv.setItem(9,border()); inv.setItem(17,border());
        if(players.isEmpty()&&slot==10)inv.setItem(13,simple(Material.BARRIER,"noop","无匹配玩家",NamedTextColor.RED));
        navRow(inv,page,total,"p:back_perm");
        p.openInventory(inv);
    }

    /* ========== XP (27 slot, 4 pages) ========== */
    // Page 0: take by level, 1: take by points, 2: give by level, 3: give by points
    private static final Map<UUID, Integer> XP_PAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> COLLAB_IS_PICKER = new ConcurrentHashMap<>();

    public static void openXP(@NotNull Player p, @NotNull ServerBot bot) { openXP(p, bot, XP_PAGE.getOrDefault(p.getUniqueId(), 0)); }
    public static void openXP(@NotNull Player p, @NotNull ServerBot bot, int page) {
        ensureR(); String fn=bot.getScoreboardName();
        XP_PAGE.put(p.getUniqueId(), page);
        int total=4, botLv=bot.experienceLevel, botXp=bot.totalExperience;
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.PANEL,fn),27,
            t("经验 · "+fn,NamedTextColor.DARK_GRAY));
        for(int i=0;i<9;i++)inv.setItem(i,border());
        String modeLabel = switch(page){case 0->"§b按级取经验";case 1->"§b按点取经验";case 2->"§d按级给经验";default->"§d按点给经验";};
        inv.setItem(4,simple(Material.EXPERIENCE_BOTTLE,"noop","假人经验",NamedTextColor.GREEN,
            "等级:"+botLv+" 点数:"+botXp,modeLabel));
        for(int i=9;i<27;i++)inv.setItem(i,border());
        // 4 fixed buttons, same positions across all pages
        switch(page){
            case 0: // take by level
                inv.setItem(10,simple(Material.EXPERIENCE_BOTTLE,"xp:1","取1级经验",NamedTextColor.GREEN,"消耗假人1级转为你"));
                inv.setItem(12,simple(Material.EXPERIENCE_BOTTLE,"xp:5","取5级经验",NamedTextColor.GREEN,"消耗假人5级转为你"));
                inv.setItem(14,simple(Material.EXPERIENCE_BOTTLE,"xp:10","取10级经验",NamedTextColor.GREEN,"消耗假人10级转为你"));
                inv.setItem(16,simple(Material.PAPER,"xp:custom","自定义等级",NamedTextColor.YELLOW,"点击后输入数量"));
                break;
            case 1: // take by points
                inv.setItem(10,simple(Material.EXPERIENCE_BOTTLE,"xp:100","取100点经验",NamedTextColor.GREEN));
                inv.setItem(12,simple(Material.EXPERIENCE_BOTTLE,"xp:500","取500点经验",NamedTextColor.GREEN));
                inv.setItem(14,simple(Material.EXPERIENCE_BOTTLE,"xp:1000","取1000点经验",NamedTextColor.GREEN));
                inv.setItem(16,simple(Material.PAPER,"xp:custom","自定义点数",NamedTextColor.YELLOW,"点击后输入数量"));
                break;
            case 2: // give by level
                inv.setItem(10,simple(Material.EXPERIENCE_BOTTLE,"xp:give_lv:1","给假人1级",NamedTextColor.LIGHT_PURPLE,"消耗你1级给假人"));
                inv.setItem(12,simple(Material.EXPERIENCE_BOTTLE,"xp:give_lv:5","给假人5级",NamedTextColor.LIGHT_PURPLE,"消耗你5级给假人"));
                inv.setItem(14,simple(Material.EXPERIENCE_BOTTLE,"xp:give_lv:10","给假人10级",NamedTextColor.LIGHT_PURPLE,"消耗你10级给假人"));
                inv.setItem(16,simple(Material.PAPER,"xp:give_lv:custom","自定义给等级",NamedTextColor.YELLOW,"点击后输入数量"));
                break;
            default: // give by points
                inv.setItem(10,simple(Material.EXPERIENCE_BOTTLE,"xp:give:100","给假人100点",NamedTextColor.LIGHT_PURPLE));
                inv.setItem(12,simple(Material.EXPERIENCE_BOTTLE,"xp:give:500","给假人500点",NamedTextColor.LIGHT_PURPLE));
                inv.setItem(14,simple(Material.EXPERIENCE_BOTTLE,"xp:give:1000","给假人1000点",NamedTextColor.LIGHT_PURPLE));
                inv.setItem(16,simple(Material.PAPER,"xp:give:custom","自定义给点数",NamedTextColor.YELLOW,"点击后输入数量"));
                break;
        }
        navRow(inv,page,total,"back_panel");
        p.openInventory(inv);
    }

    /* ========== CONFIRM REMOVE ========== */
    public static void openConfirmRemove(@NotNull Player p, @NotNull ServerBot bot) {
        if(!canM(bot,p)){p.sendMessage(t("没有权限",NamedTextColor.RED));return;}
        String fn=bot.getScoreboardName();
        Inventory inv=Bukkit.createInventory(new BotGuiHolder(BotGuiHolder.MenuType.CONFIRM_REMOVE,fn),27,t("确认删除 · "+fn,NamedTextColor.DARK_RED));
        for(int i=0;i<27;i++)inv.setItem(i,border());
        inv.setItem(4,simple(Material.PLAYER_HEAD,"noop",fn,NamedTextColor.DARK_RED,"即将彻底删除!","物品掉落,数据不可恢复"));
        inv.setItem(11,simple(Material.RED_CONCRETE,"remove_ok","确认删除",NamedTextColor.DARK_RED));
        inv.setItem(15,simple(Material.LIME_CONCRETE,"back_panel","取消",NamedTextColor.GREEN));
        p.openInventory(inv);
    }

    /* ========== LIVE CONTAINERS ========== */
    public static void openInv(@NotNull Player p,@NotNull ServerBot bot){
        if(!canM(bot,p)){p.sendMessage(t("没有权限",NamedTextColor.RED));return;}
        if(!p.getWorld().equals(bot.getBukkitEntity().getWorld())||p.getLocation().distanceSquared(bot.getBukkitEntity().getLocation())>64){p.sendMessage(t("离假人太远",NamedTextColor.RED));return;}
        ((net.minecraft.server.level.ServerPlayer)((org.bukkit.craftbukkit.entity.CraftPlayer)p).getHandle()).openMenu(new net.minecraft.world.SimpleMenuProvider((i,pi,pl)->net.minecraft.world.inventory.ChestMenu.sixRows(i,pi,bot.getBotContainer()),bot.getDisplayName()));
    }
    public static void openEchest(@NotNull Player p,@NotNull ServerBot bot){
        if(!canM(bot,p)){p.sendMessage(t("没有权限",NamedTextColor.RED));return;}
        if(!p.getWorld().equals(bot.getBukkitEntity().getWorld())||p.getLocation().distanceSquared(bot.getBukkitEntity().getLocation())>64){p.sendMessage(t("离假人太远",NamedTextColor.RED));return;}
        ((net.minecraft.server.level.ServerPlayer)((org.bukkit.craftbukkit.entity.CraftPlayer)p).getHandle()).openMenu(new net.minecraft.world.SimpleMenuProvider((i,pi,pl)->new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x3,i,pi,bot.getEnderChestInventory(),3){@Override public boolean stillValid(net.minecraft.world.entity.player.Player pl2){return !bot.isRemoved()&&pl2.distanceToSqr(bot)<=64;}},bot.getDisplayName()));
    }

    /* ========== CLICK HANDLER ========== */
    @EventHandler public void onClick(InventoryClickEvent e){
        if(!(e.getInventory().getHolder(false) instanceof BotGuiHolder h))return;
        e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;
        long now=System.currentTimeMillis();Long last=CD.get(p.getUniqueId());if(last!=null&&now-last<200)return;CD.put(p.getUniqueId(),now);
        if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        String act=actOf(e.getCurrentItem());if(act==null||"noop".equals(act))return;
        // Global navigation
        if("close".equals(act)){p.closeInventory();return;}
        if("create".equals(act)){p.closeInventory();AWAIT_NAME.add(p.getUniqueId());p.sendMessage(t("在聊天栏输入假人名字(4-16位字母数字下划线)",NamedTextColor.GREEN));return;}
        if("back_main".equals(act)){COLLAB_IS_PICKER.remove(p.getUniqueId());openMain(p);return;}
        // Page navigation
        if(act.startsWith("page:")){int pg=Integer.parseInt(act.substring(5));
            ServerBot b=resolveBot(h);
            switch(h.getType()){
                case MAIN->openMain(p,pg);
                case SETTINGS->{if(b!=null)openSettings(p,b,pg);}
                case COLLAB->{if(b!=null){
                    Boolean isPick=COLLAB_IS_PICKER.get(p.getUniqueId());
                    if(isPick!=null&&isPick)openOnlinePicker(p,b,pg,null);
                    else openPermMgmt(p,b,pg);
                }else openMain(p);}
                case PANEL->{if(b!=null)openXP(p,b,pg);}
                default->{}
            }return;
        }
        // Back to panel
        if("back_panel".equals(act)){COLLAB_IS_PICKER.remove(p.getUniqueId());ServerBot b=resolveBot(h);if(b!=null)openPanel(p,b);else openMain(p);return;}
        // Spawn / panel
        if(act.startsWith("spawn:")){p.closeInventory();p.performCommand("bot "+act.substring(6)+" spawn");return;}
        if(act.startsWith("panel:")){ServerBot b=BotList.INSTANCE.getBotByName(act.substring(6).toLowerCase(Locale.ROOT));if(b!=null)openPanel(p,b);else openMain(p);return;}
        // Bot-specific actions
        String fn=h.getBotFullName();if(fn==null)return;
        ServerBot bot=BotList.INSTANCE.getBotByName(fn.toLowerCase(Locale.ROOT));if(bot==null){openMain(p);return;}
        if(!canM(bot,p)){p.sendMessage(t("没有权限",NamedTextColor.RED));p.closeInventory();return;}
        switch(act){
            case"inv"->{p.closeInventory();openInv(p,bot);}
            case"echest"->{p.closeInventory();openEchest(p,bot);}
            case"actions"->openActions(p,bot);
            case"settings"->openSettings(p,bot,0);
            case"perm"->openPermMgmt(p,bot,0);
            case"xp"->openXP(p,bot);
            case"tp"->{p.closeInventory();p.performCommand("bot "+fn+" tp");}
            case"rm"->openConfirmRemove(p,bot);
            case"remove_ok"->{p.closeInventory();BotList.INSTANCE.removeBotPermanently(bot,p);}
            case"give"->{p.closeInventory();AWAIT_GIVE.add(p.getUniqueId());p.sendMessage(t("聊天栏输入目标玩家名转让",NamedTextColor.GREEN));}
            default->{
                if(act.startsWith("xp:")){handleXp(p,bot,act.substring(3));}
                else if(act.startsWith("p:gopen"))openGSetPage(p,bot);
                else if(act.startsWith("p:g:")){hGSet(p,bot,act.substring(4));openGSetPage(p,bot);}
                else if(act.startsWith("p:pub")){hPub(p,bot);openPermMgmt(p,bot,getCurrentPage(e));}
                else if("p:addonline".equals(act))openOnlinePicker(p,bot,0,null);
                else if(act.startsWith("p:search")){AWAIT_SEARCH.put(p.getUniqueId(),fn);p.closeInventory();p.sendMessage(t("聊天栏输入搜索名字",NamedTextColor.AQUA));}
                else if(act.startsWith("p:add:")){try{UUID tu=UUID.fromString(act.substring(6));hAddCollab(p,bot,tu);openOnlinePicker(p,bot,getCurrentPage(e),null);}catch(Exception ig){}}
                else if(act.startsWith("p:back_perm"))openPermMgmt(p,bot,0);
                else if(act.startsWith("p:rm:")){try{UUID ru=UUID.fromString(act.substring(5));hRemoveCollab(p,bot,ru);openPermMgmt(p,bot,0);}catch(Exception ig){}}
                else if(act.startsWith("p:c:")){try{UUID cu=UUID.fromString(act.substring(4));if(e.isShiftClick()){hRemoveCollab(p,bot,cu);openPermMgmt(p,bot,getCurrentPage(e));}else openPSetPage(p,bot,cu);}catch(Exception ig){}}
                else if(act.startsWith("p:ps:")){try{String[]ps=act.substring(5).split(":");if(ps.length>=2){UUID pu=UUID.fromString(ps[0]);hPSet(p,bot,pu,ps[1]);openPSetPage(p,bot,pu);}}catch(Exception ig){}}
                else if(act.startsWith("collab:rm:")){hRemoveCollab(p,bot,UUID.fromString(act.substring(10)));openPermMgmt(p,bot,0);}
                else if(act.startsWith("cfg:")){hCfg(p,bot,act.substring(4));openSettings(p,bot,getCurrentPage(e));}
                else if(act.startsWith("act:")){hAct(p,bot,act.substring(4));openActions(p,bot);}
            }
        }
    }
    private static int getCurrentPage(InventoryClickEvent e){// estimate from page label
        ItemStack pg=e.getView().getTopInventory().getItem(22);if(pg!=null&&pg.hasItemMeta()&&pg.getItemMeta().hasDisplayName()){
            String ds=pg.getItemMeta().displayName().toString();int s=ds.indexOf('第'),e2=ds.indexOf('/');if(s>=0&&e2>s)try{return Integer.parseInt(ds.substring(s+1,e2))-1;}catch(Exception ig){}}return 0;
    }
    private static ServerBot resolveBot(BotGuiHolder h){String fn=h.getBotFullName();if(fn==null)return null;return BotList.INSTANCE.getBotByName(fn.toLowerCase(Locale.ROOT));}

    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getInventory().getHolder(false)instanceof BotGuiHolder)e.setCancelled(true);}

    @EventHandler(priority=org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e){
        Player p=e.getPlayer();UUID uid=p.getUniqueId();
        if(AWAIT_NAME.remove(uid)){e.setCancelled(true);String name=e.getMessage().trim();
            if(!name.matches("^[a-zA-Z0-9_]{4,16}$")){p.sendMessage(t("名称不合法",NamedTextColor.RED));return;}
            p.getScheduler().run(MinecraftInternalPlugin.INSTANCE,task->{p.performCommand("bot "+name+" spawn");p.sendMessage(t("正在生成假人"+name,NamedTextColor.GREEN));p.getScheduler().runDelayed(MinecraftInternalPlugin.INSTANCE,t2->openMain(p),null,10L);},null);return;
        }
        if(AWAIT_GIVE.remove(uid)){e.setCancelled(true);p.sendMessage(t("转让请用: /bot <假人名> give <"+e.getMessage().trim()+">",NamedTextColor.RED));return;}
        if(AWAIT_SEARCH.containsKey(uid)){e.setCancelled(true);String filt=e.getMessage().trim();String fn=AWAIT_SEARCH.remove(uid);
            p.getScheduler().run(MinecraftInternalPlugin.INSTANCE,task->{ServerBot b=BotList.INSTANCE.getBotByName(fn.toLowerCase(Locale.ROOT));if(b!=null)openOnlinePicker(p,b,0,filt);},null);return;
        }
        if(AWAIT_XP.containsKey(uid)){e.setCancelled(true);String fn=AWAIT_XP.remove(uid);
            try{int amt=Integer.parseInt(e.getMessage().trim());int pg=XP_PAGE.getOrDefault(uid,0);
                p.getScheduler().run(MinecraftInternalPlugin.INSTANCE,task->{
                    if(pg==0)p.performCommand("bot "+fn+" xp level "+amt);        // take level
                    else if(pg==1)p.performCommand("bot "+fn+" xp take "+amt);     // take points
                    else if(pg==2)p.performCommand("bot "+fn+" xp level give "+amt); // give level
                    else p.performCommand("bot "+fn+" xp give "+amt);              // give points
                    String action=pg<2?"获取":"给予";String unit=pg%2==0?"级":"点";
                    p.sendMessage(t("已"+action+amt+unit+"经验",NamedTextColor.GREEN));
                    ServerBot b=BotList.INSTANCE.getBotByName(fn.toLowerCase(Locale.ROOT));if(b!=null)openXP(p,b,pg);
                },null);}catch(NumberFormatException ex){p.sendMessage(t("请输入数字",NamedTextColor.RED));}return;
        }
    }

    @EventHandler public void onInteractBot(PlayerInteractAtEntityEvent e){
        if(!FakeplayerConfig.shortcutEnabled)return;
        ServerBot bot=null;if(e.getRightClicked()instanceof org.leavesmc.leaves.entity.bot.CraftBot cb)bot=cb.getHandle();if(bot==null)return;
        Player p=e.getPlayer();if(!p.isSneaking()||!p.getInventory().getItemInMainHand().getType().isAir())return;
        if(!canM(bot,p))return;if(e.getHand()!=EquipmentSlot.HAND)openPanel(p,bot);
    }

    // Death respawn: intercept BotRemoveEvent to respawn if respawnOnDeath is enabled
    @EventHandler
    public void onBotRemove(org.leavesmc.leaves.event.bot.BotRemoveEvent e) {
        if (!FakeplayerConfig.respawnOnDeath) return;
        if (e.getReason() != org.leavesmc.leaves.event.bot.BotRemoveEvent.RemoveReason.DEATH) return;
        ServerBot bot = ((org.leavesmc.leaves.entity.bot.CraftBot) e.getBot()).getHandle();
        org.bukkit.Location loc = bot.getBukkitEntity().getLocation();
        String rawName = bot.createState != null ? bot.createState.rawName() : bot.getScoreboardName();
        // Respawn after 2 seconds
        org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(MinecraftInternalPlugin.INSTANCE, task -> {
            if (org.bukkit.Bukkit.getPlayerExact(rawName) == null
                && BotList.INSTANCE.getBotByName(bot.getScoreboardName().toLowerCase(java.util.Locale.ROOT)) == null) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(),
                    "bot " + rawName + " spawn at " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
            }
        }, 40L);
    }

    // --- helper methods ---
    private static void handleXp(Player p,ServerBot bot,String arg){
        String fn=bot.getScoreboardName(); int pg=XP_PAGE.getOrDefault(p.getUniqueId(),0);
        if("custom".equals(arg)||"give_lv:custom".equals(arg)||"give:custom".equals(arg)){
            AWAIT_XP.put(p.getUniqueId(),fn);p.closeInventory();
            String hint = switch(pg){case 0->"输入要获取的经验等级";case 1->"输入要获取的经验点数";case 2->"输入要给假人的经验等级";default->"输入要给假人的经验点数";};
            p.sendMessage(t(hint,NamedTextColor.GREEN));return;
        }
        // Determine action type from argument prefix
        if(arg.startsWith("give_lv:")){String amt=arg.substring(8);p.performCommand("bot "+fn+" xp level give "+amt);}  // give levels (custom command)
        else if(arg.startsWith("give:")){String amt=arg.substring(5);p.performCommand("bot "+fn+" xp give "+amt);}
        else if(pg==0){p.performCommand("bot "+fn+" xp level "+arg);}   // take level
        else{p.performCommand("bot "+fn+" xp take "+arg);}               // take points
        p.getScheduler().runDelayed(MinecraftInternalPlugin.INSTANCE,task->{ServerBot b=BotList.INSTANCE.getBotByName(fn.toLowerCase(Locale.ROOT));if(b!=null)openXP(p,b,pg);},null,2L);
    }
    private static void hGSet(Player p,ServerBot bot,String flag){
        BotOwnerRegistry.Entry e=BotOwnerRegistry.INSTANCE.get(bot.getScoreboardName());if(e==null)return;
        boolean now=!e.gsetFlags.contains(flag);if(now)e.gsetFlags.add(flag);else e.gsetFlags.remove(flag);BotOwnerRegistry.INSTANCE.save();
        p.sendMessage(t("公开权限 "+(now?"启用":"禁用")+": "+flag,NamedTextColor.GREEN));
    }
    private static void hPub(Player p,ServerBot bot){
        boolean now=!bot.collaborators.contains(ServerBot.PUBLIC_ACCESS_UUID);
        if(now)bot.collaborators.add(ServerBot.PUBLIC_ACCESS_UUID);else bot.collaborators.remove(ServerBot.PUBLIC_ACCESS_UUID);
        BotOwnerRegistry.INSTANCE.syncFromBot(bot);p.sendMessage(t("公开访问已"+(now?"开启":"关闭"),NamedTextColor.GREEN));
    }
    private static void hAddCollab(Player p,ServerBot bot,UUID u){
        if(bot.createPlayer!=null&&bot.createPlayer.equals(u)){p.sendMessage(t("不能把自己添加为协作者",NamedTextColor.RED));return;}
        if(bot.collaborators.contains(u)){p.sendMessage(t("该玩家已是协作者",NamedTextColor.RED));return;}
        bot.collaborators.add(u);BotOwnerRegistry.INSTANCE.syncFromBot(bot);p.sendMessage(t("已添加协作者",NamedTextColor.GREEN));
    }
    private static void hRemoveCollab(Player p,ServerBot bot,UUID u){
        bot.collaborators.remove(u);BotOwnerRegistry.INSTANCE.syncFromBot(bot);p.sendMessage(t("已移除协作者",NamedTextColor.GREEN));
    }
    private static void hPSet(Player p,ServerBot bot,UUID u,String flag){
        BotOwnerRegistry.Entry e=BotOwnerRegistry.INSTANCE.get(bot.getScoreboardName());if(e==null)return;
        Set<String>ps=e.psetFlags.computeIfAbsent(u,k->ConcurrentHashMap.newKeySet());
        boolean now=!ps.contains(flag);if(now)ps.add(flag);else ps.remove(flag);BotOwnerRegistry.INSTANCE.save();
        p.sendMessage(t((now?"启用":"禁用")+"了"+(e.ownerName.equals(Bukkit.getOfflinePlayer(u).getName())?"":Bukkit.getOfflinePlayer(u).getName()+"的")+flag,NamedTextColor.GREEN));
    }
    private static void hCfg(Player p,ServerBot bot,String cn){
        if("respawndeath".equals(cn)){FakeplayerConfig.respawnOnDeath=!FakeplayerConfig.respawnOnDeath;p.sendMessage(t("死亡重生已"+(FakeplayerConfig.respawnOnDeath?"开启":"关闭"),NamedTextColor.GREEN));return;}
        String nv;if("simulation_distance".equals(cn)){Object cv=bot.getConfigValue(Configs.SIMULATION_DISTANCE);int cur=cv instanceof Integer i?i:-1;nv=switch(cur){case 4->"8";case 8->"12";case 12->"16";case 16->"32";case 32->"-1";default->"4";};}
        else if("tick_type".equals(cn)){nv="NETWORK".equals(String.valueOf(bot.getConfigValue(Configs.TICK_TYPE)))?"entity_list":"network";}
        else{AbstractBotConfig<?,?>c=Configs.getConfig(cn);if(c==null)return;boolean on=bot.getConfigValue(c)instanceof Boolean b&&b;nv=on?"false":"true";}
        p.performCommand("bot "+bot.getScoreboardName()+" config "+cn+" "+nv);
    }
    private static void hAct(Player p,ServerBot bot,String act){
        String fn=bot.getScoreboardName();
        switch(act){
            case"sneak"->p.performCommand("bot "+fn+" "+(bot.isShiftKeyDown()?"unsneak":"sneak"));
            case"sprint"->p.performCommand("bot "+fn+" "+(bot.isSprinting()?"unsprint":"sprint"));
            case"attack_once"->p.performCommand("bot "+fn+" attack");
            case"attack_cont"->p.performCommand("bot "+fn+" "+(hasA(bot,"attack")?"actionstop attack":"attack continuous"));
            case"use_once"->p.performCommand("bot "+fn+" use");
            case"use_cont"->p.performCommand("bot "+fn+" "+(hasA(bot,"use_auto")?"actionstop use_auto":"use continuous"));
            case"break_once"->p.performCommand("bot "+fn+" break");
            case"break_cont"->p.performCommand("bot "+fn+" "+(hasA(bot,"break")?"actionstop break":"break continuous"));
            case"jump"->p.performCommand("bot "+fn+" jump");
            case"drop"->p.performCommand("bot "+fn+" drop");
            case"swap"->p.performCommand("bot "+fn+" swapHands");
            case"mount"->p.performCommand("bot "+fn+" mount");
            case"dismount"->p.performCommand("bot "+fn+" dismount");
            case"stopall"->p.performCommand("bot "+fn+" stop");
            case"look_n"->p.performCommand("bot "+fn+" look north");case"look_s"->p.performCommand("bot "+fn+" look south");
            case"look_e"->p.performCommand("bot "+fn+" look east");case"look_w"->p.performCommand("bot "+fn+" look west");
            case"look_u"->p.performCommand("bot "+fn+" look up");case"look_d"->p.performCommand("bot "+fn+" look down");
            case"move_f"->p.performCommand("bot "+fn+" move forward");case"move_b"->p.performCommand("bot "+fn+" move backward");
            case"move_l"->p.performCommand("bot "+fn+" move left");case"move_r"->p.performCommand("bot "+fn+" move right");
            default->{}
        }
    }
}
