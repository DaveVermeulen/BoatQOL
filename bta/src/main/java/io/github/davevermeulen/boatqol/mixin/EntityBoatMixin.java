package io.github.davevermeulen.boatqol.mixin;

import java.util.List;

import io.github.davevermeulen.boatqol.BoatQOL;
import io.github.davevermeulen.boatqol.BoatState;
import io.github.davevermeulen.boatqol.ModdedClients;
import io.github.davevermeulen.boatqol.RemoteServer;
import io.github.davevermeulen.boatqol.net.BoatPayloads;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogicFluid;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.block.tag.BlockTags;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.entity.vehicle.EntityBoat;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.world.pos.TilePos;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import net.minecraft.core.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The 1.9-1.12 boat (see docs/SPEC.md), ported onto the BTA boat. Same approach
 * as the b1.7.3 port:
 *
 * Yaw convention: the BTA boat's bow points along -(cos yRot, sin yRot) and the
 * rider sits 0.4 blocks toward the stern. That is kept so the renderer and seat
 * line up, and the 1.12 formulas are rotated to match: a rider facing the bow has
 * yRot = boat yRot + 90.
 *
 * Float height: the 1.12 buoyancy sinks the 1.12 hull about 0.39 blocks, but the
 * BTA boat sits with its bottom at the water surface. All water checks use a hull
 * {@link #HULL_DEPTH} below the hitbox, so it floats at the vanilla BTA height.
 */
@Mixin(EntityBoat.class)
public abstract class EntityBoatMixin extends Entity implements BoatState {
    @Unique private static final int STATUS_IN_WATER = 0;
    @Unique private static final int STATUS_UNDER_WATER = 1;
    @Unique private static final int STATUS_UNDER_FLOWING_WATER = 2;
    @Unique private static final int STATUS_ON_LAND = 3;
    @Unique private static final int STATUS_IN_AIR = 4;

    /** How far the 1.12 hull reaches below the BTA hitbox (see class comment). */
    @Unique private static final double HULL_DEPTH = 0.40;
    /** Server: ticks without a move from the rider's client before the server takes over. */
    @Unique private static final int CLIENT_MOVE_TIMEOUT = 60;
    /** Server: largest move per packet accepted from a client, squared (10 blocks). */
    @Unique private static final double MAX_CLIENT_MOVE_SQ = 100.0;

    @Unique private float boatqol$deltaRotation;
    @Unique private int boatqol$status = STATUS_IN_AIR;
    @Unique private int boatqol$prevStatus = STATUS_IN_AIR;
    @Unique private double boatqol$waterLevel;
    @Unique private float boatqol$glide;
    @Unique private double boatqol$lastYd;

    /** Server: tickCount at which the rider's client last reported a move. */
    @Unique private int boatqol$lastClientMove = Integer.MIN_VALUE / 2;
    /** Client: whether this boat was steered by us last tick. */
    @Unique private boolean boatqol$locallyControlled;
    @Unique private int boatqol$ticksSinceSent;
    @Unique private double boatqol$sentX;
    @Unique private double boatqol$sentY;
    @Unique private double boatqol$sentZ;
    @Unique private float boatqol$sentYRot;

    protected EntityBoatMixin(World world) {
        super(world);
    }

    // ---------------------------------------------------------------- ticking

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void boatqol$tick(CallbackInfo ci) {
        Entity rider = this.passenger;
        float[] input = rider == null ? null : BoatQOL.localInput.get(rider);

        if (this.world.isClientSide) {
            // Multiplayer client.
            if (input != null && RemoteServer.hasMod()) {
                this.boatqol$locallyControlled = true;
                this.boatqol$modernTick(input);
                this.boatqol$sendMove();
                ci.cancel();
            } else if (this.boatqol$locallyControlled) {
                // We just got out: hand the boat back to the server's updates.
                this.boatqol$locallyControlled = false;
                this.xd = this.yd = this.zd = 0.0;
            }
            // Otherwise the vanilla code follows the server's positions.
            return;
        }

        // Server, or singleplayer.
        if (input == null && rider instanceof Player) {
            if (!ModdedClients.isModded(rider)) {
                // Rider has a vanilla client: keep the vanilla BTA boat, which their
                // client steers with BTA's own boat packets.
                return;
            }
            if (this.tickCount - this.boatqol$lastClientMove <= CLIENT_MOVE_TIMEOUT) {
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
        if (this.passenger != null && this.passenger.removed) {
            this.passenger = null;
        }
    }

    @Unique
    private void boatqol$tickWobble() {
        EntityBoat self = (EntityBoat) (Object) this;
        if (self.boatTimeSinceHit > 0) {
            self.boatTimeSinceHit--;
        }
        if (self.boatCurrentDamage > 0) {
            self.boatCurrentDamage--;
        }
    }

    @Unique
    private void boatqol$modernTick(float[] input) {
        this.setRot(this.yRot, this.xRot);
        super.tick(); // baseTick: previous position/rotation, fire, water state
        this.boatqol$tickWobble();

        this.boatqol$prevStatus = this.boatqol$status;
        this.boatqol$status = this.boatqol$computeStatus();

        this.boatqol$updateMotion();
        if (input != null) {
            this.boatqol$control(input[0], input[1]);
        }
        this.move(this.xd, this.yd, this.zd);
        this.fallDistance = 0.0F; // never takes fall damage, never breaks on landing
        this.setRot(this.yRot, 0.0F);

        this.boatqol$splash();

        List<Entity> nearby = this.world.getEntitiesWithinAABBExcludingEntity(this, MathHelper.aabbGrow(this.bb, 0.2, 0.0, 0.2, new AABBd()));
        if (nearby != null) {
            for (Entity e : nearby) {
                if (e != this.passenger && e.isPushable() && e instanceof EntityBoat) {
                    e.push(this);
                }
            }
        }

        // Vanilla BTA: boats break blocks tagged for it (like lily pads) as they pass.
        if (!this.world.isClientSide) {
            for (int i = 0; i < 4; i++) {
                int bx = MathHelper.floor(this.x + (i % 2 - 0.5) * 0.8);
                int by = MathHelper.floor(this.y);
                int bz = MathHelper.floor(this.z + (i / 2 - 0.5) * 0.8);
                Block<?> block = this.world.getBlock(bx, by, bz);
                if (block != null && block.hasTag(BlockTags.BOAT_BREAKS)) {
                    this.world.setBlockWithNotify(bx, by, bz, 0);
                }
            }
        }

        if (this.passenger != null && this.passenger.removed) {
            this.passenger = null;
        }
    }

    // ---------------------------------------------------------------- 1.12 physics

    /** EntityBoat.controlBoat from 1.12 */
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
        this.yRot += this.boatqol$deltaRotation;
        if (forward) {
            f += 0.04F;
        }
        if (back) {
            f -= 0.005F;
        }
        // The bow is -(cos yRot, sin yRot).
        double rad = this.yRot * Math.PI / 180.0;
        this.xd -= Math.cos(rad) * f;
        this.zd -= Math.sin(rad) * f;
    }

    /** EntityBoat.updateMotion from 1.12 */
    @Unique
    private void boatqol$updateMotion() {
        double gravity = -0.04;
        double buoyancy = 0.0;
        float momentum = 0.05F;
        int status = this.boatqol$status;

        if (this.boatqol$prevStatus == STATUS_IN_AIR && status != STATUS_IN_AIR && status != STATUS_ON_LAND) {
            // Landed in water from the air: snap onto the surface.
            this.boatqol$waterLevel = this.bb.minY - HULL_DEPTH + this.bbHeight;
            double minY = this.boatqol$waterLevelAbove() - this.bbHeight + 0.101 + HULL_DEPTH;
            this.setPos(this.x, minY + this.heightOffset - this.ySlideOffset, this.z);
            this.yd = 0.0;
            this.boatqol$lastYd = 0.0;
            this.boatqol$status = STATUS_IN_WATER;
            return;
        }

        if (status == STATUS_IN_WATER) {
            buoyancy = (this.boatqol$waterLevel - (this.bb.minY - HULL_DEPTH)) / this.bbHeight;
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

        this.xd *= momentum;
        this.zd *= momentum;
        this.boatqol$deltaRotation *= momentum;
        this.yd += gravity;
        if (buoyancy > 0.0) {
            this.yd += buoyancy * 0.06153846;
            this.yd *= 0.75;
        }
        this.boatqol$lastYd = this.yd;
    }

    /** EntityBoat.getBoatStatus from 1.12 */
    @Unique
    private int boatqol$computeStatus() {
        int under = this.boatqol$underwaterStatus();
        if (under >= 0) {
            this.boatqol$waterLevel = this.bb.maxY;
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

    @Unique
    private boolean boatqol$isWater(int x, int y, int z) {
        return this.world.getBlockMaterial(x, y, z) == Materials.WATER;
    }

    /** Water surface height inside the block, 0..1 (BTA water levels are in eighths). */
    @Unique
    private float boatqol$liquidHeight(int x, int y, int z) {
        int meta = this.world.getBlockMetadata(x, y, z);
        if ((meta & 7) == 0 && this.boatqol$isWater(x, y + 1, z)) {
            return 1.0F;
        }
        return 1.0F - BlockLogicFluid.getHeight(meta);
    }

    /** EntityBoat.checkInWater from 1.12 */
    @Unique
    private boolean boatqol$checkInWater() {
        AABBd bb = this.bb;
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
                    if (this.boatqol$isWater(x, y, z)) {
                        float level = y + this.boatqol$liquidHeight(x, y, z);
                        this.boatqol$waterLevel = Math.max(level, this.boatqol$waterLevel);
                        inWater |= hullMinY < level;
                    }
                }
            }
        }
        return inWater;
    }

    /** EntityBoat.getUnderwaterStatus from 1.12; -1 when not under water. */
    @Unique
    private int boatqol$underwaterStatus() {
        AABBd bb = this.bb;
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
                    if (this.boatqol$isWater(x, y, z) && top < y + this.boatqol$liquidHeight(x, y, z)) {
                        if ((this.world.getBlockMetadata(x, y, z) & 7) != 0) {
                            return STATUS_UNDER_FLOWING_WATER;
                        }
                        under = true;
                    }
                }
            }
        }
        return under ? STATUS_UNDER_WATER : -1;
    }

    /** EntityBoat.getWaterLevelAbove from 1.12 */
    @Unique
    private float boatqol$waterLevelAbove() {
        AABBd bb = this.bb;
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
                    if (this.boatqol$isWater(x, y, z)) {
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
     * EntityBoat.getBoatGlide from 1.12: average friction of the blocks the boat
     * rests on. Ice only counts as slippery with "use modern ice slide" on.
     */
    @Unique
    private float boatqol$computeGlide() {
        AABBd bb = this.bb;
        AABBd below = new AABBd(bb.minX, bb.minY - 0.001, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        int x0 = MathHelper.floor(below.minX) - 1;
        int x1 = (int) Math.ceil(below.maxX) + 1;
        int y0 = MathHelper.floor(below.minY) - 1;
        int y1 = (int) Math.ceil(below.maxY) + 1;
        int z0 = MathHelper.floor(below.minZ) - 1;
        int z1 = (int) Math.ceil(below.maxZ) + 1;
        boolean modernIce = RemoteServer.useModernIceSlide(this.world.isClientSide);

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
                    Block<?> block = this.world.getBlock(x, y, z);
                    if (block == null) {
                        continue;
                    }
                    AABBdc shape = block.getCollisionAABB(this.world, new TilePos(x, y, z));
                    if (shape != null && boatqol$overlaps(shape, below)) {
                        float slip = block.friction;
                        if (!modernIce && slip > 0.6F) {
                            slip = 0.6F; // the vanilla BTA boat gets no extra speed from ice
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
        double speed = Math.sqrt(this.xd * this.xd + this.zd * this.zd);
        if (speed <= 0.15) {
            return;
        }
        double cos = Math.cos(this.yRot * Math.PI / 180.0);
        double sin = Math.sin(this.yRot * Math.PI / 180.0);
        for (int i = 0; i < 1.0 + speed * 60.0; i++) {
            double a = this.random.nextFloat() * 2.0F - 1.0F;
            double b = (this.random.nextInt(2) * 2 - 1) * 0.6;
            double px;
            double pz;
            if (this.random.nextBoolean()) {
                px = this.x - cos * a * 0.8 + sin * b;
                pz = this.z - sin * a * 0.8 - cos * b;
            } else {
                px = this.x + cos + sin * a * 0.7;
                pz = this.z + sin - cos * a * 0.7;
            }
            this.world.spawnParticle("splash", px, this.y - 0.125, pz, this.xd, this.yd, this.zd, 0, false);
        }
    }

    // ---------------------------------------------------------------- multiplayer

    @Unique
    private void boatqol$sendMove() {
        this.boatqol$ticksSinceSent++;
        boolean moved = this.x != this.boatqol$sentX || this.y != this.boatqol$sentY
                || this.z != this.boatqol$sentZ || this.yRot != this.boatqol$sentYRot;
        if (!moved && this.boatqol$ticksSinceSent < 10) {
            return; // still send now and then, so the server knows we're steering
        }
        this.boatqol$ticksSinceSent = 0;
        this.boatqol$sentX = this.x;
        this.boatqol$sentY = this.y;
        this.boatqol$sentZ = this.z;
        this.boatqol$sentYRot = this.yRot;
        RemoteServer.send(BoatPayloads.move(this.id, this.x, this.y, this.z, this.yRot));
    }

    @Override
    public void boatqol$acceptClientMove(double x, double y, double z, float yRot) {
        double dx = x - this.x;
        double dy = y - this.y;
        double dz = z - this.z;
        if (dx * dx + dy * dy + dz * dz > MAX_CLIENT_MOVE_SQ) {
            BoatQOL.LOGGER.warn("Boat {} moved wrongly ({} blocks), ignoring", this.id, Math.sqrt(dx * dx + dy * dy + dz * dz));
            return;
        }
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.setPos(x, y, z);
        this.setRot(yRot, 0.0F);
        this.fallDistance = 0.0F;
        this.boatqol$lastClientMove = this.tickCount;
        if (this.passenger != null) {
            ((EntityBoat) (Object) this).positionRider();
        }
    }

    @Override
    public boolean boatqol$isSteeredHere() {
        return this.passenger != null
                && BoatQOL.localInput.get(this.passenger) != null
                && (!this.world.isClientSide || RemoteServer.hasMod());
    }

    /** Strict box overlap (touching edges don't count), like the old AABB.intersects. */
    @Unique
    private static boolean boatqol$overlaps(AABBdc a, AABBd b) {
        return a.maxX() > b.minX && a.minX() < b.maxX
                && a.maxY() > b.minY && a.minY() < b.maxY
                && a.maxZ() > b.minZ && a.minZ() < b.maxZ;
    }

    // ---------------------------------------------------------------- durability

    @Shadow private ItemStack flag;

    /**
     * Vanilla BTA drops planks and sticks when something other than a player
     * breaks the boat. Handle those hits here so it drops the boat item instead;
     * player hits (including creative one-hit breaking) stay vanilla.
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void boatqol$nonPlayerHurt(Entity attacker, int damage, DamageType type, CallbackInfoReturnable<Boolean> cir) {
        if (this.world.isClientSide || this.removed || attacker instanceof Player) {
            return;
        }
        EntityBoat self = (EntityBoat) (Object) this;
        self.boatRockDirection = -self.boatRockDirection;
        self.boatTimeSinceHit = 10;
        self.boatCurrentDamage += damage * 10;
        this.markHurt();
        if (self.boatCurrentDamage > 40) {
            if (this.passenger != null) {
                this.passenger.startRiding(self);
            }
            this.dropItem(Items.BOAT.id, 1, 0.0F);
            if (this.flag != null) {
                this.dropItem(this.flag.copy(), 0.0F);
            }
            this.remove();
        }
        cir.setReturnValue(true);
    }
}
