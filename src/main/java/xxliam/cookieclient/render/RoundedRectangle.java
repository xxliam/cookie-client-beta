package xxliam.cookieclient.render;

/**
 * 圆角矩形数据类，描述一个矩形及其四角独立半径。
 * <p>
 * 仿照 OpenZen 的 {@code shit.zen.render.RoundedRectangle} 设计（去掉 lombok）。
 */
public final class RoundedRectangle {

    public final float x1;
    public final float y1;
    public final float x2;
    public final float y2;
    public final float topLeftRadius;
    public final float topRightRadius;
    public final float bottomRightRadius;
    public final float bottomLeftRadius;

    private RoundedRectangle(float x1, float y1, float x2, float y2,
                             float tlRadius, float trRadius, float brRadius, float blRadius) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.topLeftRadius = tlRadius;
        this.topRightRadius = trRadius;
        this.bottomRightRadius = brRadius;
        this.bottomLeftRadius = blRadius;
    }

    /**
     * 以左上角坐标 + 宽高 + 统一圆角半径构造。
     */
    public static RoundedRectangle ofXYWHR(float x, float y, float width, float height, float radius) {
        return new RoundedRectangle(x, y, x + width, y + height, radius, radius, radius, radius);
    }

    /**
     * 以左上角坐标 + 宽高 + 四角独立半径构造。
     */
    public static RoundedRectangle ofXYWHRadii(float x, float y, float width, float height,
                                               float tl, float tr, float br, float bl) {
        return new RoundedRectangle(x, y, x + width, y + height, tl, tr, br, bl);
    }

    public float getWidth() {
        return x2 - x1;
    }

    public float getHeight() {
        return y2 - y1;
    }
}
