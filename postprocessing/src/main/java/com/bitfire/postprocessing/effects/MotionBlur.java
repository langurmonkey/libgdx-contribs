
package com.bitfire.postprocessing.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.bitfire.postprocessing.PostProcessorEffect;
import com.bitfire.postprocessing.filters.Copy;
import com.bitfire.postprocessing.filters.MotionFilter;

/**
 * A motion blur effect which draws the last frame with a lower opacity. The
 * result is then stored as the next last frame to create the trail effect.
 * 
 * @author Toni Sagrista
 */
public class MotionBlur extends PostProcessorEffect {
    private MotionFilter motionFilter;
    private Copy copyFilter;
    private FrameBuffer fbo;

    public MotionBlur(int width, int height) {
        motionFilter = new MotionFilter();
        motionFilter.setResolution(width, height);
        copyFilter = new Copy();
    }

    public void setBlurOpacity(float blurOpacity) {
        motionFilter.setBlurOpacity(blurOpacity);
    }

    public void setBlurRadius(float blurRadius) {
        motionFilter.setBlurRadius(blurRadius);
    }

    public void setResolution(int w, int h) {
        motionFilter.setResolution(w, h);
    }

    @Override
    public void dispose() {
        if (motionFilter != null) {
            motionFilter.dispose();
            motionFilter = null;
        }
    }

    @Override
    public void rebind() {
        motionFilter.rebind();
    }

    @Override
    public void render(FrameBuffer src, FrameBuffer dest) {
        restoreViewport(dest);
        if (dest != null) {
            motionFilter.setInput(src).setOutput(dest).render();
            fbo = dest;
        } else {
            if (fbo == null) {
                // Init frame buffer
                fbo = FrameBuffer.createFrameBuffer(Format.RGBA8888, src.getWidth(), src.getHeight(), false);
            }
            motionFilter.setInput(src).setOutput(fbo).render();

            // Copy fbo to screen
            copyFilter.setInput(fbo).setOutput(dest).render();
        }

        // Set last frame
        motionFilter.setLastFrameTexture(fbo.getColorBufferTexture());

    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // Clean texture if disabled
        if (!enabled && fbo != null) {
            fbo.begin();
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT | (Gdx.graphics.getBufferFormat().coverageSampling ? GL20.GL_COVERAGE_BUFFER_BIT_NV : 0));
            fbo.end();
        }
    }

}
