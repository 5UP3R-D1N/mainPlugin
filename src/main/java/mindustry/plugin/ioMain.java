package mindustry.plugin;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.NetServer;
import mindustry.entities.Effect;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.effect.EffectHelper;
import mindustry.plugin.effect.EffectObject;
import mindustry.plugin.utils.Config;
import mindustry.plugin.utils.Cooldowns;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.ContentHandler;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.util.logging.FallbackLoggerConfiguration;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static arc.util.Log.*;
import static mindustry.Vars.*;
import static mindustry.plugin.effect.EffectHelper.getEffect;
import static mindustry.plugin.utils.Utils.*;
import static org.javacord.api.util.logging.FallbackLoggerConfiguration.setDebug;
import static org.javacord.api.util.logging.FallbackLoggerConfiguration.setTrace;

public class ioMain extends Plugin {
    //    public static final File prefsFile = new File("prefs.properties");
//    public static Net net = new Net();
//    public static Prefs prefs = new Prefs(prefsFile);
//    public GetMap map = new GetMap();
    public static final Fi pluginDir = new Fi("./config/mods/");
    public static final long CDT = 300L;
    public static final LocalDateTime startTime = LocalDateTime.now();
    private static final String lennyFace = "( \u0361\u00B0 \u035C\u0296 \u0361\u00B0)";
    public static String apiKey = "";
    public static String discordInviteLink;
    public static int effectId = 0; // effect id for the snowball
    public static ContentHandler contentHandler; // map and schem handler
    //    static Gson gson = new Gson();
    public static int logCount = 0; // only log join/leaves every 5 minutes
    //    public ObjectMap<String, TextChannel> discChannels = new ObjectMap<>();
    //    private final String fileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    public static NetServer.ChatFormatter chatFormatter = (player, message) -> player == null ? message : "[coral][[" + player.coloredName() + "[coral]]:[white] " + message;

    protected MiniMod[] minimods = new MiniMod[]{
            new mindustry.plugin.minimods.Communication(),
            new mindustry.plugin.minimods.Discord(),
            new mindustry.plugin.minimods.GameInfo(),
            new mindustry.plugin.minimods.Inspector(),
            new mindustry.plugin.minimods.JS(),
            new mindustry.plugin.minimods.Kick(),
            new mindustry.plugin.minimods.Logs(),
            new mindustry.plugin.minimods.Management(),
            new mindustry.plugin.minimods.Moderation(),
            new mindustry.plugin.minimods.Rainbow(),
            new mindustry.plugin.minimods.Ranks(),
            new mindustry.plugin.minimods.Redeem(),
            new mindustry.plugin.minimods.RTV(),
            new mindustry.plugin.minimods.ServerInfo(),
            new mindustry.plugin.minimods.Translate(),
            new mindustry.plugin.minimods.Weapon(),
    };

    // register event handlers and create variables in the constructor
    public ioMain() {
        info("Starting Discord Plugin...");
        info(lennyFace);
        // disable debug logs from javacord (doesnt work tho, idk why)
        setDebug(false);
        FallbackLoggerConfiguration.setDebug(false);
        FallbackLoggerConfiguration.setTrace(false);

        DiscordApi api;
        DiscordRegistrar registrar = null;
        // read settings
        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            JSONObject data = new JSONObject(new JSONTokener(pureJson));

            // url to connect to the MindServ
            Config.mapsURL = data.getString("maps_url");

            JSONObject discordData = data.getJSONObject("discord");
            discordInviteLink = discordData.getString("invite");
            String discordToken = discordData.getString("token");
            try {
                api = new DiscordApiBuilder().setToken(discordToken).login().join();
                Log.info("Logged in as: " + api.getYourself());
            } catch (Exception e) {
                Log.err("Couldn't log into discord.");
                Core.app.exit();
                return;
            }
            Channels.load(api, discordData.getJSONObject("channels"));
            Roles.load(api, discordData.getJSONObject("roles"));
            String discordPrefix = discordData.getString("prefix");
            DiscordVars.prefix = discordPrefix;
            registrar = new DiscordRegistrar(discordPrefix);

            Config.serverName = data.getString("server_name");
            Config.ipApiKey = data.getString("ipapi_key");

            JSONObject configData = data.getJSONObject("config");
            Config.previewSchem = configData.getBoolean("preview_schem");
            if (configData.has("map_rating")) {
                Config.mapRating = configData.getBoolean("map_rating");
            }

            // connect to database
            JSONObject databaseData = data.getJSONObject("database");
            String dbURL = databaseData.getString("url");
            String dbUser = databaseData.getString("user");
            String dbPwd = databaseData.getString("password");
            System.out.printf("database url: %s, user: %s%n\n", dbURL, dbUser);

            try { // test connection
                Database.connect(dbURL, dbUser, dbPwd);
            } catch (Exception e) {
                err(e.toString());
                err("Could not login to PostgresSQL database!");
            }
        } catch (Exception e) {
            Log.err("Couldn't read settings.json file.");
            Log.err(e.toString());
            return;
        }

