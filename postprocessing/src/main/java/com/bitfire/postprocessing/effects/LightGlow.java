/*******************************************************************************
 * Copyright 2012 tsagrista
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.bitfire.postprocessing.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.bitfire.postprocessing.PostProcessor;
import com.bitfire.postprocessing.PostProcessorEffect;
import com.bitfire.postprocessing.filters.Bias;
import com.bitfire.postprocessing.filters.Combine;
import com.bitfire.postprocessing.filters.Glow;
import com.bitfire.postprocessing.utils.PingPongBuffer;

/** Light scattering implementation.
 * @author Toni Sagrista */
public final class LightGlow extends PostProcessorEffect {
    public static class Settings {
        public final String name;

        public final float bias;

        public final float glowIntensity;
        public final float glowSaturation;
        public final float baseIntensity;
        public final float baseSaturation;

        public Settings(String name, float bias, float baseIntensity, float baseSaturation, float glowIntensity,
                float glowSaturation) {
            this.name = name;

            this.bias = bias;
            this.baseIntensity = baseIntensity;
            this.baseSaturation = baseSaturation;
            this.glowIntensity = glowIntensity;
            this.glowSaturation = glowSaturation;

        }

        public Settings(Settings other) {
            this.name = other.name;

            this.bias = other.bias;
            this.baseIntensity = other.baseIntensity;
            this.baseSaturation = other.baseSaturation;
            this.glowIntensity = other.glowIntensity;
            this.glowSaturation = other.glowSaturation;

        }
    }

    private PingPongBuffer pingPongBuffer;

    private Glow glow;
    private Bias bias;
    private Combine combine;

    private Settings settings;

    private boolean blending = false;
    private int sfactor, dfactor;

    public LightGlow(int fboWidth, int fboHeight) {
        pingPongBuffer = PostProcessor.newPingPongBuffer(fboWidth, fboHeight, PostProcessor.getFramebufferFormat(), false);

        glow = new Glow(fboWidth, fboHeight);
        bias = new Bias();
        combine = new Combine();

        setSettings(new Settings("default", -0.9f, 1f, 1f, 0.7f, 1f));
    }

    @Override
    public void dispose() {
        combine.dispose();
        bias.dispose();
        pingPongBuffer.dispose();
    }

    /** Sets the positions of the 10 lights in [0..1] in both coordinates **/
    public void setLightPositions(int nLights, float[] vec) {
        glow.setLightPositions(nLights, vec);
    }

    public void setLightViewAngles(float[] vec) {
        glow.setLightViewAngles(vec);
    }

    public void setLightColors(float[] vec) {
        glow.setLightColors(vec);
    }

    public void setNSamples(int nSamples) {
        glow.setNSamples(nSamples);
    }

    public void setTextureScale(float scl) {
        glow.setTextureScale(scl);
    }

    public void setBaseIntesity(float intensity) {
        combine.setSource1Intensity(intensity);
    }

    public void setBaseSaturation(float saturation) {
        combine.setSource1Saturation(saturation);
    }

    public void setScatteringIntesity(float intensity) {
        combine.setSource2Intensity(intensity);
    }

    public void setScatteringSaturation(float saturation) {
        combine.setSource2Saturation(saturation);
    }

    public void setBias(float b) {
        bias.setBias(b);
    }

    public void enableBlending(int sfactor, int dfactor) {
        this.blending = true;
        this.sfactor = sfactor;
        this.dfactor = dfactor;
    }

    public void disableBlending() {
        this.blending = false;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;

        // setup threshold filter
        setBias(settings.bias);

        // setup combine filter
        setBaseIntesity(settings.baseIntensity);
        setBaseSaturation(settings.baseSaturation);
        setScatteringIntesity(settings.glowIntensity);
        setScatteringSaturation(settings.glowSaturation);

    }

    public void setLightGlowTexture(Texture tex) {
        glow.setLightGlowTexture(tex);
    }

    public Texture getLightGlowTexture() {
        return glow.getLightGlowTexture();
    }

    public void setPrePassTexture(Texture tex) {
        glow.setPrePassTexture(tex);
    }

    public Texture getPrePassTexture() {
        return glow.getPrePassTexture();
    }

    public float getBias() {
        return bias.getBias();
    }

    public float getBaseIntensity() {
        return combine.getSource1Intensity();
    }

    public float getBaseSaturation() {
        return combine.getSource1Saturation();
    }

    public float getScatteringIntensity() {
        return combine.getSource2Intensity();
    }

    public float getScatteringSaturation() {
        return combine.getSource2Saturation();
    }

    public boolean isBlendingEnabled() {
        return blending;
    }

    public int getBlendingSourceFactor() {
        return sfactor;
    }

    public int getBlendingDestFactor() {
        return dfactor;
    }

    public Settings getSettings() {
        return settings;
    }

    @Override
    public void render(final FrameBuffer src, final FrameBuffer dest) {
        Texture texsrc = src.getColorBufferTexture();

        boolean blendingWasEnabled = PostProcessor.isStateEnabled(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        pingPongBuffer.begin();
        {
            // apply bias
            bias.setInput(texsrc).setOutput(pingPongBuffer.getSourceBuffer()).render();

            glow.setInput(pingPongBuffer.getSourceBuffer()).setOutput(pingPongBuffer.getResultBuffer()).render();

        }
        pingPongBuffer.end();

        if (blending || blendingWasEnabled) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
        }

        if (blending) {
            Gdx.gl.glBlendFunc(sfactor, dfactor);
        }

        restoreViewport(dest);

        // mix original scene and blurred threshold, modulate via
        combine.setOutput(dest).setInput(texsrc, pingPongBuffer.getResultTexture()).render();
    }

    @Override
    public void rebind() {
        glow.rebind();
        bias.rebind();
        combine.rebind();
        pingPongBuffer.rebind();
    }
}
