package net.blf02.immersivemc.client.immersive;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.blf02.immersivemc.client.config.ClientConstants;
import net.blf02.immersivemc.client.immersive.info.AbstractImmersiveInfo;
import net.blf02.immersivemc.client.immersive.info.BackpackInfo;
import net.blf02.immersivemc.client.model.BackpackModel;
import net.blf02.immersivemc.common.config.ActiveConfig;
import net.blf02.immersivemc.common.util.Util;
import net.blf02.immersivemc.common.vr.VRPlugin;
import net.blf02.immersivemc.common.vr.VRPluginVerify;
import net.blf02.vrapi.api.data.IVRData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;

import java.util.Optional;

public class BackpackImmersive extends AbstractImmersive<BackpackInfo> {

    public static final BackpackImmersive singleton = new BackpackImmersive();

    public static final BackpackModel model = new BackpackModel();
    private final double spacing = 3d/8d;

    public BackpackImmersive() {
        super(1); // A player only has one backpack
    }

    @Override
    protected void doTick(BackpackInfo info, boolean isInVR) {
        super.doTick(info, isInVR);
        int controllerNum = 1;
        IVRData backpackController = VRPlugin.API.getVRPlayer(Minecraft.getInstance().player).getController(controllerNum);
        IVRData handController = VRPlugin.API.getVRPlayer(Minecraft.getInstance().player).getController(controllerNum == 1 ? 0 : 1);
        info.handPos = backpackController.position();
        info.handPitch = (float) Math.toRadians(backpackController.getPitch());
        info.handYaw = (float) Math.toRadians(backpackController.getYaw());
        info.lookVec = backpackController.getLookAngle();

        // Render backpack closer to the player, and attached to the inner-side of the arm
        info.backVec = info.lookVec.normalize().multiply(-1, -1, -1);
        info.renderPos = info.handPos.add(0, -0.75, 0);
        info.renderPos = info.renderPos.add(info.backVec.multiply(1d/6d, 1d/6d, 1d/6d));

        info.rgb = new Vector3f(ActiveConfig.backpackColor >> 16, ActiveConfig.backpackColor >> 8 & 255,
                ActiveConfig.backpackColor & 255);
        info.rgb.mul(1f/255f);


        info.centerTopPos = info.handPos.add(0, -0.05, 0);
        info.centerTopPos = info.centerTopPos.add(info.backVec.multiply(1d/6d, 1d/6d, 1d/6d)); // Back on arm
        // Multiply massively so the < 1E-4D check from .normalize() rarely kicks in
        Vector3d rightVec = info.lookVec.multiply(1E16D, 0, 1E16D).normalize();
        if (ActiveConfig.leftHandedBackpack) {
            rightVec = new Vector3d(rightVec.z, 0, -rightVec.x).multiply(0.25, 0, 0.25);
        } else {
            rightVec = new Vector3d(-rightVec.z, 0, rightVec.x).multiply(0.25, 0, 0.25);
        }
        info.centerTopPos = info.centerTopPos.add(rightVec);


        Vector3d leftVec = rightVec.multiply(-1, 0, -1);

        // Note: rightVec and leftVec refer to the vectors for right-handed people. Swap the names if referring to
        // left-handed guys, gals, and non-binary pals.

        Vector3f downVecF = new Vector3f(0, -1, 0);
        downVecF.transform(Vector3f.XN.rotation(info.handPitch));
        downVecF.transform(Vector3f.YN.rotation(info.handYaw));
        info.downVec = new Vector3d(downVecF.x(), downVecF.y(), downVecF.z()).normalize();

        // Item hitboxes and positions
        Vector3d leftOffset = new Vector3d(
                leftVec.x * spacing, leftVec.y * spacing, leftVec.z * spacing);
        Vector3d rightOffset = new Vector3d(
                rightVec.x * spacing, rightVec.y * spacing, rightVec.z * spacing);

        double tbSpacing = spacing / 4d;
        Vector3d topOffset = info.lookVec.multiply(tbSpacing, tbSpacing, tbSpacing);
        Vector3d botOffset = info.backVec.multiply(tbSpacing, tbSpacing, tbSpacing);

        Vector3d pos = info.centerTopPos;
        Vector3d[] positions = new Vector3d[]{
                pos.add(leftOffset).add(topOffset), pos.add(topOffset), pos.add(rightOffset).add(topOffset),
                pos.add(leftOffset), pos, pos.add(rightOffset),
                pos.add(leftOffset).add(botOffset), pos.add(botOffset), pos.add(rightOffset).add(botOffset)};

        int start = 9 * info.topRow;
        int end = start + 8;
        int midStart = 9 * info.getMidRow();
        int midEnd = midStart + 8;

        Vector3d downOne = info.downVec.multiply(0.125, 0.125, 0.125);
        Vector3d downTwo = downOne.multiply(2, 2, 2);

        for (int i = 0; i <= 26; i++) {
            Vector3d posRaw = positions[i % 9];
            Vector3d yDown = inRange(i, start, end) ? Vector3d.ZERO :
                    inRange(i, midStart, midEnd) ? downOne : downTwo;
            Vector3d slotPos = posRaw;
            slotPos = slotPos.add(yDown);
            info.setPosition(i, slotPos);
            info.setHitbox(i, createHitbox(info.getPosition(i), 0.05f));
        }
        Optional<Integer> hitboxIntersect = Util.getFirstIntersect(handController.position(),
                info.getAllHitboxes());
        if (hitboxIntersect.isPresent()) {
            info.slotHovered = hitboxIntersect.get();
        } else {
            info.slotHovered = -1;
        }
    }

