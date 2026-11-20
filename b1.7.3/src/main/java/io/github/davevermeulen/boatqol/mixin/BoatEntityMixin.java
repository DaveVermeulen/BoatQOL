package io.github.davevermeulen.boatqol.mixin;

import java.util.List;

import io.github.davevermeulen.boatqol.BoatQOL;
import io.github.davevermeulen.boatqol.BoatState;
import io.github.davevermeulen.boatqol.ModdedClients;
import io.github.davevermeulen.boatqol.RemoteServer;
import io.github.davevermeulen.boatqol.net.VehicleMoveC2SPacket;
import net.minecraft.block.Block;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The 1.9-1.12 boat (see docs/SPEC.md), ported onto the Beta 1.7.3 boat.
 *
 * Yaw convention: the old boat's bow points along -(cos yaw, sin yaw) (the old
 * code turned the boat to face away from its motion, and seats the rider 0.4
 * blocks toward +(cos yaw, sin yaw), at the stern). That convention is kept so the
 * old renderer and seat line up, and the 1.12 formulas are rotated to match:
 * a rider facing the bow has yaw = boat yaw + 90.
 *
 * Float height: the 1.12 buoyancy sinks the 1.12 hull about 0.39 blocks, but the
 * Beta boat (model and hitbox) sits with its bottom at the water surface. All
 * water checks therefore use a "hull" {@link #HULL_DEPTH} below the hitbox, so
 * the boat floats at the same height as the vanilla Beta boat.
 */
@Mixin(BoatEntity.class)
public abstract class BoatEntityMixin extends Entity implements BoatState {
    @Unique private static final int STATUS_IN_WATER = 0;
    @Unique private static final int STATUS_UNDER_WATER = 1;
    @Unique private static final int STATUS_UNDER_FLOWING_WATER = 2;
    @Unique private static final int STATUS_ON_LAND = 3;
    @Unique private static final int STATUS_IN_AIR = 4;

    /** Server: ticks without a move from the rider's client before the server takes over. */
    @Unique private static final int CLIENT_MOVE_TIMEOUT = 60;
    /** Server: largest move per packet accepted from a client, squared (10 blocks). */
    @Unique private static final double MAX_CLIENT_MOVE_SQ = 100.0;

    /** How far the 1.12 hull reaches below the Beta hitbox (see class comment). */
    @Unique private static final double HULL_DEPTH = 0.385;

    @Unique private float boatqol$deltaRotation;
    @Unique private int boatqol$status = STATUS_IN_AIR;
    @Unique private int boatqol$prevStatus = STATUS_IN_AIR;
    @Unique private double boatqol$waterLevel;
    @Unique private float boatqol$glide;
    @Unique private double boatqol$lastYd;

    /** Server: age at which the rider's client last reported a move. */
    @Unique private int boatqol$lastClientMove = Integer.MIN_VALUE / 2;
    /** Client: whether this boat was steered by us last tick. */
    @Unique private boolean boatqol$locallyControlled;
    @Unique private int boatqol$ticksSinceSent;
    @Unique private double boatqol$sentX;
    @Unique private double boatqol$sentY;
    @Unique private double boatqol$sentZ;
    @Unique private float boatqol$sentYaw;

    protected BoatEntityMixin(World world) {
        super(world);
    }

    // ---------------------------------------------------------------- ticking

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void boatqol$tick(CallbackInfo ci) {
        if (!this.world.isRemote && this.passenger instanceof PlayerEntity && this.passenger.isSneaking()) {
            // Sneak to get out, like 1.9+. Done by the server (or singleplayer
            // world) so every client sees it, with or without the mod.
            this.passenger.setVehicle(this);
        }
        Entity rider = this.passenger;
        float[] input = rider == null ? null : BoatQOL.localInput.get(rider);

        if (this.world.isRemote) {
            // Multiplayer client.
            if (input != null && RemoteServer.hasMod()) {
                this.boatqol$locallyControlled = true;
                this.boatqol$modernTick(input);
                this.boatqol$sendMove();
                ci.cancel();
            } else if (this.boatqol$locallyControlled) {
                // We just got out: hand the boat back to the server's updates.
                this.boatqol$locallyControlled = false;
                this.velocityX = this.velocityY = this.velocityZ = 0.0;
            }
            // Otherwise the vanilla code interpolates the server's positions.
            return;
        }

        // Server, or singleplayer.
        if (input == null && rider instanceof PlayerEntity) {
            if (!ModdedClients.isModded(rider)) {
                // Rider has a vanilla client and can't steer the modern way:
                // keep the vanilla boat (it still never breaks, see below).
                return;
            }
            if (this.age - this.boatqol$lastClientMove <= CLIENT_MOVE_TIMEOUT) {
                // The rider's client drives this boat; positions come in by packet.
                this.boatqol$passiveTick();
                ci.cancel();
                return;
            }
        }
        this.boatqol$modernTick(input);
        ci.cancel();
    }

    @Unique
    private void boatqol$passiveTick() {
        super.tick();
        this.boatqol$tickWobble();
        this.fallDistance = 0.0F;
        if (this.passenger != null && this.passenger.dead) {
            this.passenger = null;
        }
    }

    @Unique
    private void boatqol$tickWobble() {
        BoatEntity self = (BoatEntity) (Object) this;
        if (self.damageWobbleTicks > 0) {
            self.damageWobbleTicks--;
        }
        if (self.damageWobbleStrength > 0) {
            self.damageWobbleStrength--;
        }
    }

    @Unique
    private void boatqol$modernTick(float[] input) {
        super.tick(); // baseTick: prev position/rotation, fire, water state
        this.boatqol$tickWobble();

        this.boatqol$prevStatus = this.boatqol$status;
        this.boatqol$status = this.boatqol$computeStatus();

        this.boatqol$updateMotion();
        if (input != null) {
            this.boatqol$control(input[0], input[1]);
        }
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.fallDistance = 0.0F; // never takes fall damage, never breaks on landing
        this.setRotation(this.yaw, 0.0F);

        this.boatqol$splash();

        List<?> nearby = this.world.getEntities(this, this.boundingBox.expand(0.2F, 0.0, 0.2F));
        if (nearby != null) {
            for (Object o : nearby) {
                Entity e = (Entity) o;
                if (e != this.passenger && e.isPushable() && e instanceof BoatEntity) {
                    e.onCollision(this);
                }
            }
        }

        if (this.passenger != null && this.passenger.dead) {
            this.passenger = null;
        }
    }

    // ---------------------------------------------------------------- 1.12 physics

    /** EntityBoat.controlBoat */
    @Unique
    private void boatqol$control(float forwardInput, float sidewaysInput) {
        boolean forward = forwardInput > 0.0F;
        boolean back = forwardInput < 0.0F;
        boolean left = sidewaysInput > 0.0F;
        boolean right = sidewaysInput < 0.0F;

        float f = 0.0F;
        if (left) {
            this.boatqol$deltaRotation -= 1.0F;
        }
        if (right) {
            this.boatqol$deltaRotation += 1.0F;
        }
        if (right != left && !forward && !back) {
            f += 0.005F;
        }
        this.yaw += this.boatqol$deltaRotation;
        if (forward) {
            f += 0.04F;
        }
        if (back) {
            f -= 0.005F;
        }
        // The bow is -(cos yaw, sin yaw) in the old boat's convention.
        double rad = this.yaw * Math.PI / 180.0;
        this.velocityX -= Math.cos(rad) * f;
        this.velocityZ -= Math.sin(rad) * f;
    }

    /** EntityBoat.updateMotion */
    @Unique
    private void boatqol$updateMotion() {
        double gravity = -0.04;
        double buoyancy = 0.0;
        float momentum = 0.05F;
        int status = this.boatqol$status;

        if (this.boatqol$prevStatus == STATUS_IN_AIR && status != STATUS_IN_AIR && status != STATUS_ON_LAND) {
            // Landed in water from the air: snap onto the surface.
            this.boatqol$waterLevel = this.boundingBox.minY - HULL_DEPTH + this.height;
            double minY = this.boatqol$waterLevelAbove() - this.height + 0.101 + HULL_DEPTH;
            this.setPosition(this.x, minY + this.standingEyeHeight - this.cameraOffset, this.z);
            this.velocityY = 0.0;
            this.boatqol$lastYd = 0.0;
            this.boatqol$status = STATUS_IN_WATER;
            return;
        }

        if (status == STATUS_IN_WATER) {
            buoyancy = (this.boatqol$waterLevel - (this.boundingBox.minY - HULL_DEPTH)) / this.height;
            momentum = 0.9F;
        } else if (status == STATUS_UNDER_FLOWING_WATER) {
            gravity = -7.0E-4;
            momentum = 0.9F;
        } else if (status == STATUS_UNDER_WATER) {
            buoyancy = 0.01;
            momentum = 0.45F;
        } else if (status == STATUS_IN_AIR) {
            momentum = 0.9F;
        } else if (status == STATUS_ON_LAND) {
            momentum = this.boatqol$glide;
        }

        this.velocityX *= momentum;
        this.velocityZ *= momentum;
        this.boatqol$deltaRotation *= momentum;
        this.velocityY += gravity;
        if (buoyancy > 0.0) {
            this.velocityY += buoyancy * 0.06153846;
            this.velocityY *= 0.75;
        }
        this.boatqol$lastYd = this.velocityY;
    }

    /** EntityBoat.getBoatStatus */
    @Unique
    private int boatqol$computeStatus() {
        int under = this.boatqol$underwaterStatus();
        if (under >= 0) {
            this.boatqol$waterLevel = this.boundingBox.maxY;
            return under;
        }
        if (this.boatqol$checkInWater()) {
            return STATUS_IN_WATER;
        }
        float glide = this.boatqol$computeGlide();
        if (glide > 0.0F) {
            this.boatqol$glide = glide;
            return STATUS_ON_LAND;
        }
        return STATUS_IN_AIR;
    }

    /** BlockLiquid.getLiquidHeight: water surface height inside the block, 0..1. */
    @Unique
    private float boatqol$liquidHeight(int x, int y, int z) {
        int meta = this.world.getBlockMeta(x, y, z);
        if ((meta & 7) == 0 && this.world.getMaterial(x, y + 1, z) == Material.WATER) {
            return 1.0F;
        }
        return 1.0F - LiquidBlock.getFluidHeightFromMeta(meta);
    }

    /** EntityBoat.checkInWater */
    @Unique
    private boolean boatqol$checkInWater() {
        Box bb = this.boundingBox;
        double hullMinY = bb.minY - HULL_DEPTH;
        int x0 = MathHelper.floor(bb.minX);
        int x1 = (int) Math.ceil(bb.maxX);
        int y0 = MathHelper.floor(hullMinY);
        int y1 = (int) Math.ceil(hullMinY + 0.001);
        int z0 = MathHelper.floor(bb.minZ);
        int z1 = (int) Math.ceil(bb.maxZ);
        boolean inWater = false;
        this.boatqol$waterLevel = -Double.MAX_VALUE;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (this.world.getMaterial(x, y, z) == Material.WATER) {
                        float level = y + this.boatqol$liquidHeight(x, y, z);
                        this.boatqol$waterLevel = Math.max(level, this.boatqol$waterLevel);
                        inWater |= hullMinY < level;
                    }
                }
            }
        }
        return inWater;
    }

    /** EntityBoat.getUnderwaterStatus; -1 when not under water. */
    @Unique
    private int boatqol$underwaterStatus() {
        Box bb = this.boundingBox;
        double top = bb.maxY + 0.001;
        int x0 = MathHelper.floor(bb.minX);
        int x1 = (int) Math.ceil(bb.maxX);
        int y0 = MathHelper.floor(bb.maxY);
        int y1 = (int) Math.ceil(top);
        int z0 = MathHelper.floor(bb.minZ);
        int z1 = (int) Math.ceil(bb.maxZ);
        boolean under = false;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (this.world.getMaterial(x, y, z) == Material.WATER
                            && top < y + this.boatqol$liquidHeight(x, y, z)) {
                        if (this.world.getBlockMeta(x, y, z) != 0) {
                            return STATUS_UNDER_FLOWING_WATER;
                        }
                        under = true;
                    }
                }
            }
        }
        return under ? STATUS_UNDER_WATER : -1;
    }

    /** EntityBoat.getWaterLevelAbove */
    @Unique
    private float boatqol$waterLevelAbove() {
        Box bb = this.boundingBox;
        int x0 = MathHelper.floor(bb.minX);
        int x1 = (int) Math.ceil(bb.maxX);
        double hullMaxY = bb.maxY - HULL_DEPTH;
        int y0 = MathHelper.floor(hullMaxY);
        int y1 = (int) Math.ceil(hullMaxY - this.boatqol$lastYd);
        int z0 = MathHelper.floor(bb.minZ);
        int z1 = (int) Math.ceil(bb.maxZ);
        layers:
        for (int y = y0; y < y1; y++) {
            float level = 0.0F;
            for (int x = x0; x < x1; x++) {
                for (int z = z0; z < z1; z++) {
                    if (this.world.getMaterial(x, y, z) == Material.WATER) {
                        level = Math.max(level, this.boatqol$liquidHeight(x, y, z));
                    }
                    if (level >= 1.0F) {
                        continue layers;
                    }
                }
            }
            if (level < 1.0F) {
                return y + level;
            }
        }
        return y1 + 1;
    }

    /**
     * EntityBoat.getBoatGlide: average slipperiness of the blocks the boat
     * rests on. Ice only counts as slippery with "use modern ice slide" on.
     */
    @Unique
    private float boatqol$computeGlide() {
        Box bb = this.boundingBox;
        Box below = Box.create(bb.minX, bb.minY - 0.001, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        int x0 = MathHelper.floor(below.minX) - 1;
        int x1 = (int) Math.ceil(below.maxX) + 1;
        int y0 = MathHelper.floor(below.minY) - 1;
        int y1 = (int) Math.ceil(below.maxY) + 1;
        int z0 = MathHelper.floor(below.minZ) - 1;
        int z1 = (int) Math.ceil(below.maxZ) + 1;
        boolean modernIce = RemoteServer.useModernIceSlide(this.world.isRemote);

        float total = 0.0F;
        int count = 0;
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                int edges = (x != x0 && x != x1 - 1 ? 0 : 1) + (z != z0 && z != z1 - 1 ? 0 : 1);
                if (edges == 2) {
                    continue;
                }
                for (int y = y0; y < y1; y++) {
                    if (edges > 0 && (y == y0 || y == y1 - 1)) {
                        continue;
                    }
                    int id = this.world.getBlockId(x, y, z);
                    Block block = id > 0 ? Block.BLOCKS[id] : null;
                    if (block == null) {
                        continue;
                    }
                    Box shape = block.getCollisionShape(this.world, x, y, z);
                    if (shape != null && shape.intersects(below)) {
                        float slip = block.slipperiness;
                        if (!modernIce && slip > 0.6F) {
                            slip = 0.6F; // Beta boats get no extra speed from ice
                        }
                        total += slip;
                        count++;
                    }
                }
            }
        }
        return count == 0 ? 0.0F : total / count;
    }

    /** The old boat's bow splash, kept because it costs nothing. */
    @Unique
    private void boatqol$splash() {
        if (this.boatqol$status != STATUS_IN_WATER) {
            return;
        }
        double speed = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        if (speed <= 0.15) {
            return;
        }
        double cos = Math.cos(this.yaw * Math.PI / 180.0);
        double sin = Math.sin(this.yaw * Math.PI / 180.0);
        for (int i = 0; i < 1.0 + speed * 60.0; i++) {
            double a = this.random.nextFloat() * 2.0F - 1.0F;
            double b = (this.random.nextInt(2) * 2 - 1) * 0.7;
            double px;
            double pz;
            if (this.random.nextBoolean()) {
                px = this.x - cos * a * 0.8 + sin * b;
                pz = this.z - sin * a * 0.8 - cos * b;
            } else {
                px = this.x + cos + sin * a * 0.7;
                pz = this.z + sin - cos * a * 0.7;
            }
            this.world.addParticle("splash", px, this.y - 0.125, pz, this.velocityX, this.velocityY, this.velocityZ);
        }
    }

    // ---------------------------------------------------------------- multiplayer

    @Unique
    private void boatqol$sendMove() {
        this.boatqol$ticksSinceSent++;
        boolean moved = this.x != this.boatqol$sentX || this.y != this.boatqol$sentY
                || this.z != this.boatqol$sentZ || this.yaw != this.boatqol$sentYaw;
        if (!moved && this.boatqol$ticksSinceSent < 10) {
            return; // still send now and then, so the server knows we're steering
        }
        this.boatqol$ticksSinceSent = 0;
        this.boatqol$sentX = this.x;
        this.boatqol$sentY = this.y;
        this.boatqol$sentZ = this.z;
        this.boatqol$sentYaw = this.yaw;
        RemoteServer.send(new VehicleMoveC2SPacket(this.id, this.x, this.y, this.z, this.yaw));
    }

    @Override
    public void boatqol$acceptClientMove(double x, double y, double z, float yaw) {
        double dx = x - this.x;
        double dy = y - this.y;
        double dz = z - this.z;
        if (dx * dx + dy * dy + dz * dz > MAX_CLIENT_MOVE_SQ) {
            BoatQOL.LOGGER.warn("Boat {} moved wrongly ({} blocks), ignoring", this.id, Math.sqrt(dx * dx + dy * dy + dz * dz));
            return;
        }
        this.velocityX = dx;
        this.velocityY = dy;
        this.velocityZ = dz;
        this.setPosition(x, y, z);
        this.setRotation(yaw, 0.0F);
        this.fallDistance = 0.0F;
        this.boatqol$lastClientMove = this.age;
        if (this.passenger != null) {
            this.updatePassengerPosition();
        }
    }

    @Override
    public boolean boatqol$isLocallyControlled() {
        return this.boatqol$locallyControlled && this.passenger != null;
    }

    @Override
    public boolean boatqol$isSteeredHere() {
        return this.passenger != null
                && BoatQOL.localInput.get(this.passenger) != null
                && (!this.world.isRemote || RemoteServer.hasMod());
    }

    /** Sneaking players can't get in (they would fall straight out again). */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void boatqol$noSneakBoarding(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player.isSneaking() && player.vehicle != this) {
            cir.setReturnValue(true);
        }
    }

    // ---------------------------------------------------------------- durability

    /** Same hits-to-break as before, but drops a boat item instead of planks and sticks. */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void boatqol$damage(Entity source, int amount, CallbackInfoReturnable<Boolean> cir) {
        BoatEntity self = (BoatEntity) (Object) this;
        if (!this.world.isRemote && !this.dead) {
            self.damageWobbleSide = -self.damageWobbleSide;
            self.damageWobbleTicks = 10;
            self.damageWobbleStrength += amount * 10;
            this.scheduleVelocityUpdate();
            if (self.damageWobbleStrength > 40) {
                if (this.passenger != null) {
                    this.passenger.setVehicle(this);
                }
                this.dropItem(Item.BOAT.id, 1, 0.0F);
                this.markDead();
            }
        }
        cir.setReturnValue(true);
    }

    /** The vanilla tick (still used for riders without the mod) must not break the boat in a crash. */
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/BoatEntity;markDead()V"))
    private void boatqol$noCrashBreak(BoatEntity boat) {
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/BoatEntity;dropItem(IIF)Lnet/minecraft/entity/ItemEntity;"))
    private ItemEntity boatqol$noCrashDrops(BoatEntity boat, int id, int amount, float yOffset) {
        return null;
    }
}
