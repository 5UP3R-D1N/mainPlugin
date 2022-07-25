package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.content.Bullets;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.lang.reflect.Field;

public class Weapon implements MiniMod {
    private final ObjectMap<String, WeaponData> weaponDatas = new ObjectMap<>();

    @Override
    public void registerEvents() {
        Events.on(EventType.Trigger.class, event -> {
            for (Player p : Groups.player) {
                if (weaponDatas.containsKey(p.uuid()) && p.shooting) {
                    WeaponData data = weaponDatas.get(p.uuid());
                    Call.createBullet(data.type, p.team(), p.getX(), p.getY(), p.unit().rotation, data.damage, data.velocity, data.lifetime);
                }
            }
        });
    }

    public BulletType findBullet(String query) {
        try {
            Field field = Bullets.class.getDeclaredField(query);
            return (BulletType) field.get(null);
        } catch(NoSuchFieldException | IllegalAccessException f) {}

        String[] parts = query.split(":");
        if (parts.length > 2 )return null;

        var unit = Vars.content.unit(parts[0]);
        if (unit == null) {
            return null;
        }
        int idx = 0;
        if (parts.length == 2) {
            idx = Strings.parseInt(parts[1]);
        }
        if (idx >= unit.weapons.size) {
            return null;
        }

        return unit.weapons.get(idx).bullet;
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("weapon", "<player> <bullet> [damage] [lifetime] [velocity]",
                data -> {
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.category = "Cheats";
                    data.help = "Modify the specified player's weapon.";
                },
                ctx -> {
                    float dmg = ctx.args.getFloat("damage", 1.0f);
                    float life = ctx.args.getFloat("lifetime", 1.0f);
                    float vel = ctx.args.getFloat("velocity", 1.0f);
                    BulletType bulletType = findBullet(ctx.args.get("bullet"));
                    if (bulletType == null) {
                        ctx.error("No Such Bullet", "Bullet '" + ctx.args.get("bullet") + "' does not exist. Either use one of the predefined bullets bullets:\n```\n" +
                                Seq.with(Bullets.class.getDeclaredFields()).filter(x -> x.getType().equals(BulletType.class)).map(x -> x.getName()).toString("\n")
                                + "\n```\n" + 
                                "Or use the format `unit-name:index`, where `index` is an integer, to get the weapon of a unit"
                        );
                        return;
                    }

                    Player player = Query.findPlayerEntity(ctx.args.get("player"));
                    if (player == null) {
                        ctx.error("No Such Player", ctx.args.get("player") + " does not exist");
                        return;
                    }

                    WeaponData data = new WeaponData();
                    data.damage = dmg;
                    data.lifetime = life;
                    data.velocity = vel;
                    data.type = bulletType;
                    weaponDatas.put(player.uuid(), data);

                    ctx.sendEmbed(new EmbedBuilder()
                            .setTitle("Modded " + Utils.escapeEverything(player.name()) + "'s gun")
                            .addInlineField("Damage", dmg + "")
                            .addInlineField("Lifetime", life + "")
                            .addInlineField("Velocity", vel + "")
                            .addInlineField("Type", ctx.args.get("bullet"))
                            .setColor(DiscordPalette.SUCCESS)
                    );

                    DiscordLog.cheat("Weapon", ctx.author(), "Player: " + Utils.escapeEverything(player.name()) + "\nDamage: " + dmg + "\nLife: " + life + "\nVelocity: " +vel +"\nType: " + ctx.args.get("bullet"));
                }
        );
    }

    private static class WeaponData {
        public float velocity;
        public float lifetime;
        public float damage;
        public BulletType type;
    }
}
