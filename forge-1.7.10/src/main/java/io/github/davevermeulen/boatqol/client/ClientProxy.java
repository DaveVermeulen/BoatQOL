package io.github.davevermeulen.boatqol.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import io.github.davevermeulen.boatqol.CommonProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;

public class ClientProxy extends CommonProxy {
    private volatile boolean remoteModernIce;

    @Override
    public void init() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.mouseHelper = new LimitedMouseHelper(mc);
        FMLCommonHandler.instance().bus().register(this);
    }

    @Override
    public float[] localInput(Entity rider) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (rider != player || player == null || !rider.worldObj.isRemote) {
            return null;
        }
        return new float[] {player.movementInput.moveForward, player.movementInput.moveStrafe};
    }

    @Override
    public boolean remoteModernIce() {
        return this.remoteModernIce;
    }

    @Override
    public void setRemoteModernIce(boolean value) {
        this.remoteModernIce = value;
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        this.remoteModernIce = false;
    }
}
