package mindustry.plugin.discord;

import arc.struct.StringMap;
import arc.util.Structs;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.utils.LogAction;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.springframework.context.annotation.Description;
import org.springframework.data.util.ReflectionUtils.DescribedFieldFilter;

import java.awt.*;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DiscordLog {
    /**
     * Log an error.
     *
     * @param fields List of fields. May be null.
     */
    public static void error(String title, String description, StringMap fields) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(DiscordPalette.ERROR)
                .setTimestamp(Instant.now());

        if (fields != null)
            for (var entry : fields) {
                eb.addField(entry.key, entry.value);
            }
        Channels.ERROR_LOG.sendMessage(eb);
    }

    /** Log a cheat */
    public static void cheat(String action, User mod, String information) {
        EmbedBuilder eb = new EmbedBuilder()
            .setColor(DiscordPalette.INFO)
            .addInlineField("Moderator", "<@" + mod.getId() + ">")
            .addInlineField("Players", StreamSupport.stream(Groups.player.spliterator(), false)
                .map(p -> Utils.escapeEverything(p.name()) + " `" + p.uuid() + "`")
                .collect(Collectors.joining("\n")));
        if (information != null) {
            eb.addField("Additional Information", information);
        }
        Channels.LOG.sendMessage(eb);
    }

    public static void logAction(LogAction action, Administration.PlayerInfo info, Context ctx, String reason) {
        logAction(action, info, ctx, reason, null, null);
    }

    public static void logAction(LogAction action, Administration.PlayerInfo info, Context ctx, String reason, MessageAttachment mapFile) {
        logAction(action, info, ctx, reason, mapFile, null);
    }

    public static void logAction(LogAction action, Context ctx, String reason, String ip) {
        logAction(action, null, ctx, reason, null, ip);
    }

    public static void logAction(LogAction action, Administration.PlayerInfo info, Context ctx, String reason, MessageAttachment mapFile, String ip) {
        EmbedBuilder eb = new EmbedBuilder();
        final String reasonNotNull = Objects.equals(reason, "") || reason == null ? "Not provided" : reason;
        switch (action) {
            case ban, unban, blacklist, kick -> {
                eb.setTitle(action.getName() + " " + Utils.escapeEverything(info.lastName))
                        .addField("UUID", info.id, true)
                        .addField("IP", info.lastIP, true)
                        .addField(action + " by", "<@" + ctx.author().getIdAsString() + ">", true)
                        .addField("Reason", reasonNotNull, true);
            }
            case ipBan, ipUnban -> {
                eb.setTitle(action.getName() + " " + ip)
                        .addField("IP", ip, true)
                        .addField(action + " by", "<@" + ctx.author().getIdAsString() + ">", true)
                        .addField("Reason", reasonNotNull, true);
            }
            case uploadMap, updateMap -> {
                eb.setTitle(action.getName() + " " + Utils.escapeEverything(mapFile.getFileName()))
                        .addField("Uploaded by ", "<@" + ctx.author().getIdAsString() + ">", true);
            }
            case setRank -> {
                Database.Player pd = Database.getPlayerData(info.id);
                eb.setTitle(Utils.escapeEverything(info.lastName) + "'s rank was set to " + Rank.all[pd.rank].name + "!")
                        .setColor(new Color(0x00ff00))
                        .addField("UUID", info.id)
                        .addField("By", "<@" + ctx.author().getIdAsString() + ">");
            }
        }
        eb.setTimestampToNow();
        Channels.LOG.sendMessage(eb);
    }
}
