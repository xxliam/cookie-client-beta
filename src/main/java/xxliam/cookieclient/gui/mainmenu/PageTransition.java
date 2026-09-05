package xxliam.cookieclient.gui.mainmenu;

/** 页面切换黑幕过渡（复刻 Setsuna PageTransition 220ms 三次方淡出）。 */
final class PageTransition {

    private static final long DURATION_NANOS = 220_000_000L;

    private PageTransition() {
    }

    static int overlayAlpha(long startedAt) {
        float progress = Math.max(0.0F, Math.min(1.0F,
                (System.nanoTime() - startedAt) / (float) DURATION_NANOS));
        float remaining = 1.0F - progress;
        return Math.round(255.0F * remaining * remaining * remaining);
    }
}
