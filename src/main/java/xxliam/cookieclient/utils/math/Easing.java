package xxliam.cookieclient.utils.math;

/**
 * 缓动函数接口。输入归一化进度 t ∈ [0,1]，输出缓动后的进度。
 */
@FunctionalInterface
public interface Easing {
    double ease(double t);
}
