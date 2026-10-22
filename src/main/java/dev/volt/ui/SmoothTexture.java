package dev.volt.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Linear sampling is essential when a UI texture is scaled by GUI settings. */
public final class SmoothTexture extends DynamicTexture {
    public SmoothTexture(String label, NativeImage image) {
        super(() -> label, image);
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }
}
