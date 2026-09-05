package xxliam.cookieclient.render;

/**
 * 单个字形：在字体图集纹理中的位置、尺寸、对应字符与所属图页。
 */
record Glyph(int u, int v, int width, int height, char value, GlyphPage owner) {
}
