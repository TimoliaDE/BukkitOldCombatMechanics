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

    // TODO
//     public void attack(Entity entity) {
//        if (entity.aD() && !entity.l(this)) {
//            float f = (float)this.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).getValue();
//            byte b0 = 0;
//            float f1 = 0.0F;
//            if (entity instanceof EntityLiving) {
//                f1 = EnchantmentManager.a(this.bA(), ((EntityLiving)entity).getMonsterType());
//            } else {
//                f1 = EnchantmentManager.a(this.bA(), EnumMonsterType.UNDEFINED);
//            }
//
//            int i = b0 + EnchantmentManager.a(this);
//            if (this.isSprinting()) {
//                ++i;
//            }
//
//            if (f > 0.0F || f1 > 0.0F) {
//                boolean flag = this.fallDistance > 0.0F && !this.onGround && !this.k_() && !this.V() && !this.hasEffect(MobEffectList.BLINDNESS) && this.vehicle == null && entity instanceof EntityLiving;
//                if (flag && f > 0.0F) {
//                    f *= 1.5F;
//                }
//
//                f += f1;
//                boolean flag1 = false;
//                int j = EnchantmentManager.getFireAspectEnchantmentLevel(this);
//                if (entity instanceof EntityLiving && j > 0 && !entity.isBurning()) {
//                    EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), 1);
//                    Bukkit.getPluginManager().callEvent(combustEvent);
//                    if (!combustEvent.isCancelled()) {
//                        flag1 = true;
//                        entity.setOnFire(combustEvent.getDuration());
//                    }
//                }
//
//                double d0 = entity.motX;
//                double d1 = entity.motY;
//                double d2 = entity.motZ;
//                boolean flag2 = entity.damageEntity(DamageSource.playerAttack(this), f);
//                if (flag2) {
//                    if (i > 0) {
//                        entity.g((double)(-MathHelper.sin(this.yaw * (float)Math.PI / 180.0F) * (float)i * 0.5F), 0.1, (double)(MathHelper.cos(this.yaw * (float)Math.PI / 180.0F) * (float)i * 0.5F));
//                        this.motX *= 0.6;
//                        this.motZ *= 0.6;
//                        this.setSprinting(false);
//                    }
//
//                    if (entity instanceof EntityPlayer && entity.velocityChanged) {
//                        boolean cancelled = false;
//                        Player player = (Player)entity.getBukkitEntity();
//                        Vector velocity = new Vector(d0, d1, d2);
//                        PlayerVelocityEvent event = new PlayerVelocityEvent(player, velocity.clone());
//                        this.world.getServer().getPluginManager().callEvent(event);
//                        if (event.isCancelled()) {
//                            cancelled = true;
//                        } else if (!velocity.equals(event.getVelocity())) {
//                            player.setVelocity(event.getVelocity());
//                        }
//
//                        if (!cancelled) {
//                            ((EntityPlayer)entity).playerConnection.sendPacket(new PacketPlayOutEntityVelocity(entity));
//                            entity.velocityChanged = false;
//                            entity.motX = d0;
//                            entity.motY = d1;
//                            entity.motZ = d2;
//                        }
//                    }
//
//                    if (flag) {
//                        this.b(entity);
//                    }
//
//                    if (f1 > 0.0F) {
//                        this.c(entity);
//                    }
//
//                    if (f >= 18.0F) {
//                        this.b((Statistic)AchievementList.F);
//                    }
//
//                    this.p(entity);
//                    if (entity instanceof EntityLiving) {
//                        EnchantmentManager.a((EntityLiving)entity, this);
//                    }
//
//                    EnchantmentManager.b(this, entity);
//                    ItemStack itemstack = this.bZ();
//                    Object object = entity;
//                    if (entity instanceof EntityComplexPart) {
//                        IComplex icomplex = ((EntityComplexPart)entity).owner;
//                        if (icomplex instanceof EntityLiving) {
//                            object = (EntityLiving)icomplex;
//                        }
//                    }
//
//                    if (itemstack != null && object instanceof EntityLiving) {
//                        itemstack.a((EntityLiving)object, this);
//                        if (itemstack.count == 0) {
//                            this.ca();
//                        }
//                    }
//
//                    if (entity instanceof EntityLiving) {
//                        this.a(StatisticList.w, Math.round(f * 10.0F));
//                        if (j > 0) {
//                            EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), j * 4);
//                            Bukkit.getPluginManager().callEvent(combustEvent);
//                            if (!combustEvent.isCancelled()) {
//                                entity.setOnFire(combustEvent.getDuration());
//                            }
//                        }
//                    }
//
//                    this.applyExhaustion(this.world.spigotConfig.combatExhaustion);
//                } else if (flag1) {
//                    entity.extinguish();
//                }
//            }
//        }
//
//    }

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
            // TODO: Versioning der .jar-Datei später ändern

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
