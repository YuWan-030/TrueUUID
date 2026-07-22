package cn.alini.trueuuid.presentation;

/** Loader-neutral badge placement and text alignment. */
public record BadgeLayout(float scale, int x, int y, int textX, int textY) {
    public static BadgeLayout calculate(int screenWidth, int screenHeight, int textWidth, int fontHeight,
                                        float scale, boolean right, boolean bottom,
                                        int offsetX, int offsetY) {
        if (!Float.isFinite(scale) || scale <= 0.0F) throw new IllegalArgumentException("scale must be positive");
        float drawnWidth = (BadgeArtwork.WIDTH + BadgeArtwork.GAP + textWidth) * scale;
        float drawnHeight = BadgeArtwork.HEIGHT * scale;
        float unscaledX = (right ? screenWidth - BadgeArtwork.SAFE_MARGIN - drawnWidth
                : BadgeArtwork.SAFE_MARGIN) + offsetX;
        float unscaledY = (bottom ? screenHeight - BadgeArtwork.SAFE_MARGIN - drawnHeight
                : BadgeArtwork.SAFE_MARGIN) + offsetY;
        int x = Math.round(unscaledX / scale);
        int y = Math.round(unscaledY / scale);
        int textY = y + (BadgeArtwork.BODY_TOP + BadgeArtwork.BODY_BOTTOM) / 2 - fontHeight / 2;
        return new BadgeLayout(scale, x, y, x + BadgeArtwork.WIDTH + BadgeArtwork.GAP, textY);
    }
}
