$input v_color0, v_texcoord0, v_worldPos, fclr, p, waktu

#include <bgfx_shader.sh>

uniform vec4 FogAndDistanceControl;

float hash(vec2 q) {
    return fract(sin(dot(q, vec2(127.1, 311.7))) * 43758.5453);
}

//Bayer dithering
float bayer2(vec2 a) {
    a = floor(a);
    return fract(a.x / 2.0 + a.y * a.y * 0.75);
}
float bayer4(vec2 a) {
    return bayer2(0.5 * a) * 0.25 + bayer2(a);
}
float bayer8(vec2 a) {
    return bayer4(0.5 * a) * 0.25 + bayer2(a);
}

#define UDW_FOG_WARNA vec3(0.3,1.1,0.85)*0.7
#define AWAN_BAYER 1
#define AWAN_DITHER 0.02

void main() {
    vec4 diffuse = v_color0;

#if !defined(INSTANCING)
    //deteksi waktu dan cuaca
    float day = pow(max(min(1.0 - fclr.r * 1.2, 1.0), 0.0), 0.4);
    float night = pow(max(min(1.0 - fclr.r * 1.5, 1.0), 0.0), 1.2);
    highp float dusk = clamp(fclr.r - fclr.g, 0.0, 0.5) * 2.0;

    vec2 rf = vec2(0.5 + 20.0 / FogAndDistanceControl.z, 1.0);
    vec2 rt = clamp((FogAndDistanceControl.xy - rf) / (vec2(0.23, 0.7) - rf), 0.0, 1.0);
    float rk = rt.x * rt.y;
    float rain = rk * rk * (3.0 - 2.0 * rk);
    rain *= step(0.01, FogAndDistanceControl.x);

    float grad = clamp(length(p.xz) * 1.6, 0.1, 1.0);

    //deteksi
    float fogSat = max(fclr.r, max(fclr.g, fclr.b)) - min(fclr.r, min(fclr.g, fclr.b));
    float fogLum = dot(fclr.rgb, vec3_splat(0.333));
    float rainGray = (1.0 - smoothstep(0.05, 0.2, fogSat)) * smoothstep(0.15, 0.4, fogLum);
    float nS = smoothstep(0.3, 0.6, night);
    float rS = smoothstep(0.1, 0.4, max(rain, rainGray)) * (1.0 - nS);
    float duS = smoothstep(0.2, 0.6, dusk) * (1.0 - rS) * (1.0 - nS);
    float pS = smoothstep(0.3, 0.6, day) * (1.0 - rS) * (1.0 - duS) * (1.0 - nS);

    //warna langit
    vec4 cPagi = mix(vec4(0.1, 0.5, 1.0, 1.0) * 0.7, vec4_splat(1.0), grad);
    vec4 cSore = mix(vec4(0.1, 0.5, 1.0, 1.0) * 0.2, vec4(1.0, 0.8, 0.1, 1.0), grad);
    vec4 cHujan = mix(vec4(0.3, 0.3, 0.3, 1.0) * 1.5, vec4(0.2, 0.2, 0.2, 1.0), grad);
    vec4 cMalam = mix(vec4(0.1, 0.1, 0.1, 1.0), vec4(0.1, 0.5, 1.0, 1.0), grad);
    diffuse = (cPagi * (pS + 0.0001) + cSore * duS + cHujan * rS + cMalam * nS) / (pS + 0.0001 + duS + rS + nS);

    //AWAN
    vec3 d = normalize(v_worldPos);
    vec2 base = d.xz / max(d.y, 0.02);
#if AWAN_BAYER
    float dh = bayer8(gl_FragCoord.xy);
#else
    float dh = hash(base * 999.0);
#endif

    float shd = 0.0;
    float mm = 1.0;
    float sc = 1.4;
    for (int i = 0; i < 12; i++) {
        sc /= 1.009;
        mm *= 0.96;
        float awan = step(0.88, hash(floor(base * sc + vec2_splat(dh * AWAN_DITHER + waktu * 0.04))));
        shd = mix(shd, mm, awan);
    }
    float awanMask = shd * smoothstep(0.0, 0.12, d.y) * (1.0 - rS) * (1.0 - nS);
    diffuse.rgb = mix(diffuse.rgb, vec3_splat(1.0), awanMask);

    //langit bawah air
    float ungu = smoothstep(0.0, 0.012, min(fclr.r, fclr.b) - fclr.g);
    float gelap = 1.0 - smoothstep(0.05, 0.14, max(fclr.r, max(fclr.g, fclr.b)));
    float isAkhir = step(0.01, ungu * gelap);
    float isNether = step(fclr.b, fclr.r) * step(fclr.r, 0.4999) * step(fclr.b, 0.1999);
    float isUdw = step(FogAndDistanceControl.x, 0.00999) * (1.0 - isAkhir) * (1.0 - isNether);
    diffuse.rgb = mix(diffuse.rgb, UDW_FOG_WARNA, isUdw);
#endif

    gl_FragColor = diffuse;
}
