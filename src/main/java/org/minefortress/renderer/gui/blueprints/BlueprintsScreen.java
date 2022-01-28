package org.minefortress.renderer.gui.blueprints;

import com.chocohead.mm.api.ClassTinkerers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.minefortress.blueprints.BlueprintMetadata;
import org.minefortress.blueprints.BlueprintMetadataManager;
import org.minefortress.interfaces.FortressMinecraftClient;

import java.util.List;

public final class BlueprintsScreen extends Screen {

    private static final Identifier INVENTORY_TABS_TEXTURE = new Identifier("textures/gui/container/creative_inventory/tabs.png");
    private static final String BACKGROUND_TEXTURE = "textures/gui/container/creative_inventory/tab_items.png";

    private final int backgroundWidth = 195;
    private final int backgroundHeight = 136;

    private int x;
    private int y;

    private BlueprintGroup selectedGroup = BlueprintGroup.MAIN;
    private BlueprintMetadataManager blueprintMetadataManager;
    private int scrollPosition = 0;

    private BlueprintMetadata focusedBlueprint;

    private boolean hasScrollbar = false;

    public BlueprintsScreen() {
        super(new LiteralText("Blueprints"));
    }

    @Override
    protected void init() {
        if(this.client != null) {
            this.client.keyboard.setRepeatEvents(true);
            final ClientPlayerInteractionManager interactionManager = this.client.interactionManager;
            if(interactionManager != null && interactionManager.getCurrentGameMode() == ClassTinkerers.getEnum(GameMode.class, "FORTRESS")) {
                super.init();
                this.x = (this.width - backgroundWidth) / 2;
                this.y = (this.height - backgroundHeight) / 2;

                if(this.client instanceof FortressMinecraftClient fortressClient) {
                    this.blueprintMetadataManager = fortressClient.getBlueprintMetadataManager();
                }
            } else {
                this.client.setScreen(null);
            }
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        this.drawBackground(matrices, delta, mouseX, mouseY);
        RenderSystem.disableDepthTest();
        super.render(matrices, mouseX, mouseY, delta);

        int screenX = this.x;
        int screenY = this.y;

        MatrixStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.push();
        matrixStack.translate(screenX, screenY, 0.0);
        RenderSystem.applyModelViewMatrix();
        this.focusedBlueprint = null;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        final List<BlueprintMetadata> allBlueprints = this.blueprintMetadataManager.getAllBlueprint();
        final int blueprintsAmount = allBlueprints.size();
        this.hasScrollbar = blueprintsAmount > 9 * 5;
        for (int i = 0; i < blueprintsAmount; i++) {
            int slotColumn = i % 9;
            int slotRow = i / 9;

            int slotX = slotColumn * 18 + 9;
            int slotY = slotRow * 18 + 18;

            final BlueprintMetadata blueprintMetadata = allBlueprints.get(i);
            this.drawSlot(matrices, blueprintMetadata, slotX, slotY);


            if (!this.isPointOverSlot(slotX, slotY, mouseX, mouseY)) continue;
            this.focusedBlueprint = blueprintMetadata;
            HandledScreen.drawSlotHighlight(matrices, slotX, slotY, this.getZOffset());
        }

        this.drawForeground(matrices, mouseX, mouseY);

        matrixStack.pop();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();

        for(BlueprintGroup group : BlueprintGroup.values()) {
            this.renderTabTooltipIfHovered(matrices, group, mouseX, mouseY);
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        this.drawMouseoverTooltip(matrices, mouseX, mouseY);
    }

    private boolean isPointOverSlot(int slotX, int slotY, int mouseX, int mouseY) {
        int screenX = this.x;
        int screenY = this.y;

        return mouseX >= screenX + slotX && mouseX < screenX + slotX + 18 && mouseY >= screenY + slotY && mouseY < screenY + slotY + 18;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
        if (this.selectedGroup != null) {
            RenderSystem.disableBlend();
            this.textRenderer.draw(matrices, this.selectedGroup.getNameText(), 8.0f, 6.0f, 0x404040);
        }
    }

    private void drawSlot(MatrixStack matrices, BlueprintMetadata metadata, int slotX, int slotY) {
        ItemStack itemStack = new ItemStack(Items.DIRT);
        String string = null;
        this.setZOffset(100);
        this.itemRenderer.zOffset = 100.0f;

        RenderSystem.enableDepthTest();
        if(this.client != null)
            this.itemRenderer.renderInGuiWithOverrides(this.client.player, itemStack, slotX, slotY, slotX + slotY * this.backgroundWidth);
        this.itemRenderer.renderGuiItemOverlay(this.textRenderer, itemStack, slotX, slotY, string);

        this.itemRenderer.zOffset = 0.0f;
        this.setZOffset(0);
    }

    @Override
    public void removed() {
        super.removed();
        if(this.client!=null)
            this.client.keyboard.setRepeatEvents(false);
    }

    private void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        for (BlueprintGroup bg : BlueprintGroup.values()) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, INVENTORY_TABS_TEXTURE);
            if (bg == selectedGroup) continue;
            this.renderTabIcon(matrices, bg);
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, new Identifier(BACKGROUND_TEXTURE));
        this.drawTexture(matrices, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
//        this.searchBox.render(matrices, mouseX, mouseY, delta);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        int i = this.x + 175;
        int j = this.y + 18;
        int k = j + 112;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, INVENTORY_TABS_TEXTURE);

