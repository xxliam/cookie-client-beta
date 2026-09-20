package xxliam.cookieclient.render;

/**
 * 不可变矩形（左上 + 右下），逐字搬运自 OpenZen 的 {@code shit.zen.render.Rectangle}。
 * <p>
 * 与项目里既有的 {@link RoundedRectangle} 平行：这个只表达"矩形区域"（GUI 裁剪、命中判定），
 * 不带圆角信息。
 */
public record Rectangle(float x1, float y1, float x2, float y2) {

    /** 左上角 + 尺寸 → 左上 + 右下。 */
    public static Rectangle ofXYWH(float x, float y, float width, float height) {
        return new Rectangle(x, y, x + width, y + height);
    }

    public static Rectangle ofCorners(float x1, float y1, float x2, float y2) {
        return new Rectangle(x1, y1, x2, y2);
    }

    public float getWidth() {
        return this.x2 - this.x1;
    }

    public float getHeight() {
        return this.y2 - this.y1;
    }

    public float getX() {
        return this.x1;
    }

    public float getY() {
        return this.y1;
    }

    public float getRight() {
        return this.x2;
    }

    public float getBottom() {
        return this.y2;
    }
}
