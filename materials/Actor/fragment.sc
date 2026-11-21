$input v_clipPosition, v_color0, v_fog, v_light, v_texcoord0, v_worldPos

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/ActorUtil.dragonh>
#include <MinecraftRenderer.Materials/FogUtil.dragonh>
#include <MinecraftRenderer.Materials/DitherUtil.dragonh>

uniform vec4 ColorBased;
uniform vec4 ChangeColor;
uniform vec4 UseAlphaRewrite;
uniform vec4 TintedAlphaTestEnabled;
uniform vec4 MatColor;
uniform vec4 OverlayColor;
uniform vec4 TileLightColor;
uniform vec4 MultiplicativeTintColor;
uniform vec4 ActorFPEpsilon;
uniform vec4 HudOpacity;
uniform vec4 DitherParams;
uniform vec4 DitherParams2[3];
uniform vec4 DitheringEnabledToggle;
uniform vec4 FogControl;

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_MatTexture1);

//actor: coloring, tonemap, underwater, nether, haze
#define ACTOR_APA 0.6
#define ACTOR_AO 1
#define ACTOR_AO_MIN 0.55
#define ACTOR_AO_MAX 0.8

//end
#define END_BRIGHT   4.0
#define END_SAT      0.7
#define END_LM_TINT  0.35
#define END_SHOULDER 0.7
#define END_DIM      0.55

highp float endFogAmt(highp vec4 f) {
    highp float ungu = smoothstep(0.0, 0.012, min(f.r, f.b) - f.g);
    highp float gelap = 1.0 - smoothstep(0.05, 0.14, max(f.r, max(f.g, f.b)));
    return ungu * gelap;
}

highp vec3 ACES(highp vec3 x) {
    highp float a = 3.0;
    highp float b = 0.001;
    highp float c = 3.1;
    highp float d = 0.48;
    highp float e = 0.1;
    return ((x * (a * x + b)) / (x * (c * x + d) + e));
}

highp vec3 Tm(highp vec3 x) {
    highp float a = 4.1;
    highp float b = 0.01;
    highp float c = 2.6;
    highp float d = 0.56;
    highp float e = 0.4;
    return (x * (a * x + b)) / (x * (c * x + d) + e);
}

highp vec3 actorSaturation(highp vec3 rgb, highp float adjustment) {
    highp vec3 W = vec3(0.2125, 0.7154, 0.0721);
    highp vec3 intensity = vec3_splat(dot(rgb, W));
    return mix(intensity, rgb, adjustment);
}

//grade
highp vec3 actorGrade(highp vec3 c) {
    highp vec4 fctl = FogControl;
    if (fctl.z < 1.0) {
        return c;
    }
    highp vec4 fclr = v_fog;
    highp vec3 ks = v_clipPosition.xyz;
    highp float endF = endFogAmt(fclr);
    bool akhir = endF > 0.01;
    bool udw = (fctl.x < 0.01) && !akhir;
    highp float endK = (fctl.x < 0.01) ? END_DIM : 1.0;
    bool nether = (fclr.r > fclr.b && fclr.r < 0.5 && fclr.b < 0.2);

    //AO
#if ACTOR_AO
    highp float lumAo = dot(v_light.xyz, vec3_splat(0.3333));
    c *= mix(mix(ACTOR_AO_MIN, ACTOR_AO_MAX, smoothstep(0.3, 0.7, lumAo)), ACTOR_AO_MAX, endF);
#endif

    //dirlight
    highp vec3 cr = cross(dFdx(v_worldPos), dFdy(v_worldPos));
    highp vec3 N = cr / max(length(cr), 0.000001);
    highp float fogL = dot(fclr.rgb, vec3(0.2125, 0.7154, 0.0721));
    highp float wk = smoothstep(0.08, 0.4, fogL);
    highp float uvy = smoothstep(0.25, 0.55, dot(v_light.xyz, vec3_splat(0.3333)));
    highp float malamd = 1.0 - wk * uvy;
    highp vec2 rf = vec2(0.5 + 20.0 / fctl.z, 1.0);
    highp vec2 rt = clamp((fctl.xy - rf) / (vec2(0.23, 0.7) - rf), 0.0, 1.0);
    highp float rk = rt.x * rt.y;
    highp float hujand = rk * rk * (3.0 - 2.0 * rk);
    highp float dir = max(N.x, -N.z);
    c *= mix(vec3_splat(0.75), vec3_splat(0.65), dir * uvy * (1.0 - hujand) * (1.0 - malamd));

    //coloring, saturation and tonemapping
    if (!akhir) {
        c *= vec3_splat(1.7);
        c = actorSaturation(c, 1.2);
        if (!udw) {
            c *= ACES(c);
        }
        c = Tm(c);
    }

    //end
    if (akhir) {
        highp vec3 ec = c * END_BRIGHT * endK;
        ec = actorSaturation(ec, END_SAT);
        highp vec3 eo = max(ec - vec3_splat(END_SHOULDER), vec3_splat(0.0));
        highp float ek = 1.0 - END_SHOULDER;
        ec = min(ec, vec3_splat(END_SHOULDER)) + ek * (vec3_splat(1.0) - exp(-eo / ek));
        highp float drl = max(-N.x, -N.z);
        ec *= mix(vec3_splat(0.85), vec3_splat(0.65), drl);
        c = mix(c, ec, endF);
    }

    //underwater
    if (udw) {
        c *= vec3(0.3, 1.1, 0.85);
        c = mix(c, fclr.xyz * sqrt(vec3(0.8, 1.5, 1.4)), ACTOR_APA * clamp(length(-ks) / 50.0, 0.0, 1.0));
        c *= ACES(c);
    }

    //nether
    if (nether) {
        c *= vec3(1.5, 0.9, 0.0);
        c += vec3_splat(0.2);
        c = Tm(c) * 0.9;
        c = mix(c, fclr.rgb * vec3(1.5, 1.0, 0.0), clamp(length(-ks) / fctl.z * 1.5, 0.1, 0.2));
    }
    return c;
}

