package org.leavesmc.leaves.bot.gui;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

public class BotGuiHolder implements InventoryHolder {
    public enum MenuType { MAIN, PANEL, SETTINGS, ACTIONS, COLLAB, CONFIRM_REMOVE }
    private final MenuType type;
    @Nullable private final String botFullName;
    private Inventory inventory;
    public BotGuiHolder(MenuType type, @Nullable String botFullName) { this.type = type; this.botFullName = botFullName; }
    public MenuType getType() { return type; }
    @Nullable public String getBotFullName() { return botFullName; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
    @Override public Inventory getInventory() { return inventory; }
}
