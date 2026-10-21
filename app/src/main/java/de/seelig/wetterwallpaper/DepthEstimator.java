package de.seelig.wetterwallpaper;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/** Depth Anything V2 Small (fp16, Apache-2.0) auf dem Handy, gleiche Vorverarbeitung wie tools/split_layers.py. */
final class DepthEstimator implements AutoCloseable {
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    private static final int MODEL_WIDTH = 518;

    /** Relative Nähe 0..1 (1 = nah) in Modellauflösung. */
    static final class Depth {
        final int width;
        final int height;
        final float[] near;

        Depth(int width, int height, float[] near) {
            this.width = width;
            this.height = height;
            this.near = near;
        }
    }

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;

    DepthEstimator(Context ctx) throws Exception {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(4);
        session = env.createSession(readAsset(ctx, "depth.onnx"), options);
    }

    Depth estimate(Bitmap img) throws OrtException {
        int w = MODEL_WIDTH;
        // Modell will Kantenlängen als Vielfache von 14
        int h = Math.max(14, Math.round(w * img.getHeight() / (float) img.getWidth() / 14f) * 14);
        Bitmap scaled = Bitmap.createScaledBitmap(img, w, h, true);
        int n = w * h;
        int[] px = new int[n];
        scaled.getPixels(px, 0, w, 0, 0, w, h);
        FloatBuffer input = FloatBuffer.allocate(3 * n);
        for (int c = 0; c < 3; c++) {
            int shift = 16 - 8 * c;
            for (int i = 0; i < n; i++) {
                float value = ((px[i] >> shift) & 0xFF) / 255f;
                input.put(c * n + i, (value - MEAN[c]) / STD[c]);
            }
        }
        String inputName = session.getInputNames().iterator().next();
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, input, new long[]{1, 3, h, w});
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            float[][][] out = (float[][][]) result.get(0).getValue();
            float[] near = new float[n];
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float d = out[0][y][x];
                    near[y * w + x] = d;
                    min = Math.min(min, d);
                    max = Math.max(max, d);
                }
            }
            float range = Math.max(max - min, 1e-6f);
            for (int i = 0; i < n; i++) near[i] = (near[i] - min) / range;
            return new Depth(w, h, near);
        }
    }

    private static byte[] readAsset(Context ctx, String name) throws java.io.IOException {
        try (InputStream in = ctx.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1 << 16];
            for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
