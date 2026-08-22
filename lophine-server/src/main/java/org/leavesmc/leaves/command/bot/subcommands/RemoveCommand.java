/*
 * This file is part of Leaves (https://github.com/LeavesMC/Leaves)
 *
 * Leaves is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Leaves is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Leaves. If not, see <https://www.gnu.org/licenses/>.
 */

package org.leavesmc.leaves.command.bot.subcommands;

import io.papermc.paper.adventure.PaperAdventure;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.command.ArgumentNode;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.arguments.BotArgumentType;
import org.leavesmc.leaves.command.bot.BotSubcommand;

import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.spaces;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class RemoveCommand extends BotSubcommand {

    public RemoveCommand() {
        super("remove");
        children(BotArgument::new);
    }

    private static class BotArgument extends ArgumentNode<ServerBot> {
        private BotArgument() {
            super("bot", BotArgumentType.bot());
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) {
            ServerBot bot = context.getArgument(BotArgument.class);
            BotList.INSTANCE.removeBotPermanently(bot, context.getSender());
            return true;
        }
    }
}