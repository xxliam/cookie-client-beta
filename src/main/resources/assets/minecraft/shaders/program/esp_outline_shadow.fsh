#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float Radius;

out vec4 fragColor;

void main(){
    vec4 center = texture(DiffuseSampler, texCoord);
    float core = center.a;

    // sobel 细边（alpha > 0.5）保持原样：细黑边不动（DarkShadow 染黑 → 黑边；Glow 保留队色）
    if (core > 0.5) {
        fragColor = vec4(center.rgb, core);
        return;
    }

    // 8 方向（每 45°，运行时三角生成 → GLSL 150 合法且各向同性）
    float dist = Radius;
    vec3 edgeColor = center.rgb;
    float found = 0.0;
    for (int dir = 0; dir < 8; ++dir) {
        float ang = float(dir) * 0.7853981633974483; // dir * 45°
        vec2 stepDir = vec2(cos(ang), sin(ang)) * oneTexel;
        for (int i = 1; i <= 64; ++i) {
            float d = float(i);
            if (d > Radius) break;
            vec4 s = texture(DiffuseSampler, texCoord + stepDir * d);
            if (s.a > 0.001) {
                if (d < dist) {
                    dist = d;
                    edgeColor = s.rgb;
                }
                found = 1.0;
                break; // 该方向最近命中即止
            }
        }
    }
    if (found < 0.5) {
        // 远离所有黑边：完全透明（不参与屏幕叠加）
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }

    // 余弦衰减：t=0(贴边) 浓度 1，t=1(半径尽头) 平滑衰减到 0（两端导数均为 0，
    // 渐变更顺滑、无生硬截止线）；强度系数 0.55 控制淡影浓度
    float t = clamp(dist / Radius, 0.0, 1.0);
    float weight = 0.5 + 0.5 * cos(3.141592653589793 * t);
    float alpha = weight * 0.55;

    fragColor = vec4(edgeColor, alpha);
}
