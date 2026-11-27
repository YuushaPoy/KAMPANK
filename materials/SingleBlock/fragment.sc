$input v_clipPosition, v_color, v_fog, v_light, v_texCoords, v_worldPos

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

vec4 sampelSheet(vec2 uv) {
    vec4 raw = texture2D(s_MatTexture, uv);
#if SHEET_HAND_AKTIF
    if (int(raw.w * 255.0 + 0.5) != SHEET_PENANDA) {
        return raw;
    }
    vec2 at = vec2(textureSize(s_MatTexture, 0));
    vec2 g = uv * at / SHEET_PX;
    vec2 cell = floor(g);
    vec2 loc = g - cell;
    vec4 a = texture2DLod(s_MatTexture, (cell + loc * 0.5) * SHEET_PX / at, 0.0);
    a.a = 1.0;
    return a;
#else
    return raw;
#endif
}

void main() {
    vec4 diffuse = sampelSheet(v_texCoords);

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