        for (MiniMod mod : minimods) {
            mod.registerDiscordCommands(registrar);
        }

        DiscordRegistrar finalRegistrar = registrar;
        registrar.register("help", "[cmd]", data -> {
            data.help = "Display information about commands";
            data.aliases = new String[]{"h"};
        }, ctx -> {
            if (ctx.args.containsKey("cmd")) {
                ctx.sendEmbed(finalRegistrar.helpEmbed(ctx.args.get("cmd")));
            } else {
                ctx.sendEmbed(finalRegistrar.helpEmbed());
            }
        });

        Channels.BOT.addMessageCreateListener(registrar::dispatchEvent);
        Channels.ADMIN_BOT.addMessageCreateListener(registrar::dispatchEvent);
        Channels.STAFF_BOT.addMessageCreateListener(registrar::dispatchEvent);
        DiscordVars.api = api;

        Utils.init();
        EffectHelper.init();
        FallbackLoggerConfiguration.setDebug(false);
        FallbackLoggerConfiguration.setTrace(false);

        // Update discord status
        Timer.schedule((Runnable)this::updateDiscordStatus, 60, 60);

        // Display on-screen messages
        float duration = 10f;
        int start = 450;
        int increment = 30;

        Timer.schedule(() -> {
            int currentInc = 0;
            for (String msg : onScreenMessages) {
                Call.infoPopup(msg, duration, 20, 50, 20, start + currentInc, 0);
                currentInc = currentInc + increment;
            }
        }, 0, 10);

        Events.on(EventType.ServerLoadEvent.class, event -> {
//            contentHandler = new ContentHandler();
            Log.info("Everything's loaded !");
        });


        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;
            if (bannedNames.contains(escapeEverything(player.name))) {
                player.con.kick("[scarlet]Please change your name.");
                return;
            }

            // check if the player is already in the database
            Database.Player pd = Database.getPlayerData(player.uuid());

            // check if he's impersonating a rank
            // remove all color codes, so it's not possible to just change the color of the rank symbol
            String escapedName = escapeColorCodes(player.name).replaceAll("\\[accent\\]", "");
            for (int i = 0; i < Rank.all.length; i++) {
                if (i == 0) continue;

                Rank rank = Rank.all[i];
                if (escapedName.toLowerCase().contains(escapeColorCodes(rank.tag).replaceAll("\\[accent\\]", ""))) {
                    player.con.kick("[scarlet]Dont impersonate a rank.");
                    Log.warn("Player " + escapedName + " tried to impersonate rank: " + rank.name);
                    return;
                }
            }

            // check for ban & give name
            if (pd != null) {
                if (pd.banned || pd.bannedUntil > Instant.now().getEpochSecond()) {
                    player.con.kick("[scarlet]You are banned.[accent] Reason:\n" + pd.banReason + "\n[white] If you what to appeal join our discord server: [cyan]" + discordInviteLink);
                    return;
                }

                Rank rank = Rank.all[pd.rank];
                Call.sendMessage("[#" + rank.color.toString().substring(0, 6) + "]" + rank.name + "[] " + player.name + "[accent] joined the front!");
                player.name = rank.tag + player.name;

                // Give Marshals admin
                if (pd.rank == Rank.all.length - 1) {
                    player.admin = true;
                }
            } else { // not in database
                info("New player connected: " + escapeColorCodes(event.player.name));
                Database.setPlayerData(new Database.Player(player.uuid(), 0));

                Rank rank = Rank.all[0];
                Call.sendMessage("[#" + rank.color.toString().substring(0, 6) + "]" + rank.name + "[] " + player.name + "[accent] joined the front!");
            }

