$input v_color0, v_texcoord0, v_worldPos, fclr, p

#include <bgfx_shader.sh>

uniform vec4 FogAndDistanceControl;

void main() {
    vec4 diffuse = v_color0;

#if !defined(INSTANCING)
    // ===== deteksi waktu dan cuaca (dari warna fog) =====
    float day = pow(max(min(1.0 - fclr.r * 1.2, 1.0), 0.0), 0.4);
    float night = pow(max(min(1.0 - fclr.r * 1.5, 1.0), 0.0), 1.2);
    float dusk = clamp(fclr.r - fclr.g, 0.0, 0.5) * 2.0;

    vec2 rf = vec2(0.5 + 20.0 / FogAndDistanceControl.z, 1.0);
    vec2 rt = clamp((FogAndDistanceControl.xy - rf) / (vec2(0.23, 0.7) - rf), 0.0, 1.0);
    float rk = rt.x * rt.y;
    float rain = rk * rk * (3.0 - 2.0 * rk);
    rain *= step(0.01, FogAndDistanceControl.x);

    // gradasi dari puncak kubah (0.1) ke horizon (1.0)
    float grad = clamp(length(p.xz) * 1.6, 0.1, 1.0);

    //=====deteksi=====
    float fogSat = max(fclr.r, max(fclr.g, fclr.b)) - min(fclr.r, min(fclr.g, fclr.b));
    float fogLum = dot(fclr.rgb, vec3_splat(0.333));
    float rainGray = (1.0 - smoothstep(0.05, 0.2, fogSat)) * smoothstep(0.15, 0.4, fogLum);
    float nS = smoothstep(0.3, 0.6, night);
    float rS = smoothstep(0.1, 0.4, max(rain, rainGray)) * (1.0 - nS);
    float duS = smoothstep(0.2, 0.6, dusk) * (1.0 - rS) * (1.0 - nS);
    float pS = smoothstep(0.3, 0.6, day) * (1.0 - rS) * (1.0 - duS) * (1.0 - nS);

    //=====warna langit=====
    vec4 cPagi = mix(vec4(0.1, 0.5, 1.0, 1.0) * 0.7, vec4_splat(1.0), grad);
    vec4 cSore = mix(vec4(0.1, 0.5, 1.0, 1.0) * 0.2, vec4(1.0, 0.8, 0.1, 1.0), grad);
    vec4 cHujan = mix(vec4(0.3, 0.3, 0.3, 1.0) * 1.5, vec4(0.2, 0.2, 0.2, 1.0), grad);
    vec4 cMalam = mix(vec4(0.1, 0.1, 0.1, 1.0), vec4(0.1, 0.5, 1.0, 1.0), grad);
    diffuse = (cPagi * (pS + 0.0001) + cSore * duS + cHujan * rS + cMalam * nS) / (pS + 0.0001 + duS + rS + nS);
#endif

    gl_FragColor = diffuse;
}
