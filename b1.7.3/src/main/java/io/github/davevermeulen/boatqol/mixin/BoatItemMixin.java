package io.github.davevermeulen.boatqol.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.BoatItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A placed boat faces the way the player is looking, like 1.9+. */
@Mixin(BoatItem.class)
public abstract class BoatItemMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private boolean boatqol$faceLookDirection(World world, Entity entity, ItemStack stack, World useWorld, PlayerEntity user) {
        if (entity instanceof BoatEntity) {
            // The old boat's bow points along -(cos yaw, sin yaw): bow toward the
            // player's look means boat yaw = player yaw - 90.
            entity.yaw = entity.prevYaw = user.yaw - 90.0F;
        }
        return world.spawnEntity(entity);
    }
}
