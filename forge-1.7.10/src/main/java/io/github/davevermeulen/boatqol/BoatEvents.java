package io.github.davevermeulen.boatqol;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.davevermeulen.boatqol.net.ConfigMessage;
import io.github.davevermeulen.boatqol.net.MoveMessage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public class BoatEvents {
    /** Server: the player who last right-clicked with a boat, and when. */
    private EntityPlayer placingPlayer;
    private long placingTime;

    /** Server: vanilla boats waiting to be swapped for modern ones at the end of the tick. */
    private final List<Object[]> swaps = new ArrayList<Object[]>();

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            BoatQOL.network.sendTo(new ConfigMessage(BoatQOLConfig.useModernIceSlide), (EntityPlayerMP) event.player);
        }
    }

    /** Fired on the server right before a held boat item is used. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        World world = event.entityPlayer.worldObj;
        if (!world.isRemote && event.entityPlayer.getHeldItem() != null
                && event.entityPlayer.getHeldItem().getItem() == Items.boat) {
            this.placingPlayer = event.entityPlayer;
            this.placingTime = world.getTotalWorldTime();
        }
    }

    /**
     * Vanilla code (the boat item, dispensers) still creates vanilla boats:
     * swap each one for a modern boat. A boat placed by a player faces the way
     * the player looks, like 1.9+.
     */
    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        Entity entity = event.entity;
        if (entity.getClass() != EntityBoat.class || event.world.isRemote) {
            return;
        }
        event.setCanceled(true);
        NBTTagCompound tag = new NBTTagCompound();
        entity.writeToNBT(tag);
        float yaw = entity.rotationYaw;
        EntityPlayer player = this.placingPlayer;
        if (player != null && player.worldObj == event.world && this.placingTime == event.world.getTotalWorldTime()
                && player.getDistanceSqToEntity(entity) < 64.0) {
            // The old boat's bow points along -(cos yaw, sin yaw): bow toward the
            // player's look means boat yaw = player yaw - 90.
            yaw = player.rotationYaw - 90.0F;
        }
        // Spawning during this event can happen while a chunk's entity list is
        // being walked, so do it at the end of the tick.
        this.swaps.add(new Object[] {event.world, tag, yaw});
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Object[] move;
            while ((move = MoveMessage.PENDING.poll()) != null) {
                EntityPlayerMP player = (EntityPlayerMP) move[0];
                MoveMessage message = (MoveMessage) move[1];
                Entity boat = player.ridingEntity;
                if (boat instanceof ModernBoat && boat.getEntityId() == message.entityId && boat.riddenByEntity == player) {
                    ((ModernBoat) boat).acceptClientMove(message.x, message.y, message.z, message.yaw);
                }
            }
            return;
        }
        for (Object[] swap : this.swaps) {
            World world = (World) swap[0];
            ModernBoat boat = new ModernBoat(world);
            boat.readFromNBT((NBTTagCompound) swap[1]);
            boat.rotationYaw = boat.prevRotationYaw = (Float) swap[2];
            world.spawnEntityInWorld(boat);
        }
        this.swaps.clear();
        this.placingPlayer = null;
    }
}
