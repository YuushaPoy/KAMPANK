$input v_clipPosition, v_color0, v_fog, v_light, v_texcoord0, v_worldPos, v_corner

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/DynamicUtil.dragonh>
#include <MinecraftRenderer.Materials/FogUtil.dragonh>
#include <MinecraftRenderer.Materials/DitherUtil.dragonh>

uniform vec4 AlphaMaskedTint;
uniform vec4 ChangeColor;
uniform vec4 OverlayColor;
uniform vec4 ColorBased;
uniform vec4 MultiplicativeTintColor;
uniform vec4 MatColor;
uniform vec4 DitherParams;
uniform vec4 DitherParams2[3];
uniform vec4 DitheringEnabledToggle;

SAMPLER2D_AUTOREG(s_MatTexture);

#define SHEET_HAND_AKTIF 1   // 0 = matikan perbaikan sheet di item tangan
#define SHEET_PENANDA 249
#define SHEET_PX 32.0

//efek metalic & glow di tangan (disamakan dengan RenderChunk)
#define HAND_METAL_AKTIF 1
#define HAND_GLOW_AKTIF 1
#define HAND_METAL_GAIN 2.5          // sama dengan METAL_GAIN di RenderChunk
#define HAND_METAL_MIN 0.02          // sama dengan METAL_MIN di RenderChunk
#define HAND_SISI_HALUS 0.15
#define HAND_TERANG_MIN 0.45         // di bawah ini dianggap gelap (metal mati, glow penuh)
#define HAND_TERANG_MAX 0.95         // di atas ini dianggap terang (metal penuh, glow lemah)
#define HAND_GLOW_SIANG 0.35
#define GLOW_INTENSITY 0.85
#define GLOW_DASAR 0.25
#define GLOW_TETAP 1.0
#define GLOW_BLOOM_KUAT 1.6
#define GLOW_BLOOM_MULAI 0.4
#define GLOW_BLOOM_PUTIH 0.6

float oreGlowCode(float a) {
    int c = int(a * 255.0 + 0.5);
    return (c == 252) ? 1.0 : ((c == 253) ? 0.4 : 0.0);
}

vec3 glowBloom(vec3 g) {
    g *= GLOW_BLOOM_KUAT;
    float l = dot(g, vec3(0.299, 0.587, 0.114));
    float w = smoothstep(GLOW_BLOOM_MULAI, 1.0, l) * GLOW_BLOOM_PUTIH;
    return mix(g, vec3_splat(l * 1.15), w);
}


//koordinat lokal sisi blok (0..1) dari t (interpolasi sudut kubus), dipetakan ke u/v lewat turunan uv
vec2 sheetLocalT(vec3 t, vec2 uv) {
    vec3 dt = abs(dFdx(t)) + abs(dFdy(t));
    float mn = min(dt.x, min(dt.y, dt.z));
    vec3 an = vec3(step(dt.x, mn + 0.0001), step(dt.y, mn + 0.0001), step(dt.z, mn + 0.0001));
    vec2 w = (an.y > 0.5) ? t.xz : ((an.x > 0.5) ? vec2(t.z, t.y) : vec2(t.x, t.y));
    vec2 dwx = dFdx(w);
    vec2 dwy = dFdy(w);
    float cuA = dFdx(uv.x) * dwx.x + dFdy(uv.x) * dwy.x;
    float cuB = dFdx(uv.x) * dwx.y + dFdy(uv.x) * dwy.y;
    float cvA = dFdx(uv.y) * dwx.x + dFdy(uv.y) * dwy.x;
    float cvB = dFdx(uv.y) * dwx.y + dFdy(uv.y) * dwy.y;
    bool lurus = (abs(cuA) + abs(cvB)) >= (abs(cuB) + abs(cvA));
    float su = lurus ? cuA : cuB;
    float sv = lurus ? cvB : cvA;
    float wu = lurus ? w.x : w.y;
    float wv = lurus ? w.y : w.x;
    return clamp(vec2(su >= 0.0 ? wu : 1.0 - wu, sv >= 0.0 ? wv : 1.0 - wv), 0.002, 0.998);
}