    protected boolean inRange(int num, int start, int end) {
        return start <= num && num <= end;
    }

    @Override
    public boolean shouldRender(BackpackInfo info, boolean isInVR) {
        return Minecraft.getInstance().player != null &&
                VRPluginVerify.hasAPI && VRPlugin.API.playerInVR(Minecraft.getInstance().player) &&
                VRPlugin.API.apiActive(Minecraft.getInstance().player) &&
                info.readyToRender();
    }

    @Override
    protected void render(BackpackInfo info, MatrixStack stack, boolean isInVR) {
        for (int i = 0; i <= 26; i++) {
            AxisAlignedBB hitbox = info.getHibtox(i);
            renderHitbox(stack, hitbox, info.getPosition(i));
        }

        for (int i = 0; i <= 26; i++) {
            ItemStack item = Minecraft.getInstance().player.inventory.getItem(i + 9);
            if (!item.isEmpty() && info.getPosition(i) != null) {
                final float size =
                        info.slotHovered == i ? ClientConstants.itemScaleSizeBackpackSelected : ClientConstants.itemScaleSizeBackpack;
                renderItem(item, stack, info.getPosition(i), size, null, info.getHibtox(i), true);
            }
        }

        stack.pushPose();
        Vector3d pos = info.renderPos;

        ActiveRenderInfo renderInfo = Minecraft.getInstance().gameRenderer.getMainCamera();
        stack.translate(-renderInfo.getPosition().x + pos.x,
                -renderInfo.getPosition().y + pos.y,
                -renderInfo.getPosition().z + pos.z);
        stack.scale(0.5f, 0.5f, 0.5f);

        stack.translate(0, 1.5, 0); // Translate origin to our hand

        stack.mulPose(Vector3f.YN.rotation(info.handYaw));
        stack.mulPose(Vector3f.XN.rotation(info.handPitch));
        stack.mulPose(Vector3f.ZP.rotation((float) Math.PI)); // Rotate

        stack.translate(0, -1.5, 0); // Move back to where we started

        // Basically move the model to the side of the origin
        stack.translate(ActiveConfig.leftHandedBackpack ? -0.5 : 0.5, 0, 0);

        // Render the model (finally!)
        model.renderToBuffer(stack,
                Minecraft.getInstance().renderBuffers().bufferSource()
                        .getBuffer(RenderType.entityCutout(BackpackModel.textureLocation)),
                15728880, OverlayTexture.NO_OVERLAY,
                info.rgb.x(), info.rgb.y(), info.rgb.z(), 1);

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        stack.popPose();
    }

    @Override
    protected boolean enabledInConfig() {
        return ActiveConfig.useBackpack;
    }

    @Override
    public void handleRightClick(AbstractImmersiveInfo info, PlayerEntity player, int closest, Hand hand) {}

    public void doTrack() {
        if (this.infos.isEmpty()) {
            this.infos.add(new BackpackInfo());
        } else {
            this.infos.clear();
        }
    }
}