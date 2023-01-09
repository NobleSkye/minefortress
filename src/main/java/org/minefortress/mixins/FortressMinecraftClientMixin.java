package org.minefortress.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.jetbrains.annotations.Nullable;
import org.minefortress.MineFortressMod;
import org.minefortress.areas.AreasClientManager;
import org.minefortress.blueprints.manager.ClientBlueprintManager;
import org.minefortress.blueprints.renderer.BlueprintRenderer;
import org.minefortress.blueprints.world.BlueprintsWorld;
import org.minefortress.fortress.FortressClientManager;
import org.minefortress.interfaces.FortressClientWorld;
import org.minefortress.interfaces.FortressMinecraftClient;
import org.minefortress.renderer.FortressCameraManager;
import org.minefortress.renderer.FortressRenderLayer;
import org.minefortress.renderer.gui.ChooseModeScreen;
import org.minefortress.renderer.gui.blueprints.BlueprintsPauseScreen;
import org.minefortress.renderer.gui.hud.FortressHud;
import org.minefortress.selections.SelectionManager;
import org.minefortress.selections.renderer.campfire.CampfireRenderer;
import org.minefortress.selections.renderer.selection.SelectionRenderer;
import org.minefortress.selections.renderer.tasks.TasksRenderer;
import org.minefortress.tasks.ClientTasksHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Supplier;

import static java.util.Map.entry;

@Mixin(MinecraftClient.class)
public abstract class FortressMinecraftClientMixin extends ReentrantThreadExecutor<Runnable> implements FortressMinecraftClient {

    private SelectionManager selectionManager;
    private FortressCameraManager fortressCameraManager;
    private FortressHud fortressHud;

    private FortressClientManager fortressClientManager;

    private final BlockBufferBuilderStorage blockBufferBuilderStorage = new BlockBufferBuilderStorage();

    private ClientBlueprintManager clientBlueprintManager;
    private BlueprintRenderer blueprintRenderer;
    private CampfireRenderer campfireRenderer;
    private SelectionRenderer selectionRenderer;
    private TasksRenderer tasksRenderer;
    private AreasClientManager areasClientManager;

    @Shadow
    @Final
    public GameOptions options;

    @Shadow
    public ClientPlayerInteractionManager interactionManager;

    @Shadow @Final public Mouse mouse;
    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow @Nullable public ClientWorld world;

    @Shadow @Nullable public HitResult crosshairTarget;

    @Shadow public abstract @Nullable IntegratedServer getServer();

    @Shadow public abstract boolean isIntegratedServerRunning();

    @Shadow private @Nullable IntegratedServer server;

    @Shadow @Final private SoundManager soundManager;

    @Shadow public abstract void setScreen(@Nullable Screen screen);

    @Shadow @Nullable public Screen currentScreen;

