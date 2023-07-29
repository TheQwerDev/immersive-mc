package com.hammy275.immersivemc.client.subscribe;

import com.hammy275.immersivemc.client.immersive_item.AbstractItemImmersive;
import com.hammy275.immersivemc.client.immersive_item.ItemImmersives;
import com.hammy275.immersivemc.common.util.ShieldUtil;
import com.hammy275.immersivemc.common.vr.VRPlugin;
import com.hammy275.immersivemc.common.vr.VRPluginVerify;
import com.mojang.blaze3d.vertex.PoseStack;
import com.hammy275.immersivemc.client.immersive.AbstractImmersive;
import com.hammy275.immersivemc.client.immersive.Immersives;
import com.hammy275.immersivemc.client.immersive.info.AbstractImmersiveInfo;
import net.blf02.vrapi.api.data.IVRData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.UseAnim;

import java.util.ConcurrentModificationException;

public class ClientRenderSubscriber {

    public static void onWorldRender(PoseStack stack) {
        try {
            for (AbstractImmersive<? extends AbstractImmersiveInfo> singleton : Immersives.IMMERSIVES) {
                renderInfos(singleton, stack);
            }
            if (VRPluginVerify.clientInVR()) {
                for (AbstractItemImmersive<?> singleton : ItemImmersives.ITEM_IMMERSIVES) {
                    singleton.renderAll(stack);
                }
            }
        } catch (ConcurrentModificationException ignored) {
            // Skip rendering if the list is modified mid-render
            // It's fine, since we were only going to read it anyway!!
        }

        // Draw shield hitbox(es)
        if (VRPluginVerify.clientInVR()) {
            for (InteractionHand iHand : InteractionHand.values()) {
                if (Minecraft.getInstance().player.getItemInHand(iHand).getUseAnimation() == UseAnim.BLOCK) {
                    IVRData hand = VRPlugin.API.getVRPlayer(Minecraft.getInstance().player).getController(iHand.ordinal());
                    AbstractImmersive.renderHitbox(stack, ShieldUtil.getShieldHitbox(Minecraft.getInstance().player, hand, iHand),
                            hand.position(), false, 1, 1, 1);
                }
            }
        }
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    protected static <I extends AbstractImmersiveInfo> void renderInfos(AbstractImmersive<I> singleton,
                                                                 PoseStack stack) {
        try {
            if (singleton.isVROnly() && !VRPluginVerify.clientInVR()) {
                return;
            }
            for (I info : singleton.getTrackedObjects()) {
                singleton.doRender(info, stack, VRPluginVerify.clientInVR());
            }
        } catch (ConcurrentModificationException ignored) {
            // Skip rendering if the list is modified mid-render
            // It's fine, since we were only going to read it anyway!!
        }
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(); // Write all our buffers!

    }

}