//haze
highp vec3 actorHaze(highp vec3 c) {
    highp vec4 fctl = FogControl;
    if (fctl.z < 1.0) {
        return c;
    }
    highp vec3 ks = v_clipPosition.xyz;
    highp vec2 rf = vec2(0.5 + 20.0 / fctl.z, 1.0);
    highp vec2 rt = clamp((fctl.xy - rf) / (vec2(0.23, 0.7) - rf), 0.0, 1.0);
    highp float rk = rt.x * rt.y;
    highp float hujand = rk * rk * (3.0 - 2.0 * rk);
    highp vec3 h = mix(c, v_fog.xyz * sqrt(vec3_splat(1.1)), ACTOR_APA * clamp(length(-ks) / 100.0, 0.0, 1.0));
    return mix(mix(c, h, 1.0 - hujand), c, endFogAmt(v_fog));
}

//light
highp vec4 actorLight() {
    highp vec4 l = v_light;
    if (FogControl.z >= 1.0) {
        highp float ll = dot(l.xyz, vec3(0.2125, 0.7154, 0.0721));
        l.xyz = mix(l.xyz, mix(vec3_splat(ll), l.xyz, END_LM_TINT), endFogAmt(v_fog));
    }
    return l;
}

void main() {
#if defined(DEPTH_ONLY) || defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY_ALPHA_TEST)
    gl_FragColor = vec4_splat(0.0);
    return;
#else

    vec4 albedo = getActorAlbedoNoColorChange(v_texcoord0, s_MatTexture, s_MatTexture1, MatColor);

    bool ditherDiscard = false;
    if (DitheringEnabledToggle.x != 0.0) {
        vec3 camPos = mul(u_invView, vec4(0.0, 0.0, 0.0, 1.0)).xyz;
        ditherDiscard = evaluateDitherDiscard(v_clipPosition, v_worldPos, camPos, u_view, DitherParams, DitherParams2[0]);
    }

#if defined(ALPHA_TEST)
    float alpha = mix(albedo.a, albedo.a * OverlayColor.a, TintedAlphaTestEnabled.x);
    if (ditherDiscard || shouldDiscard(albedo.rgb, alpha, ActorFPEpsilon.x)) {
        discard;
    }
#endif

#if defined(CHANGE_COLOR_MULTI)
    albedo = applyMultiColorChange(albedo, ChangeColor.rgb, MultiplicativeTintColor.rgb);
#elif defined(CHANGE_COLOR)
    albedo = applyColorChange(albedo, ChangeColor, albedo.a);
    albedo.a *= ChangeColor.a;
#endif

#if defined(ALPHA_TEST)
    albedo.a = max(UseAlphaRewrite.x, albedo.a);
#endif

    albedo = applyActorDiffuse(albedo, v_color0.rgb, actorLight(), ColorBased.x, OverlayColor);

#if defined(TRANSPARENT)
    albedo = applyHudOpacity(albedo, HudOpacity.x);
    if (ditherDiscard) {
        albedo.a = 0.0;
    }
#endif

    albedo.rgb = actorGrade(albedo.rgb);
    albedo.rgb = applyFog(albedo.rgb, v_fog.rgb, v_fog.a);
    albedo.rgb = actorHaze(albedo.rgb);
    gl_FragColor = albedo;
#endif
}
