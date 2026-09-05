package xxliam.cookieclient.render;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Kernel;

/**
 * 高斯模糊滤镜（纯 java.awt 实现，无外部依赖）。
 * <p>
 * 搬运自 OpenZen 的 {@code shit.zen.render.GaussianBlur}。
 */
public class GaussianBlur {

    protected float radius;
    protected Kernel kernel;

    public GaussianBlur(float radius) {
        setRadius(radius);
    }

    public static void convolve(Kernel kernel, int[] src, int[] dst, int width, int height,
                                boolean alphaChannel, boolean premultiply, boolean unpremultiply, int edgeAction) {
        float[] kernelData = kernel.getKernelData(null);
        int kernelWidth = kernel.getWidth();
        int half = kernelWidth / 2;
        for (int i = 0; i < height; ++i) {
            int dstIndex = i;
            int srcRowStart = i * width;
            for (int j = 0; j < width; ++j) {
                float red = 0.0f;
                float green = 0.0f;
                float blue = 0.0f;
                float alpha = 0.0f;
                int kernelOffset = half;
                for (int k = -half; k <= half; ++k) {
                    float weight = kernelData[kernelOffset + k];
                    if (weight == 0.0f) {
                        continue;
                    }
                    int srcX = j + k;
                    if (srcX < 0) {
                        if (edgeAction == 1) {
                            srcX = 0;
                        } else if (edgeAction == 2) {
                            srcX = (j + width) % width;
                        }
                    } else if (srcX >= width) {
                        if (edgeAction == 1) {
                            srcX = width - 1;
                        } else if (edgeAction == 2) {
                            srcX = (j + width) % width;
                        }
                    }
                    int argb = src[srcRowStart + srcX];
                    int a = argb >> 24 & 0xFF;
                    int r = argb >> 16 & 0xFF;
                    int g = argb >> 8 & 0xFF;
                    int b = argb & 0xFF;
                    if (premultiply) {
                        float scale = a * 0.003921569f;
                        r = (int) (r * scale);
                        g = (int) (g * scale);
                        b = (int) (b * scale);
                    }
                    alpha += weight * a;
                    red += weight * r;
                    green += weight * g;
                    blue += weight * b;
                }
                if (unpremultiply && alpha != 0.0f && alpha != 255.0f) {
                    float invAlpha = 255.0f / alpha;
                    red *= invAlpha;
                    green *= invAlpha;
                    blue *= invAlpha;
                }
                int outA = alphaChannel ? clamp((int) (alpha + 0.5)) : 255;
                int outR = clamp((int) (red + 0.5));
                int outG = clamp((int) (green + 0.5));
                int outB = clamp((int) (blue + 0.5));
                dst[dstIndex] = outA << 24 | outR << 16 | outG << 8 | outB;
                dstIndex += height;
            }
        }
    }

    public static int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 255);
    }

    public static Kernel makeKernel(float radius) {
        int kernelHalf = (int) Math.ceil(radius);
        int kernelSize = kernelHalf * 2 + 1;
        float[] kernelData = new float[kernelSize];
        float sigma = radius / 3.0f;
        float twoSigmaSq = 2.0f * sigma * sigma;
        float sqrtTwoPiSigma = (float) Math.sqrt(Math.PI * 2 * sigma);
        float radiusSq = radius * radius;
        float sum = 0.0f;
        int index = 0;
        for (int i = -kernelHalf; i <= kernelHalf; ++i) {
            float distSq = i * i;
            kernelData[index] = distSq > radiusSq ? 0.0f : (float) Math.exp(-distSq / twoSigmaSq) / sqrtTwoPiSigma;
            sum += kernelData[index];
            ++index;
        }
        for (int i = 0; i < kernelSize; ++i) {
            kernelData[i] = kernelData[i] / sum;
        }
        return new Kernel(kernelSize, 1, kernelData);
    }

    public void setRadius(float radius) {
        this.radius = radius;
        this.kernel = makeKernel(radius);
    }

    public BufferedImage filter(BufferedImage source, BufferedImage dest) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (dest == null) {
            dest = createCompatibleDestImage(source, null);
        }
        int[] srcPixels = new int[width * height];
        int[] dstPixels = new int[width * height];
        source.getRGB(0, 0, width, height, srcPixels, 0, width);
        if (radius > 0.0f) {
            convolve(kernel, srcPixels, dstPixels, width, height, true, true, false, 1);
            convolve(kernel, dstPixels, srcPixels, height, width, true, false, true, 1);
        }
        dest.setRGB(0, 0, width, height, srcPixels, 0, width);
        return dest;
    }

    public BufferedImage createCompatibleDestImage(BufferedImage source, ColorModel colorModel) {
        if (colorModel == null) {
            colorModel = source.getColorModel();
        }
        return new BufferedImage(colorModel,
                colorModel.createCompatibleWritableRaster(source.getWidth(), source.getHeight()),
                colorModel.isAlphaPremultiplied(), null);
    }
}
