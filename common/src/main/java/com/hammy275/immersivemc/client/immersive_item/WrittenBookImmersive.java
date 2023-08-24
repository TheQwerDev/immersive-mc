package com.hammy275.immersivemc.client.immersive_item;

import com.hammy275.immersivemc.ImmersiveMC;
import com.hammy275.immersivemc.client.immersive.AbstractImmersive;
import com.hammy275.immersivemc.client.immersive_item.info.WrittenBookInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.blf02.vrapi.api.data.IVRData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class WrittenBookImmersive extends AbstractItemImmersive<WrittenBookInfo> {
    /*
        Notes:
        - Book is 14 lines long, with 20 characters per line
        - Page turning is done using a central hitbox and edge hitboxes. Hand moves from edge to center to confirm turn.
        If it doesn't make it to center, cancel the turn.
     */

    public static final BookModel bookModel = new BookModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BOOK));
    public static final ResourceLocation writtenBookTexture = new ResourceLocation(ImmersiveMC.MOD_ID, "written_book.png");

    // User Controlled
    public static final float scaleSize = 1f;

    // Derived from user controlled
    public static final double singlePageWidth = scaleSize * 0.3d;
    public static final double pageHalfHeight = scaleSize / 4d;
    public static final float textStackScaleSize = -scaleSize * 0.0025f;
    public static final double textUpAmount = 0.1875 * (scaleSize / 2f);
    public static final double pageTurnStartDistanceSqr = pageHalfHeight * pageHalfHeight;
    public static final double pageDontStartTurnDistanceSqr = 2.2 * 2.2 * singlePageWidth * singlePageWidth;
    public static final double pageTurningPointMaxDistanceSqr = (singlePageWidth * singlePageWidth +
                                                                pageHalfHeight * pageHalfHeight +
                                                                textUpAmount * textUpAmount)
                                                                * 1.1;

    // Helpful constants
    public static final double moveScaleForCenter = 0.15;
    public static final float pageTilt = 11f;
    public static final int bookLines = 14;
    public static final int charsPerLine = 20;

    /*
     * Pitch is 0 forward, with 30 up and -30 down
     * Yaw is just point direction
     */

    @Override
    protected void render(WrittenBookInfo info, PoseStack stack, IVRData hand) {
        stack.pushPose();

        Vec3 pos = hand.position();
        Camera cameraInfo = Minecraft.getInstance().gameRenderer.getMainCamera();
        stack.translate(-cameraInfo.getPosition().x + pos.x,
                -cameraInfo.getPosition().y + pos.y,
                -cameraInfo.getPosition().z + pos.z);

        stack.scale(scaleSize, scaleSize, scaleSize);

        stack.mulPose(Vector3f.YN.rotationDegrees(hand.getYaw() + 90f));
        stack.mulPose(Vector3f.ZP.rotationDegrees(90f));
        stack.mulPose(Vector3f.ZP.rotationDegrees(hand.getPitch()));

        float bookOpenAmount = 1.1f;

        bookModel.setupAnim(
                0, // Partial tick time is always 0 to have page stay in one constant spot
                info.leftPageTurn, // 0-1. How far the page is in the turn. Range is [0f, 1f] with 0f being left.
                info.rightPageTurn, // 0-1. How far across a different page is. Range is [0f, 1f] with 0f being left.
                bookOpenAmount // How open the book is. A good range seems to be (0f,1.2f]
        );
        bookModel.render(stack,
                Minecraft.getInstance().renderBuffers().bufferSource()
                        .getBuffer(RenderType.entitySolid(writtenBookTexture)),
                15728880, OverlayTexture.NO_OVERLAY,
                1, 1, 1, 1);

        stack.popPose();

        if (info.pageChangeState == WrittenBookInfo.PageChangeState.NONE) {
            renderPage(stack, hand, info.left, true);
            renderPage(stack, hand, info.right, false);
        }

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    protected void renderPage(PoseStack stack, IVRData hand, FormattedText textRaw, boolean leftPage) {
        stack.pushPose();

        Vec3 awayFromBookUp = getAway(hand);

        Vec3 pageUp = hand.getLookAngle();
        Vec3 left = getLeftRight(hand, leftPage); // Should be called "right" for right page
        Vec3 pos = hand.position().add(pageUp.scale(pageHalfHeight)).add(left.scale(singlePageWidth / 2d))
                .add(awayFromBookUp.scale(textUpAmount));

        Camera cameraInfo = Minecraft.getInstance().gameRenderer.getMainCamera();
        stack.translate(-cameraInfo.getPosition().x + pos.x,
                -cameraInfo.getPosition().y + pos.y,
                -cameraInfo.getPosition().z + pos.z);
        stack.mulPose(Vector3f.YN.rotationDegrees(hand.getYaw() + 90f));
        stack.mulPose(Vector3f.ZP.rotationDegrees(hand.getPitch()));
        stack.mulPose(Vector3f.XP.rotationDegrees(90f + (leftPage ? pageTilt : -pageTilt)));
        stack.mulPose(Vector3f.ZP.rotationDegrees(270f));
        stack.scale(textStackScaleSize, textStackScaleSize, textStackScaleSize);

        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> text = font.split(textRaw, 114);
        int lineNum = 0;
        for (FormattedCharSequence seq : text) {
            // -56f is used to make the text left-aligned.
            font.drawInBatch(seq, -56f, 32 + lineNum++ * 9, 0xFF000000, false,
                    stack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(),
                    false, 0, AbstractImmersive.maxLight);
        }

        stack.popPose();
    }

    @Override
    protected void tick(WrittenBookInfo info, IVRData hand, IVRData other) {
        ItemStack book = info.item;
        if (info.pageChanged()) {
            BookViewScreen.WrittenBookAccess access = new BookViewScreen.WrittenBookAccess(book);
            info.left = access.getPage(info.getLeftPageIndex());
            info.right = access.getPage(info.getRightPageIndex());
            info.setPageChanged(false);
        }


        Vec3 up = hand.getLookAngle();
        Vec3 down = up.scale(-1);
        Vec3 left = getLeftRight(hand, true);
        Vec3 right = getLeftRight(hand, false);
        Vec3 away = getAway(hand);

        info.distancePoints[0] = hand.position().add(left.scale(singlePageWidth * 1.1)).add(away.scale(textUpAmount));
        info.distancePoints[1] = hand.position().add(right.scale(singlePageWidth * 1.1)).add(away.scale(textUpAmount));
        info.distancePoints[2] = hand.position().add(away.scale(textUpAmount));
        for (int i = 0; i <= 2; i++) {
            info.distancePoints[i + 3] = info.distancePoints[i].add(away.scale(singlePageWidth));
        }

        if (info.pageChangeState == WrittenBookInfo.PageChangeState.NONE) {
            if (possiblyBeginPageTurn(other.position(), info.distancePoints[0], info.distancePoints[1]) && !info.onFirstPage()) {
                info.pageChangeState = WrittenBookInfo.PageChangeState.LEFT_TO_RIGHT;
            } else if (possiblyBeginPageTurn(other.position(), info.distancePoints[1], info.distancePoints[0]) && !info.onLastPage()) {
                info.pageChangeState = WrittenBookInfo.PageChangeState.RIGHT_TO_LEFT;
            }
        } else if (!info.pageChangeState.isAnim) {
            // Make sure we're nearby at least one point
            double smallestDistanceSqr = Double.POSITIVE_INFINITY;
            for (Vec3 pos : info.distancePoints) {
                smallestDistanceSqr = Math.min(pos.distanceToSqr(other.position()), smallestDistanceSqr);
            }
            if (smallestDistanceSqr > pageTurnStartDistanceSqr) {
                resetTurnState(info);
            }

            // Make sure we didn't go off the page in the opposite direction of where we're turning
            // Technically, these evaluate to the same numbers, so could reduce this down or calculate the distance
            // between the two points once
            if (info.pageChangeState == WrittenBookInfo.PageChangeState.LEFT_TO_RIGHT) {
                if (shouldCancelByGoingOffPage(other.position(), info.distancePoints[1], info.distancePoints[3])) {
                    resetTurnState(info);
                }
            } else if (info.pageChangeState == WrittenBookInfo.PageChangeState.RIGHT_TO_LEFT) {
                if (shouldCancelByGoingOffPage(other.position(), info.distancePoints[0], info.distancePoints[4])) {
                    resetTurnState(info);
                }
            }

            // Manual page turning
            if (info.pageChangeState == WrittenBookInfo.PageChangeState.LEFT_TO_RIGHT) {
                double toLeftDist = other.position().distanceTo(info.distancePoints[0]);
                double toRightDist = other.position().distanceTo(info.distancePoints[1]);
                if (toRightDist < toLeftDist) {
                    info.pageChangeState = WrittenBookInfo.PageChangeState.LEFT_TO_RIGHT_ANIM;
                    info.lastPage();
                } else {
                    info.leftPageTurn = (float) (toLeftDist / (toLeftDist + toRightDist));
                }
            } else if (info.pageChangeState == WrittenBookInfo.PageChangeState.RIGHT_TO_LEFT) {
                double toLeftDist = other.position().distanceTo(info.distancePoints[0]);
                double toRightDist = other.position().distanceTo(info.distancePoints[1]);
                if (toLeftDist < toRightDist) {
                    info.pageChangeState = WrittenBookInfo.PageChangeState.RIGHT_TO_LEFT_ANIM;
                    info.nextPage();
                } else {
                    info.rightPageTurn = 1f - ((float) (toRightDist / (toLeftDist + toRightDist)));
                }
            }
        }

        // Automatic page turning
        // Note that the next page/last page on the info is done as the animation starts, so the text is loaded
        // by the time we get to resetState here.
        if (info.pageChangeState == WrittenBookInfo.PageChangeState.LEFT_TO_RIGHT_ANIM) {
            info.leftPageTurn = Math.min(info.leftPageTurn + 0.05f, 1f);
            if (info.leftPageTurn == 1f) {
                resetTurnState(info);
            }
        } else if (info.pageChangeState == WrittenBookInfo.PageChangeState.RIGHT_TO_LEFT_ANIM) {
            info.rightPageTurn = Math.max(info.rightPageTurn - 0.05f, 0f);
            if (info.rightPageTurn == 0f) {
                resetTurnState(info);
            }
        }
    }

    @Override
    protected boolean itemMatches(ItemStack item) {
        return item.getItem() == Items.WRITTEN_BOOK;
    }

    @Override
    protected WrittenBookInfo createInfo(ItemStack item, InteractionHand hand) {
        return new WrittenBookInfo(item, hand);
    }

    private Vec3 getLeftRight(IVRData hand, boolean left) {
        Vec3 look = hand.getLookAngle();
        Vector3f leftF = new Vector3f((float) look.x(), 0, (float) look.z());
        leftF.normalize();
        leftF.transform(Vector3f.YN.rotationDegrees(left ? 270 : 90));
        return new Vec3(leftF.x(), Math.abs(leftF.y()), leftF.z());
    }

    /**
     *
     * @param hand Hand data
     * @return The vector pointing away from the book. This is the opposite of the look vector of an HMD looking
     * directly at the book.
     */
    private Vec3 getAway(IVRData hand) {
        Vector3f awayFromBookF = new Vector3f(0, 1, 0);
        awayFromBookF.transform(Vector3f.XN.rotationDegrees(hand.getPitch()));
        awayFromBookF.transform(Vector3f.YN.rotationDegrees(hand.getYaw()));
        return new Vec3(awayFromBookF.x(), awayFromBookF.y(), awayFromBookF.z());
    }

    public boolean possiblyBeginPageTurn(Vec3 handPos, Vec3 startPos, Vec3 oppositePos) {
        return handPos.distanceToSqr(startPos) < pageTurnStartDistanceSqr &&
        handPos.distanceToSqr(oppositePos) >= pageDontStartTurnDistanceSqr;
    }

    public boolean shouldCancelByGoingOffPage(Vec3 handPos, Vec3 oppositePos, Vec3 startAbovePos) {
        double oppositeHandDistanceSqr = handPos.distanceToSqr(oppositePos);
        return oppositeHandDistanceSqr > oppositePos.distanceToSqr(startAbovePos);
    }

    private void resetTurnState(WrittenBookInfo info) {
        info.leftPageTurn = 0f;
        info.rightPageTurn = 1f;
        info.pageChangeState = WrittenBookInfo.PageChangeState.NONE;
    }
}