vec4 sampelSheet(vec2 uv, vec3 corner, out float metalAmt, out vec4 glowTex) {
    metalAmt = 0.0;
    vec4 raw = texture2D(s_MatTexture, uv);
    glowTex = raw;
    int ca = int(raw.w * 255.0 + 0.5);
#if SHEET_HAND_AKTIF
    vec2 loc = sheetLocalT(corner, uv);
    if (ca == SHEET_PENANDA) {
        vec2 at = vec2(textureSize(s_MatTexture, 0));
        vec2 e = vec2(SHEET_PX, SHEET_PX) / at;
        vec2 o = uv - loc * e;
        vec4 a = texture2DLod(s_MatTexture, o + loc * 0.5 * e, 0.0);
        vec4 d = texture2DLod(s_MatTexture, o + (vec2(0.0, 0.5) + loc * 0.5) * e, 0.0);
        vec3 em = texture2DLod(s_MatTexture, o + (vec2(0.5, 0.5) + loc * 0.5) * e, 0.0).rgb;
        metalAmt = (d.g > HAND_METAL_MIN) ? saturate(d.g * HAND_METAL_GAIN) : 0.0;
        float sg = step(0.004, max(em.r, max(em.g, em.b)));
        glowTex = vec4(em, mix(249.0, 252.0, sg) / 255.0);
        a.a = 1.0;
        return a;
    }
#endif
    if (ca == 250) {
        metalAmt = 1.0;
    }
    return raw;
}

void main() {
#if defined(DEPTH_ONLY)
    gl_FragColor = vec4_splat(0.0);
    return;
#else
    float metalAmt;
    vec4 glowTex;
    vec4 albedo = sampelSheet(v_texcoord0, v_corner, metalAmt, glowTex);

    if (AlphaMaskedTint.x != 0.0) {
        albedo.rgb = mix(albedo.rgb, albedo.rgb * v_color0.rgb, albedo.a) * v_color0.a;
        albedo.a = 1.0;
    } else {
        albedo.rgb *= v_color0.rgb;
    }

    albedo *= MatColor;

    vec3 tinted = albedo.rgb * mix(vec3_splat(1.0), v_color0.rgb, ColorBased.x);

#if defined(MULTI_COLOR_TINT)
    vec2 colorMask = tinted.rg;
    vec3 multiTint = mix(tinted.r * ChangeColor.rgb, tinted.g * MultiplicativeTintColor.rgb, ceil(colorMask.g));
    albedo.rgb = mix(multiTint, OverlayColor.rgb, OverlayColor.a) * v_light.rgb;
#else
    vec4 baseTint = vec4(tinted, albedo.a);
    vec3 singleTint = mix(baseTint, baseTint * ChangeColor, v_color0.a).rgb;
    albedo.rgb = mix(singleTint, OverlayColor.rgb, OverlayColor.a) * v_light.rgb;
#endif

    float handTerang = max(v_light.r, max(v_light.g, v_light.b));
    float handSiang = smoothstep(HAND_TERANG_MIN, HAND_TERANG_MAX, handTerang);

#if HAND_METAL_AKTIF
    if (metalAmt > 0.0) {
        vec3 hCam = mul(u_invView, vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        vec3 hV = normalize(hCam - v_worldPos);
        vec3 hN = normalize(cross(dFdx(v_worldPos), dFdy(v_worldPos)));
        if (dot(hN, hV) < 0.0) {
            hN = -hN;
        }
        float hNV = clamp(dot(hN, hV), 0.001, 1.0);
        float hFr = (0.3 + 0.7 * pow(saturate(1.0 - hNV), 5.0)) * metalAmt;
        vec3 hR = reflect(-hV, hN);
        float hAtas = smoothstep(-HAND_SISI_HALUS, HAND_SISI_HALUS, hR.y);
        vec3 hSisiAtas = mix(vec3(0.1, 0.5, 1.0) * 1.2, vec3_splat(1.1), 0.35);
        vec3 hSisiBawah = vec3(212.0, 232.0, 255.0) / 255.0;
        albedo.rgb = mix(albedo.rgb, mix(hSisiBawah, hSisiAtas, hAtas), saturate(hFr) * handSiang);
    }
#endif

#if HAND_GLOW_AKTIF
    {
        float hGlow = oreGlowCode(glowTex.w);
        if (hGlow > 0.0) {
            float hGf = mix(1.0, HAND_GLOW_SIANG, handSiang);
            albedo.rgb = mix(albedo.rgb, glowTex.rgb * GLOW_DASAR, saturate(hGlow * hGf * GLOW_TETAP));
            albedo.rgb += glowBloom(glowTex.rgb * hGlow * GLOW_INTENSITY * hGf);
        }
    }
#endif

    bool ditherDiscard = false;
    if (DitheringEnabledToggle.x != 0.0) {
        vec3 camPos = mul(u_invView, vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        ditherDiscard = evaluateDitherDiscard(v_clipPosition, v_worldPos, camPos, u_view, DitherParams, DitherParams2[0]);
    }

#if defined(ALPHA_TEST)
    if (ditherDiscard || albedo.a < 0.5) {
        discard;
    }
#elif defined(TRANSPARENT)
    if (ditherDiscard) {
        albedo.a = 0.0;
    }
#endif

    albedo.rgb = applyFog(albedo.rgb, v_fog.rgb, v_fog.a);
    gl_FragColor = albedo;
#endif
}