            Call.infoMessage(player.con, welcomeMessage);
            
//
//            CompletableFuture.runAsync(() -> {
//                if(verification) {
//                    if (pd != null && !pd.verified) {
//                        CustomLog.info("Unverified player joined: " + player.name);
//                        String url = "http://api.vpnblocker.net/v2/json/" + player.con.address + "/" + apiKey;
//                        String pjson = ClientBuilder.newClient().target(url).request().accept(MediaType.APPLICATION_JSON).get(String.class);
//
//                        JSONObject json = new JSONObject(new JSONTokener(pjson));
//                        if (json.has("host-ip")) {
//                            if (json.getBoolean("host-ip")) { // verification failed
//                                CustomLog.info("IP verification failed for: " + player.name);
//                                Call.onInfoMessage(player.con, verificationMessage);
//                            } else {
//                                CustomLog.info("IP verification success for: " + player.name);
//                                pd.verified = true;
//                                setData(player.uuid(), pd);
//                            }
//                        } else { // site doesn't work for some reason  ?
//                            pd.verified = true;
//                            setData(player.uuid(), pd);
//                        }
//                    }
//                }
//            });
//            player.sendMessage(welcomeMessage);

        });

        // Log game over
        Events.on(EventType.GameOverEvent.class, event -> {
            if (Groups.player.size() > 0) {
                EmbedBuilder gameOverEmbed = new EmbedBuilder().setTitle("Game over!").setDescription("Map " + escapeEverything(state.map.name()) + " ended with " + state.wave + " waves and " + Groups.player.size() + " players!").setColor(new Color(0x33FFEC));
                Channels.LOG.sendMessage(gameOverEmbed);
                Channels.CHAT.sendMessage(gameOverEmbed);
            }
        });

        // TODO: remove this when MapRules is back in use
        Cooldowns.instance.set("rotate", 0);
        Cooldowns.instance.set("configure", 1);
        Events.on(EventType.ServerLoadEvent.class, event -> {
            Vars.netServer.admins.addActionFilter(action -> {
                Player player = action.player;
                if (player == null) return true;
                if (player.admin) return true;

                switch (action.type) {
                    case rotate -> {
                        if (!Cooldowns.instance.canRun("rotate", player.uuid())) {
                            player.sendMessage(GameMsg.error("Mod", "Rotate ratelimit exceeded, please rotate slower"));
                            return false;
                        }
                    }
                    case configure -> {
                        if (!Cooldowns.instance.canRun("configure", player.uuid())) {
                            player.sendMessage(GameMsg.error("Mod", "Configure ratelimit exceeded, please configure slower"));
                            return false;
                        }
                    }
                    default -> {
                        return true;
                    }
                }
                return true;
            });

            Vars.netServer.admins.addChatFilter((player, message) -> {
                if (!checkChatRatelimit(message, player)) {
                    return null;
                }
                return message;
            });

            info("Registered all filters.");
        });

        for (MiniMod minimod : minimods) {
            minimod.registerEvents();
        }
    }

    public static boolean checkChatRatelimit(String message, Player player) {
        // copied almost exactly from mindustry core, will probably need updating
        // will also update the user's global chat ratelimits
        long resetTime = Administration.Config.messageRateLimit.num() * 1000L;
        if (Administration.Config.antiSpam.bool() && !player.isLocal() && !player.admin) {
            //prevent people from spamming messages quickly
            if (resetTime > 0 && Time.timeSinceMillis(player.getInfo().lastMessageTime) < resetTime) {
                //supress message
                player.sendMessage("[scarlet]You may only send messages every " + Administration.Config.messageRateLimit.num() + " seconds.");
                player.getInfo().messageInfractions++;
                //kick player for spamming and prevent connection if they've done this several times
                if (player.getInfo().messageInfractions >= Administration.Config.messageSpamKick.num() && Administration.Config.messageSpamKick.num() != 0) {
                    player.con.kick("You have been kicked for spamming.", 1000 * 60 * 2);
                }
                return false;
            } else {
                player.getInfo().messageInfractions = 0;
            }

            // prevent players from sending the same message twice in the span of 50 seconds
            if (message.equals(player.getInfo().lastSentMessage) && Time.timeSinceMillis(player.getInfo().lastMessageTime) < 1000 * 50) {
                player.sendMessage("[scarlet]You may not send the same message twice.");
                return false;
            }

            player.getInfo().lastSentMessage = message;
            player.getInfo().lastMessageTime = Time.millis();
        }
        return true;
    }

    public void updateDiscordStatus() {
        if (Vars.state.is(GameState.State.playing)) {
            DiscordVars.api.updateActivity("with " + Groups.player.size() + (netServer.admins.getPlayerLimit() == 0 ? "" : "/" + netServer.admins.getPlayerLimit()) + " players");
        } else {
            DiscordVars.api.updateActivity(ActivityType.CUSTOM, "Not currently hosting");
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("logging", "<trace/debug> <true/false>", "Enable or disable logging for javacord.", args -> {
            if (!Objects.equals(args[1], "false") && !Objects.equals(args[1], "true")) {
                err("Second argument has to be true or false!");
            }
            switch (args[0]) {
                case "trace", "t" -> {
                    setTrace(Objects.equals(args[1], "true"));
                    info("Set trace logging to " + args[1]);
                }
                case "debug", "d" -> {
                    setDebug(Objects.equals(args[1], "true"));
                    info("Set debug to " + args[1]);
                }
                default -> {
                    err("Please select either trace or debug!");
                }
            }
        });

        for (MiniMod mod : minimods) {
            mod.registerServerCommands(handler);
        }
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {
        for (MiniMod minimod : minimods) {
            minimod.registerCommands(handler);
        }
    }
}