package io.github.davevermeulen.boatqol;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.github.davevermeulen.boatqol.net.MoveMessage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * The 1.9-1.12 boat (see docs/SPEC.md), on top of the 1.7.10 boat. It keeps the
 * vanilla model, renderer, hitbox, damage and item drop; only the ticking changes.
 *
 * Yaw convention: the 1.7.10 boat's bow points along -(cos yaw, sin yaw) and the
 * rider sits 0.4 blocks toward +(cos yaw, sin yaw), at the stern. That convention
 * is kept so the vanilla renderer and seat line up, and the 1.12 formulas are
 * rotated to match: a rider facing the bow has yaw = boat yaw + 90.
 *
 * Float height: the 1.12 buoyancy sinks the 1.12 hull about 0.39 blocks, but the
 * 1.7.10 boat sits with its bottom at the water surface. All water checks
 * therefore use a "hull" {@link #HULL_DEPTH} below the hitbox, so the boat
 * floats at the same height as the vanilla boat.
 *
 * Multiplayer: the rider's client simulates the boat and sends its position
 * ({@link MoveMessage}); the server accepts it and passes it on to everyone else.
 * If the client goes quiet the server simulates the boat itself.
 */
public class ModernBoat extends EntityBoat {
    private static final int STATUS_IN_WATER = 0;
    private static final int STATUS_UNDER_WATER = 1;
    private static final int STATUS_UNDER_FLOWING_WATER = 2;
    private static final int STATUS_ON_LAND = 3;
    private static final int STATUS_IN_AIR = 4;

    /** Server: ticks without a move from the rider's client before the server takes over. */
    private static final int CLIENT_MOVE_TIMEOUT = 60;
    /** Server: largest move per message accepted from a client, squared (10 blocks). */
    private static final double MAX_CLIENT_MOVE_SQ = 100.0;

    /** How far the 1.12 hull reaches below the 1.7.10 hitbox (see class comment). */
    private static final double HULL_DEPTH = 0.385;

    private float deltaRotation;
    private int status = STATUS_IN_AIR;
    private int prevStatus = STATUS_IN_AIR;
    private double waterLevel;
    private float glide;
    private double lastYd;

    /** Server: age at which the rider's client last reported a move. */
    private int lastClientMove = Integer.MIN_VALUE / 2;

    /** Client: whether this boat was steered by us last tick. */
    private boolean locallyControlled;
    private int ticksSinceSent;
    private double sentX;
    private double sentY;
    private double sentZ;
    private float sentYaw;

    /** Client: where the server says the boat is, for boats we don't steer. */
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;

    /** Rider: the tick the rider last turned with the boat. */
    private int riderFollowedAt = -1;

    public ModernBoat(World world) {
        super(world);
    }

    public ModernBoat(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void onUpdate() {
        Entity rider = this.riddenByEntity;
        float[] input = rider == null ? null : BoatQOL.proxy.localInput(rider);

        if (this.worldObj.isRemote) {
            if (input != null) {
                this.locallyControlled = true;
                this.modernTick(input);
                this.sendMove();
            } else {
                if (this.locallyControlled) {
                    // We just got out: hand the boat back to the server's updates.
                    this.locallyControlled = false;
                    this.lerpSteps = 0;
                    this.motionX = this.motionY = this.motionZ = 0.0;
                }
                this.remoteTick();
            }
            return;
        }

        // Server, or singleplayer.
        if (rider instanceof EntityPlayer && this.ticksExisted - this.lastClientMove <= CLIENT_MOVE_TIMEOUT) {
            // The rider's client drives this boat; positions come in by message.
            this.passiveTick();
            return;
        }
        if (input == null && rider instanceof EntityLivingBase) {
            // Steer with what the rider's client reports as movement input.
            EntityLivingBase living = (EntityLivingBase) rider;
            input = new float[] {living.moveForward, living.moveStrafing};
        }
        this.modernTick(input);
    }

    private void tickWobble() {
        if (this.getTimeSinceHit() > 0) {
            this.setTimeSinceHit(this.getTimeSinceHit() - 1);
        }
        if (this.getDamageTaken() > 0.0F) {
            this.setDamageTaken(this.getDamageTaken() - 1.0F);
        }
    }

    private void passiveTick() {
        this.onEntityUpdate();
        this.tickWobble();
        this.fallDistance = 0.0F;
        if (this.riddenByEntity != null && this.riddenByEntity.isDead) {
            this.riddenByEntity = null;
        }
    }

    /** Client, a boat steered by someone else (or nobody): follow the server. */
    private void remoteTick() {
        this.onEntityUpdate();
        this.tickWobble();
        if (this.lerpSteps > 0) {
            double x = this.posX + (this.lerpX - this.posX) / this.lerpSteps;
            double y = this.posY + (this.lerpY - this.posY) / this.lerpSteps;
            double z = this.posZ + (this.lerpZ - this.posZ) / this.lerpSteps;
            double dYaw = MathHelper.wrapAngleTo180_double(this.lerpYaw - this.rotationYaw);
            this.rotationYaw = (float) (this.rotationYaw + dYaw / this.lerpSteps);
            this.lerpSteps--;
            this.setPosition(x, y, z);
            this.setRotation(this.rotationYaw, 0.0F);
        }
        this.prevStatus = this.status;
        this.status = this.computeStatus();
        double mx = this.posX - this.prevPosX;
        double mz = this.posZ - this.prevPosZ;
        this.splash(mx, this.posY - this.prevPosY, mz);
    }

    private void modernTick(float[] input) {
        this.onEntityUpdate(); // prev position/rotation, fire, water state
        this.tickWobble();

        this.prevStatus = this.status;
        this.status = this.computeStatus();

        this.updateMotion();
        if (input != null) {
            this.control(input[0], input[1]);
        }
        this.moveEntity(this.motionX, this.motionY, this.motionZ);
        this.fallDistance = 0.0F; // never takes fall damage, never breaks on landing
        this.setRotation(this.rotationYaw, 0.0F);

        this.splash(this.motionX, this.motionY, this.motionZ);

        if (!this.worldObj.isRemote) {
            this.breakSoftBlocks();
        }

        List<?> nearby = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, this.boundingBox.expand(0.2, 0.0, 0.2));
        if (nearby != null) {
            for (Object o : nearby) {
                Entity e = (Entity) o;
                if (e != this.riddenByEntity && e.canBePushed() && e instanceof EntityBoat) {
                    e.applyEntityCollision(this);
                }
            }
        }

        if (this.riddenByEntity != null && this.riddenByEntity.isDead) {
            this.riddenByEntity = null;
        }
    }

    /** Like vanilla: driving through snow layers and lily pads breaks them. */
    private void breakSoftBlocks() {
        for (int corner = 0; corner < 4; corner++) {
            int x = MathHelper.floor_double(this.posX + (corner % 2 - 0.5) * 0.8);
            int z = MathHelper.floor_double(this.posZ + (corner / 2 - 0.5) * 0.8);
            for (int dy = 0; dy < 2; dy++) {
                int y = MathHelper.floor_double(this.posY) + dy;
                Block block = this.worldObj.getBlock(x, y, z);
                if (block == Blocks.snow_layer) {
                    this.worldObj.setBlockToAir(x, y, z);
                } else if (block == Blocks.waterlily) {
                    this.worldObj.func_147480_a(x, y, z, true);
                }
            }
        }
    }

    /** Never breaks or takes damage from falling. */
    @Override
    protected void updateFallState(double distanceFallenThisTick, boolean onGround) {
        this.fallDistance = 0.0F;
    }

    // ---------------------------------------------------------------- 1.12 physics

    /** EntityBoat.controlBoat */
    private void control(float forwardInput, float sidewaysInput) {
        boolean forward = forwardInput > 0.0F;
        boolean back = forwardInput < 0.0F;
        boolean left = sidewaysInput > 0.0F;
        boolean right = sidewaysInput < 0.0F;

        float f = 0.0F;
        if (left) {
            this.deltaRotation -= 1.0F;
        }
        if (right) {
            this.deltaRotation += 1.0F;
        }
        if (right != left && !forward && !back) {
            f += 0.005F;
        }
        this.rotationYaw += this.deltaRotation;
        if (forward) {
            f += 0.04F;
        }
        if (back) {
            f -= 0.005F;
        }
        // The bow is -(cos yaw, sin yaw) in the old boat's convention.
        double rad = this.rotationYaw * Math.PI / 180.0;
        this.motionX -= Math.cos(rad) * f;
        this.motionZ -= Math.sin(rad) * f;
    }

    /** EntityBoat.updateMotion */
    private void updateMotion() {
        double gravity = -0.04;
        double buoyancy = 0.0;
        float momentum = 0.05F;
        int status = this.status;

        if (this.prevStatus == STATUS_IN_AIR && status != STATUS_IN_AIR && status != STATUS_ON_LAND) {
            // Landed in water from the air: snap onto the surface.
            this.waterLevel = this.boundingBox.minY - HULL_DEPTH + this.height;
            double minY = this.waterLevelAbove() - this.height + 0.101 + HULL_DEPTH;
            this.setPosition(this.posX, minY + this.yOffset - this.ySize, this.posZ);
            this.motionY = 0.0;
            this.lastYd = 0.0;
            this.status = STATUS_IN_WATER;
            return;
        }

        if (status == STATUS_IN_WATER) {
            buoyancy = (this.waterLevel - (this.boundingBox.minY - HULL_DEPTH)) / this.height;
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
            momentum = this.glide;
        }

        this.motionX *= momentum;
        this.motionZ *= momentum;
        this.deltaRotation *= momentum;
        this.motionY += gravity;
        if (buoyancy > 0.0) {
            this.motionY += buoyancy * 0.06153846;
            this.motionY *= 0.75;
        }
        this.lastYd = this.motionY;
    }

    /** EntityBoat.getBoatStatus */
    private int computeStatus() {
        int under = this.underwaterStatus();
        if (under >= 0) {
            this.waterLevel = this.boundingBox.maxY;
            return under;
        }
        if (this.checkInWater()) {
            return STATUS_IN_WATER;
        }
        float glide = this.computeGlide();
        if (glide > 0.0F) {
            this.glide = glide;
            return STATUS_ON_LAND;
        }
        return STATUS_IN_AIR;
    }

    private boolean isWater(int x, int y, int z) {
        return this.worldObj.getBlock(x, y, z).getMaterial() == Material.water;
    }

    /** BlockLiquid.getLiquidHeight: water surface height inside the block, 0..1. */
    private float liquidHeight(int x, int y, int z) {
        int meta = this.worldObj.getBlockMetadata(x, y, z);
        if ((meta & 7) == 0 && this.isWater(x, y + 1, z)) {
            return 1.0F;
        }
        return 1.0F - BlockLiquid.getLiquidHeightPercent(meta);
    }

    /** EntityBoat.checkInWater */
    private boolean checkInWater() {
        AxisAlignedBB bb = this.boundingBox;
        double hullMinY = bb.minY - HULL_DEPTH;
        int x0 = MathHelper.floor_double(bb.minX);
        int x1 = MathHelper.ceiling_double_int(bb.maxX);
        int y0 = MathHelper.floor_double(hullMinY);
        int y1 = MathHelper.ceiling_double_int(hullMinY + 0.001);
        int z0 = MathHelper.floor_double(bb.minZ);
        int z1 = MathHelper.ceiling_double_int(bb.maxZ);
        boolean inWater = false;
        this.waterLevel = -Double.MAX_VALUE;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (this.isWater(x, y, z)) {
                        float level = y + this.liquidHeight(x, y, z);
                        this.waterLevel = Math.max(level, this.waterLevel);
                        inWater |= hullMinY < level;
                    }
                }
            }
        }
        return inWater;
    }

    /** EntityBoat.getUnderwaterStatus; -1 when not under water. */
    private int underwaterStatus() {
        AxisAlignedBB bb = this.boundingBox;
        double top = bb.maxY + 0.001;
        int x0 = MathHelper.floor_double(bb.minX);
        int x1 = MathHelper.ceiling_double_int(bb.maxX);
        int y0 = MathHelper.floor_double(bb.maxY);
        int y1 = MathHelper.ceiling_double_int(top);
        int z0 = MathHelper.floor_double(bb.minZ);
        int z1 = MathHelper.ceiling_double_int(bb.maxZ);
        boolean under = false;
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (this.isWater(x, y, z) && top < y + this.liquidHeight(x, y, z)) {
                        if (this.worldObj.getBlockMetadata(x, y, z) != 0) {
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
    private float waterLevelAbove() {
        AxisAlignedBB bb = this.boundingBox;
        int x0 = MathHelper.floor_double(bb.minX);
        int x1 = MathHelper.ceiling_double_int(bb.maxX);
        double hullMaxY = bb.maxY - HULL_DEPTH;
        int y0 = MathHelper.floor_double(hullMaxY);
        int y1 = MathHelper.ceiling_double_int(hullMaxY - this.lastYd);
        int z0 = MathHelper.floor_double(bb.minZ);
        int z1 = MathHelper.ceiling_double_int(bb.maxZ);
        layers:
        for (int y = y0; y < y1; y++) {
            float level = 0.0F;
            for (int x = x0; x < x1; x++) {
                for (int z = z0; z < z1; z++) {
                    if (this.isWater(x, y, z)) {
                        level = Math.max(level, this.liquidHeight(x, y, z));
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
    private float computeGlide() {
        AxisAlignedBB bb = this.boundingBox;
        AxisAlignedBB below = AxisAlignedBB.getBoundingBox(bb.minX, bb.minY - 0.001, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        int x0 = MathHelper.floor_double(below.minX) - 1;
        int x1 = MathHelper.ceiling_double_int(below.maxX) + 1;
        int y0 = MathHelper.floor_double(below.minY) - 1;
        int y1 = MathHelper.ceiling_double_int(below.maxY) + 1;
        int z0 = MathHelper.floor_double(below.minZ) - 1;
        int z1 = MathHelper.ceiling_double_int(below.maxZ) + 1;
        boolean modernIce = BoatQOLConfig.useModernIceSlide(this.worldObj.isRemote);

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
                    Block block = this.worldObj.getBlock(x, y, z);
                    if (block.getMaterial() == Material.air) {
                        continue;
                    }
                    AxisAlignedBB shape = block.getCollisionBoundingBoxFromPool(this.worldObj, x, y, z);
                    if (shape != null && shape.intersectsWith(below)) {
                        float slip = block.slipperiness;
                        if (!modernIce && slip > 0.6F) {
                            slip = 0.6F; // 1.7.10 boats get no extra speed from ice
                        }
                        total += slip;
                        count++;
                    }
                }
            }
        }
        return count == 0 ? 0.0F : total / count;
    }

    /** The old boat's bow splash. */
    private void splash(double mx, double my, double mz) {
        if (!this.worldObj.isRemote || this.status != STATUS_IN_WATER) {
            return;
        }
        double speed = Math.sqrt(mx * mx + mz * mz);
        if (speed <= 0.15) {
            return;
        }
        double cos = Math.cos(this.rotationYaw * Math.PI / 180.0);
        double sin = Math.sin(this.rotationYaw * Math.PI / 180.0);
        for (int i = 0; i < 1.0 + speed * 60.0; i++) {
            double a = this.rand.nextFloat() * 2.0F - 1.0F;
            double b = (this.rand.nextInt(2) * 2 - 1) * 0.7;
            double px;
            double pz;
            if (this.rand.nextBoolean()) {
                px = this.posX - cos * a * 0.8 + sin * b;
                pz = this.posZ - sin * a * 0.8 - cos * b;
            } else {
                px = this.posX + cos + sin * a * 0.7;
                pz = this.posZ + sin - cos * a * 0.7;
            }
            this.worldObj.spawnParticle("splash", px, this.posY - 0.125, pz, mx, my, mz);
        }
    }

    // ---------------------------------------------------------------- riding

    /**
     * Seats the rider as vanilla does. The rider of a boat steered here turns
     * exactly with the boat and can look at most 105 degrees away from the bow.
     */
    @Override
    public void updateRiderPosition() {
        super.updateRiderPosition();
        Entity rider = this.riddenByEntity;
        if (rider != null && this.worldObj.isRemote && BoatQOL.proxy.localInput(rider) != null
                && this.riderFollowedAt != this.ticksExisted) {
            this.riderFollowedAt = this.ticksExisted;
            rider.rotationYaw += this.rotationYaw - this.prevRotationYaw;
            BoatRiding.clampLook(rider, this);
        }
    }

    /** Sneaking players can't get in (they would fall straight out again). */
    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (player.isSneaking() && player.ridingEntity != this) {
            return true;
        }
        return super.interactFirst(player);
    }

    // ---------------------------------------------------------------- multiplayer

    /** Client: the server's position updates, ignored while we steer this boat. */
    @Override
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int increments) {
        if (this.locallyControlled && this.riddenByEntity != null) {
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpSteps = increments + 2;
    }

    /** Client: the server's velocity; only used for the splash, so just keep it. */
    @Override
    @SideOnly(Side.CLIENT)
    public void setVelocity(double x, double y, double z) {
        if (this.locallyControlled && this.riddenByEntity != null) {
            return;
        }
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
    }

    private void sendMove() {
        this.ticksSinceSent++;
        boolean moved = this.posX != this.sentX || this.posY != this.sentY
                || this.posZ != this.sentZ || this.rotationYaw != this.sentYaw;
        if (!moved && this.ticksSinceSent < 10) {
            return; // still send now and then, so the server knows we're steering
        }
        this.ticksSinceSent = 0;
        this.sentX = this.posX;
        this.sentY = this.posY;
        this.sentZ = this.posZ;
        this.sentYaw = this.rotationYaw;
        BoatQOL.network.sendToServer(new MoveMessage(this.getEntityId(), this.posX, this.posY, this.posZ, this.rotationYaw));
    }

    /** Server: a move reported by the rider's client. */
    public void acceptClientMove(double x, double y, double z, float yaw) {
        double dx = x - this.posX;
        double dy = y - this.posY;
        double dz = z - this.posZ;
        if (dx * dx + dy * dy + dz * dz > MAX_CLIENT_MOVE_SQ) {
            BoatQOL.LOGGER.warn("Boat {} moved wrongly ({} blocks), ignoring",
                    this.getEntityId(), Math.sqrt(dx * dx + dy * dy + dz * dz));
            return;
        }
        this.motionX = dx;
        this.motionY = dy;
        this.motionZ = dz;
        this.setPosition(x, y, z);
        this.setRotation(yaw, 0.0F);
        this.fallDistance = 0.0F;
        this.lastClientMove = this.ticksExisted;
        if (this.riddenByEntity != null) {
            this.updateRiderPosition();
        }
    }

    /** Client: whether this game instance steers the boat (for the look limit). */
    public boolean isSteeredHere() {
        return this.riddenByEntity != null && BoatQOL.proxy.localInput(this.riddenByEntity) != null;
    }
}