    public FortressMinecraftClientMixin(String string) {
        super(string);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    public void constructor(RunArgs args, CallbackInfo ci) {
        final MinecraftClient client = (MinecraftClient) (Object) this;

        this.selectionManager = new SelectionManager(client);
        this.fortressCameraManager = new FortressCameraManager(client);
        this.fortressHud = new FortressHud(client);
        this.fortressClientManager = new FortressClientManager();
        this.areasClientManager = new AreasClientManager();

        clientBlueprintManager = new ClientBlueprintManager(client);
        blueprintRenderer = new BlueprintRenderer(clientBlueprintManager.getBlockDataManager(), client, blockBufferBuilderStorage);
        campfireRenderer = new CampfireRenderer(client, blockBufferBuilderStorage);
        Map<RenderLayer, BufferBuilder> selectionBufferBuilderStorage = Map.ofEntries(
                entry(RenderLayer.getLines(), new BufferBuilder(256)),
                entry(FortressRenderLayer.getLinesNoDepth(), new BufferBuilder(256))
        );
        selectionRenderer = new SelectionRenderer(client, selectionBufferBuilderStorage, blockBufferBuilderStorage);

        final Supplier<ClientTasksHolder> clientTasksHolderSupplier = () -> {
            final FortressClientWorld fortressWorld = (FortressClientWorld) this.world;
            if(fortressWorld == null) return null;
            return fortressWorld.getClientTasksHolder();
        };

        tasksRenderer = new TasksRenderer(client, selectionBufferBuilderStorage.get(RenderLayer.getLines()), clientTasksHolderSupplier);
    }

    @Override
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    @Override
    public boolean isNotFortressGamemode() {
        return this.interactionManager == null || this.interactionManager.getCurrentGameMode() != MineFortressMod.FORTRESS;
    }

    @Override
    public boolean isFortressGamemode() {
        return !isNotFortressGamemode();
    }

    @Inject(method="render", at=@At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;updateMouse()V"))
    public void render(boolean tick, CallbackInfo ci) {
        final boolean middleMouseButtonIsDown = options.pickItemKey.isPressed();
        if(!isNotFortressGamemode()) { // is fortress
            if(middleMouseButtonIsDown) {
                if(!mouse.isCursorLocked())
                    mouse.lockCursor();
            } else {
                if(mouse.isCursorLocked())
                    mouse.unlockCursor();
            }
        }

        if(isFortressGamemode() && !middleMouseButtonIsDown) {
            if(player != null) {
                this.fortressCameraManager.updateCameraPosition();
            }
        }

        if ((isNotFortressGamemode() || middleMouseButtonIsDown) && this.world!=null && this.world.getRegistryKey() != BlueprintsWorld.BLUEPRINTS_WORLD_REGISTRY_KEY) {
            if(player != null) {
                this.fortressCameraManager.setRot(player.getPitch(), player.getYaw());
            }
        }
    }

    @Inject(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/TutorialManager;onInventoryOpened()V", shift = At.Shift.BEFORE))
    private void handleInputEvents(CallbackInfo ci) {
        if(this.interactionManager != null && this.interactionManager.getCurrentGameMode() == MineFortressMod.FORTRESS) {
            if(this.options.sprintKey.isPressed()) {
                if(this.getBlueprintManager().hasSelectedBlueprint()) {
                    this.getBlueprintManager().rotateSelectedStructureClockwise();
                } else {
                    this.getSelectionManager().moveSelectionUp();
                }
            }
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    public void setScreenMix(Screen screen, CallbackInfo ci) {
        if(this.interactionManager != null && this.interactionManager.getCurrentGameMode() == MineFortressMod.FORTRESS) {
            if(this.options.sprintKey.isPressed() && screen instanceof InventoryScreen) {
                ci.cancel();
            }
        }
    }

    @Inject(method="doItemPick", at=@At("HEAD"), cancellable = true)
    public void doItemPick(CallbackInfo ci) {
        if(this.isFortressGamemode()) {
            ci.cancel();
        }
    }

    @Inject(method="tick", at=@At("TAIL"))
    public void tick(CallbackInfo ci) {
        if(this.world == null && this.fortressHud.isHovered()) {
            this.fortressHud = new FortressHud((MinecraftClient)(Object)this);
        }
        this.fortressHud.tick();
        this.fortressClientManager.tick(this);
        if(this.fortressClientManager.gamemodeNeedsInitialization() && !(this.currentScreen instanceof ChooseModeScreen)) {
            this.setScreen(new ChooseModeScreen());
        }
    }

    @Override
    public FortressHud getFortressHud() {
        return fortressHud;
    }

    @Override
    public FortressClientManager getFortressClientManager() {
        return fortressClientManager;
    }

    @Override
    public BlockPos getHoveredBlockPos() {
        final HitResult hitResult = this.crosshairTarget;
        if(hitResult instanceof BlockHitResult) {
            return ((BlockHitResult) hitResult).getBlockPos();
        } else {
            return null;
        }
    }

    @Inject(method = "openPauseMenu", at = @At("HEAD"), cancellable = true)
    public void openPauseMenu(boolean pause, CallbackInfo ci) {
        if(this.world != null && this.world.getRegistryKey() == BlueprintsWorld.BLUEPRINTS_WORLD_REGISTRY_KEY) {
            final boolean localServer = this.isIntegratedServerRunning() && (this.server == null || !this.server.isRemote());
            if (localServer) {
                this.setScreen(new BlueprintsPauseScreen(!pause));
                this.soundManager.pauseAll();
            } else {
                this.setScreen(new BlueprintsPauseScreen(true));
            }
            ci.cancel();
        }
    }

    @Override
    public BlueprintRenderer getBlueprintRenderer() {
        return blueprintRenderer;
    }

    @Override
    public CampfireRenderer getCampfireRenderer() {
        return campfireRenderer;
    }

    @Override
    public SelectionRenderer getSelectionRenderer() {
        return selectionRenderer;
    }

    @Override
    public TasksRenderer getTasksRenderer() {
        return tasksRenderer;
    }

    @Override
    public ClientBlueprintManager getBlueprintManager() {
        return this.clientBlueprintManager;
    }

    @Override
    public AreasClientManager getAreasClientManager() {
        return this.areasClientManager;
    }

    @Inject(method = "close", at = @At("HEAD"))
    public void close(CallbackInfo ci) {
        this.blueprintRenderer.close();
        this.campfireRenderer.close();
        this.selectionRenderer.close();
    }
}