        this.drawTexture(matrices, i, j + (int)((float)(k - j - 17) * this.scrollPosition), 232 + (this.hasScrollbar() ? 0 : 12), 0, 12, 15);

        if(selectedGroup != null)
            this.renderTabIcon(matrices, selectedGroup);
    }


    public boolean hasScrollbar() {
        return hasScrollbar;
    }

    private void renderTabIcon(MatrixStack matrices, BlueprintGroup group) {
        boolean isSelectedGroup = group == selectedGroup;
        int columnNumber = group.ordinal() % 7;
        int texX = columnNumber * 28;
        int texY = 0;
        int x = this.x + 28 * columnNumber;
        int y = this.y;
        if (columnNumber > 0) {
            x += columnNumber;
        }
        boolean topRow = group.isTopRow();
        if (topRow) {
            y -= 28;
        } else {
            texY += 64;
            y += this.backgroundHeight - 4;
        }
        if (isSelectedGroup) {
            texY += 32;
        }
        this.drawTexture(matrices, x, y, texX, texY, 28, 32);
        this.itemRenderer.zOffset = 100.0f;
        int yIconDelta = topRow ? 1 : -1;
        ItemStack icon = group.getIcon();
        this.itemRenderer.renderInGuiWithOverrides(icon, x += 6, y += 8 + yIconDelta);
        this.itemRenderer.renderGuiItemOverlay(this.textRenderer, icon, x, y);
        this.itemRenderer.zOffset = 0.0f;
    }

    private void renderTabTooltipIfHovered(MatrixStack matrices, BlueprintGroup group, int mouseX, int mouseY) {
        int columnNumber = group.ordinal();
        int x = 28 * columnNumber + this.x;
        int y = this.y;
        if (columnNumber > 0) {
            x += columnNumber;
        }
        if (group.isTopRow()) {
            y -= 32;
        } else {
            y += this.backgroundHeight;
        }
        if (this.isPointWithinBounds(x + 3, y + 3, 23, 27, mouseX, mouseY)) {
            this.renderTooltip(matrices, group.getNameText(), mouseX, mouseY);

        }
    }

    private boolean isPointWithinBounds(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private void drawMouseoverTooltip(MatrixStack matrices, int x, int y) {
        if (this.focusedBlueprint != null) {
            this.renderTooltip(matrices, new LiteralText(focusedBlueprint.getName()), x, y);
        }
    }

}
