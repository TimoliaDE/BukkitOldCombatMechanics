package kernitus.plugin.OldCombatMechanics.module;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import org.bukkit.*;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.Set;

/*
 * Makes wind charges visible to clients that do not support them (1.20.2 and older)
 * by using an ender eye as a visual representation.
 */
public class ModuleVisibleWindCharge extends OCMModule {

    private static final int PROTOCOL_1_20_2 = 764;
    private int particleTaskId = -1;
    private boolean withParticles;

    private final Set<AbstractWindCharge> windCharges = new HashSet<>();

    public ModuleVisibleWindCharge(OCMMain plugin) {
        super(plugin, "visible-wind-charge");
        reload();
    }

    @Override
    public void reload() {
        withParticles = module().getBoolean("with-particles", true);
        if (withParticles) {
            startParticleTaskIfNeeded();
        } else {
            stopParticleTaskIfNeeded();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!ViaVersionUtil.isLegacyClientsAllowed(PROTOCOL_1_20_2)) return;

        Player player = event.getPlayer();
        if (ViaVersionUtil.isLegacyClient(player, PROTOCOL_1_20_2)) return;

        windCharges.removeIf(Entity::isDead);
        windCharges.forEach(entity -> player.hideEntity(plugin, entity));
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!ViaVersionUtil.isLegacyClientsAllowed(PROTOCOL_1_20_2)) return;

        Projectile proj = event.getEntity();
        if (proj instanceof AbstractWindCharge windCharge) {
            World world = proj.getWorld();

            Entity enderEye = world.spawnEntity(proj.getLocation(), EntityType.EYE_OF_ENDER);
            enderEye.setGravity(true);
            enderEye.setInvulnerable(true);
            enderEye.setVelocity(proj.getVelocity());

            EyeOfEnder eyeOfEnder = (EyeOfEnder) ((CraftEntity) enderEye).getHandle();
            eyeOfEnder.life = Integer.MIN_VALUE;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ViaVersionUtil.isLegacyClient(player, PROTOCOL_1_20_2)) continue;

                player.hideEntity(plugin, enderEye);
            }

            proj.setMetadata("wind_charge", new FixedMetadataValue(plugin, enderEye));
            proj.setPassenger(enderEye);
            windCharges.add(windCharge);
        }
    }

    private void startParticleTaskIfNeeded() {
        if (particleTaskId != -1) return;
        particleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::onTick, 0, 1L);
    }

    private void stopParticleTaskIfNeeded() {
        if (particleTaskId == -1) return;
        Bukkit.getScheduler().cancelTask(particleTaskId);
        particleTaskId = -1;
    }
    
    private void onTick() {
        if (!withParticles) return;

        windCharges.removeIf(charge -> {
            if (charge.isDead()) return true;

            Location particleLoc = charge.getLocation()
                    .add(0, charge.getBoundingBox().getHeight(), 0);
            World world = particleLoc.getWorld();
            world.getPlayers().forEach(player -> {
                if (!ViaVersionUtil.isLegacyClient(player, PROTOCOL_1_20_2)) return;

                player.spawnParticle(Particle.INSTANT_EFFECT, particleLoc,
                        1, 0, 0, 0, 0,
                        new Particle.Spell(Color.WHITE, 1), true);
            });
            return false;
        });
    }

    @EventHandler
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        if (!ViaVersionUtil.isLegacyClientsAllowed(PROTOCOL_1_20_2)) return;

        Entity entity = event.getEntity();

        if (entity instanceof WindCharge charge && entity.hasMetadata("wind_charge")) {
            Entity passenger = (Entity) entity.getMetadata("wind_charge")
                    .getFirst().value();
            if (passenger != null) {
                windCharges.remove(charge);
                // This prevents getting an error:
                Bukkit.getScheduler().runTask(plugin, passenger::remove);
            }
        }
    }
}
