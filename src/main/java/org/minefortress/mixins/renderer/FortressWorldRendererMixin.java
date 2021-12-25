package org.minefortress.mixins.renderer;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import org.minefortress.interfaces.FortressClientWorld;
import org.minefortress.interfaces.FortressMinecraftClient;
import org.minefortress.renderer.FortressRenderLayer;
import org.minefortress.renderer.MineFortressEntityRenderer;
import org.minefortress.selections.ClickType;
import org.minefortress.selections.ClientSelection;
import org.minefortress.selections.SelectionManager;
import org.minefortress.tasks.BuildingManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Mixin(WorldRenderer.class)
public abstract class FortressWorldRendererMixin  {

    @Shadow @Final private MinecraftClient client;
    @Shadow private ClientWorld world;
    @Shadow @Final private BufferBuilderStorage bufferBuilders;

    @Shadow private static void drawShapeOutline(MatrixStack matrices, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, float g, float h, float i, float j) {}

    private MineFortressEntityRenderer entityRenderer;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(MinecraftClient client, BufferBuilderStorage bufferBuilders, CallbackInfo ci) {
        this.entityRenderer = new MineFortressEntityRenderer(client.textRenderer, ((FortressMinecraftClient) client)::getSelectionManager);
    }

    @Inject(method = "setWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;setWorld(Lnet/minecraft/world/World;)V"))
    public void setWorld(ClientWorld world, CallbackInfo ci) {
        this.entityRenderer.setLevel(world);
    }


    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/hit/BlockHitResult;getBlockPos()Lnet/minecraft/util/math/BlockPos;", shift = At.Shift.BEFORE))
    public void render(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f, CallbackInfo ci) {
        this.entityRenderer.prepare(this.world, camera);
        final Vec3d cameraPos = camera.getPos();
        final VertexConsumerProvider.Immediate multibuffersource$buffersource = this.bufferBuilders.getEntityVertexConsumers();
        this.entityRenderer.render(cameraPos.x, cameraPos.y, cameraPos.z, matrices, multibuffersource$buffersource, LightmapTextureManager.pack(15, 15));

        SelectionManager selectionManager = ((FortressMinecraftClient)client).getSelectionManager();
        Iterator<BlockPos> currentSelection = selectionManager.getCurrentSelection();
        VertexConsumer vertexconsumer2 = multibuffersource$buffersource.getBuffer(RenderLayer.getLines());
        if(currentSelection.hasNext()) {
            ClickType clickType = selectionManager.getClickType();
            while(currentSelection.hasNext()) {
                BlockPos sel = currentSelection.next();
                BlockState blockstate = this.world.getBlockState(sel);
                BlockState clickingBlockState = selectionManager.getClickingBlockState();
                if(clickType == ClickType.BUILD) {
                    if(!BuildingManager.canPlaceBlock(world, sel)) continue; // skipping all not air blocks on build
                    if(clickingBlockState != null) {
                        blockstate = clickingBlockState;
                    }
                }

                if (this.world.getWorldBorder().contains(sel)) {
                    this.drawBlockOutline(matrices, vertexconsumer2, camera.getFocusedEntity(), cameraPos.x, cameraPos.y, cameraPos.z, sel, blockstate);
                }
            }

            List<Pair<Vec3i, Vec3i>> selections = selectionManager.getSelectionSize();
            for(Pair<Vec3i, Vec3i> selectionSize : selections) {
                if(clickType == ClickType.REMOVE && selectionSize != null) {
                    VertexConsumer noDepthBuffer = multibuffersource$buffersource.getBuffer(FortressRenderLayer.getLinesNoDepth());
                    Vec3i selectionDimensions = selectionSize.getFirst();
                    VoxelShape generalSelectionBox = Block.createCuboidShape(
                            0,
                            0,
                            0,
                            selectionDimensions.getX(),
                            selectionDimensions.getY(),
                            selectionDimensions.getZ()
                    );
                    Vec3i selectionStart = selectionSize.getSecond();
                    Vector4f clickColors = selectionManager.getClickColors();
                    drawShapeOutline(matrices, noDepthBuffer, generalSelectionBox, selectionStart.getX() - cameraPos.x, selectionStart.getY() - cameraPos.y, selectionStart.getZ() -  cameraPos.z, clickColors.getX(), clickColors.getY(), clickColors.getZ(), clickColors.getW());
                }
            }

        }

        Collection<ClientSelection> allRemoveTasks = ((FortressClientWorld)world).getClientTasksHolder().getAllRemoveTasks();
        VertexConsumer buffer = multibuffersource$buffersource.getBuffer(RenderLayer.getLines());
        final Vector4f color = new Vector4f(170f/255f, 0, 0, 1f);
        for(ClientSelection task : allRemoveTasks) {
            for(BlockPos pos: task.getBlockPositions()) {
                if(BuildingManager.canRemoveBlock(world, pos)) {
                    BlockState blockState = world.getBlockState(pos);
                    this.drawBlockOutlineWithColor(matrices, buffer, camera.getFocusedEntity(), cameraPos.x, cameraPos.y, cameraPos.z, pos, blockState, color);
                }
            }
        }

        Set<BlockPos> buildTasksPos = ((FortressClientWorld)world).getClientTasksHolder().getAllBuildTasks().keySet();
        final Vector4f buildColor = new Vector4f(0, 170f/255f, 0, 1f);
        for(BlockPos pos: buildTasksPos) {
            if(BuildingManager.canPlaceBlock(world, pos)) {
                this.drawBlockOutlineWithColor(matrices, buffer, camera.getFocusedEntity(), cameraPos.x, cameraPos.y, cameraPos.z, pos, Blocks.DIRT.getDefaultState(), buildColor);
            }
        }

        if(((FortressMinecraftClient)client).isFortressGamemode()) {
            HitResult hitResult = this.client.crosshairTarget;
            if (renderBlockOutline && hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {

                BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
                BlockState blockState = this.world.getBlockState(blockPos);
                if (!blockState.isAir() && this.world.getWorldBorder().contains(blockPos)) {
                    VertexConsumer outlineVertexConsumerProvider3 = multibuffersource$buffersource.getBuffer(RenderLayer.getLines());
                    this.drawBlockOutline(matrices, outlineVertexConsumerProvider3, camera.getFocusedEntity(), cameraPos.x, cameraPos.y, cameraPos.z, blockPos, blockState);
                }
            }

            multibuffersource$buffersource.drawCurrentLayer();
        }
    }

    private void drawBlockOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity, double d, double e, double f, BlockPos blockPos, BlockState blockState) {
        final Vector4f clickColors = ((FortressMinecraftClient) client).getSelectionManager().getClickColors();
        drawShapeOutline(matrices, vertexConsumer, blockState.getOutlineShape(this.world, blockPos, ShapeContext.of(entity)), (double)blockPos.getX() - d, (double)blockPos.getY() - e, (double)blockPos.getZ() - f, clickColors.getX(), clickColors.getY(), clickColors.getZ(), clickColors.getW());
    }

    private void drawBlockOutlineWithColor(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity, double d, double e, double f, BlockPos blockPos, BlockState blockState, Vector4f color) {
        drawShapeOutline(matrices, vertexConsumer, blockState.getOutlineShape(this.world, blockPos, ShapeContext.of(entity)), (double)blockPos.getX() - d, (double)blockPos.getY() - e, (double)blockPos.getZ() - f, color.getX(), color.getY(), color.getZ(), color.getW());
    }

}