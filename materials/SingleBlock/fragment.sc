$input v_clipPosition, v_color, v_fog, v_light, v_texCoords, v_worldPos, v_corner

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/FogUtil.dragonh>
#include <MinecraftRenderer.Materials/DynamicUtil.dragonh>
#include <MinecraftRenderer.Materials/UIUtil.dragonh>

uniform vec4 AlphaMaskedTint;
uniform vec4 ChangeColor;
uniform vec4 CurrentColor;
uniform vec4 DiscardValue;
uniform vec4 HudOpacity;
uniform vec4 OverlayColor;

SAMPLER2D_AUTOREG(s_MatTexture);

#define SHEET_HAND_AKTIF 1   // 0 = matikan perbaikan sheet di item tangan/ikon
#define SHEET_PENANDA 249
#define SHEET_PX 32.0


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

vec4 sampelSheet(vec2 uv, vec3 corner) {
    vec4 raw = texture2D(s_MatTexture, uv);
#if SHEET_HAND_AKTIF
    vec2 loc = sheetLocalT(corner, uv);
    if (int(raw.w * 255.0 + 0.5) != SHEET_PENANDA) {
        return raw;
    }
    vec2 at = vec2(textureSize(s_MatTexture, 0));
    vec2 e = vec2(SHEET_PX, SHEET_PX) / at;
    vec2 o = uv - loc * e;
    vec4 a = texture2DLod(s_MatTexture, o + loc * 0.5 * e, 0.0);
    a.a = 1.0;
    return a;
#else
    return raw;
#endif
}

void main() {
    vec4 diffuse = sampelSheet(v_texCoords, v_corner);

#ifdef ALPHA_TEST_DISCARD_VALUE_BASED
    if (diffuse.a < DiscardValue.x) {
        discard;
    }
#elif defined(ALPHA_TEST_VERTEX_TINT_MASK_BASED)
    if (diffuse.a <= 0.0) {
        discard;
    }
#endif

    diffuse = applyOverlayColor(diffuse, OverlayColor);

#if LIT
    diffuse = applyLighting(diffuse, v_light);
#endif

    if (AlphaMaskedTint.x != 0.0) {
        diffuse.rgb = (CurrentColor.rgb * mix(diffuse.rgb, diffuse.rgb * v_color.rgb, diffuse.a)) * v_color.a;
        diffuse.a = 1.0;
    } else {
        diffuse = (CurrentColor * v_color) * diffuse;
    }

    diffuse = applyHudOpacity(diffuse, HudOpacity);
    diffuse = applyFog(diffuse, v_fog);
    gl_FragColor = diffuse;
}
