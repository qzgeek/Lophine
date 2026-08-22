package org.leavesmc.leaves.bot;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.*;
import org.slf4j.Logger;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BotOwnerRegistry {
    public static BotOwnerRegistry INSTANCE;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final class Entry {
        public String fullName, rawName, ownerName = "";
        @Nullable public UUID owner;
        public final Set<UUID> collaborators = ConcurrentHashMap.newKeySet();
        public boolean publicAccess;
        // PlayerDoll-style GSet/PSet: per-action permission flags
        // true = allowed for all (gset) or for specific player (pset)
        public final Set<String> gsetFlags = ConcurrentHashMap.newKeySet();
        public final Map<UUID, Set<String>> psetFlags = new ConcurrentHashMap<>();
        public boolean isOwner(UUID u) { return owner != null && owner.equals(u); }
    }
    private final MinecraftServer server;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    public BotOwnerRegistry(@NotNull MinecraftServer server) { this.server = server; load(); INSTANCE = this; }
    private File file() { File d = server.storageSource.getLevelPath(new LevelResource("lophine_config")).toFile(); d.mkdirs(); return new File(d, "bot_owners.dat"); }
    private synchronized void load() {
        File f = file(); if (!f.exists()) return;
        try {
            CompoundTag root = NbtIo.readCompressed(f.toPath(), NbtAccounter.unlimitedHeap());
            for (int i = 0; i < root.getList("entries").orElse(new ListTag()).size(); i++) {
                CompoundTag t = root.getList("entries").orElse(new ListTag()).getCompound(i).orElse(null);
                if (t == null) continue;
                Entry e = new Entry(); e.fullName = t.getStringOr("name",""); if (e.fullName.isEmpty()) continue;
                e.rawName = t.getStringOr("rawName", e.fullName);
                String os = t.getStringOr("owner",""); if (!os.isEmpty()) try { e.owner = UUID.fromString(os); } catch (IllegalArgumentException ignored) {}
                e.ownerName = t.getStringOr("ownerName",""); e.publicAccess = t.getBooleanOr("public",false);
                // load gset flags
                ListTag gl = t.getList("gset").orElse(new ListTag());
                for (int j = 0; j < gl.size(); j++) e.gsetFlags.add(gl.getString(j).orElse(""));
                // load pset flags
                ListTag pl = t.getList("pset").orElse(new ListTag());
                for (int j = 0; j < pl.size(); j++) {
                    CompoundTag pt = pl.getCompound(j).orElse(null); if (pt == null) continue;
                    String pu = pt.getStringOr("player",""); if (pu.isEmpty()) continue;
                    try {
                        UUID puid = UUID.fromString(pu);
                        Set<String> fs = java.util.concurrent.ConcurrentHashMap.newKeySet();
                        ListTag fl = pt.getList("flags").orElse(new ListTag());
                        for (int k = 0; k < fl.size(); k++) fs.add(fl.getString(k).orElse(""));
                        e.psetFlags.put(puid, fs);
                    } catch (IllegalArgumentException ignored) {}
                }
                ListTag cl = t.getList("collab").orElse(new ListTag());
                for (int j = 0; j < cl.size(); j++) { String us = cl.getString(j).orElse(""); if (!us.isEmpty()) try { e.collaborators.add(UUID.fromString(us)); } catch (IllegalArgumentException ignored) {} }
                entries.put(e.fullName.toLowerCase(Locale.ROOT), e);
            }
        } catch (Exception ex) { LOGGER.warn("加载假人所有权注册表失败", ex); }
    }
    public synchronized void save() {
        try {
            CompoundTag root = new CompoundTag(); ListTag list = new ListTag();
            for (Entry e : entries.values()) {
                CompoundTag t = new CompoundTag(); t.putString("name",e.fullName); t.putString("rawName",e.rawName);
                t.putString("owner",e.owner==null?"":e.owner.toString()); t.putString("ownerName",e.ownerName); t.putBoolean("public",e.publicAccess);
                ListTag gl = new ListTag(); for (String f : e.gsetFlags) gl.add(StringTag.valueOf(f)); t.put("gset", gl);
                ListTag pl = new ListTag();
                for (var pe : e.psetFlags.entrySet()) {
                    CompoundTag pt = new CompoundTag(); pt.putString("player", pe.getKey().toString());
                    ListTag fl = new ListTag(); for (String f : pe.getValue()) fl.add(StringTag.valueOf(f));
                    pt.put("flags", fl); pl.add(pt);
                }
                t.put("pset", pl);
                ListTag cl = new ListTag(); for (UUID u : e.collaborators) cl.add(StringTag.valueOf(u.toString())); t.put("collab",cl); list.add(t);
            }
            root.put("entries",list);
            File f = file(); Path tmp = f.toPath().resolveSibling("bot_owners.dat.tmp");
            NbtIo.writeCompressed(root, tmp); Files.move(tmp, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) { LOGGER.warn("保存失败", ex); }
    }
    @Nullable public Entry get(@NotNull String fn) { return entries.get(fn.toLowerCase(Locale.ROOT)); }
    public void record(@NotNull ServerBot bot) {
        String fn = bot.getScoreboardName();
        Entry e = entries.computeIfAbsent(fn.toLowerCase(Locale.ROOT), k -> new Entry());
        e.fullName = fn; e.rawName = bot.createState != null ? bot.createState.rawName() : fn; e.owner = bot.createPlayer;
        if (bot.createPlayer != null) { org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(bot.createPlayer); if (op.getName() != null) e.ownerName = op.getName(); }
        syncCollab(e, bot); save();
    }
    public void syncFromBot(@NotNull ServerBot bot) { Entry e = get(bot.getScoreboardName()); if (e == null) { record(bot); return; } syncCollab(e, bot); save(); }
    private void syncCollab(Entry e, ServerBot bot) { e.collaborators.clear(); e.publicAccess = bot.collaborators.contains(ServerBot.PUBLIC_ACCESS_UUID); for (UUID u : bot.collaborators) if (!u.equals(ServerBot.PUBLIC_ACCESS_UUID)) e.collaborators.add(u); }
    public void applyTo(@NotNull ServerBot bot) { Entry e = get(bot.getScoreboardName()); if (e == null) return; if (bot.createPlayer == null && e.owner != null) bot.createPlayer = e.owner; bot.collaborators.addAll(e.collaborators); if (e.publicAccess) bot.collaborators.add(ServerBot.PUBLIC_ACCESS_UUID); }
    public void remove(@NotNull String fn) { if (entries.remove(fn.toLowerCase(Locale.ROOT)) != null) save(); }
    public int countByOwner(@NotNull UUID o) { int c = 0; for (Entry e : entries.values()) if (e.isOwner(o)) c++; return c; }
    /** Count online bots owned by player (excludes despawned/offline entries) */
    public int countOnlineByOwner(@NotNull UUID o) {
        int c = 0;
        for (Entry e : entries.values()) {
            if (e.isOwner(o) && BotList.INSTANCE.getBotByName(e.fullName.toLowerCase(java.util.Locale.ROOT)) != null) c++;
        }
        return c;
    }
    public List<Entry> listByOwner(@NotNull UUID o) { List<Entry> l = new ArrayList<>(); for (Entry e : entries.values()) if (e.isOwner(o)) l.add(e); l.sort(Comparator.comparing(a->a.fullName,String.CASE_INSENSITIVE_ORDER)); return l; }
    public List<Entry> all() { List<Entry> l = new ArrayList<>(entries.values()); l.sort(Comparator.comparing(a->a.fullName,String.CASE_INSENSITIVE_ORDER)); return l; }
}
